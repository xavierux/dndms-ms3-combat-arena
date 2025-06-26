package com.xvclemente.dnd.ms3.service;

import com.xvclemente.dnd.dtos.events.*;
import com.xvclemente.dnd.ms3.kafka.producer.CombatEventProducer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class CombatSimulationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatSimulationService.class);
    private final CombatEventProducer combatEventProducer;
    private final Random random = new Random();

    // Clase interna para manejar los combatientes y sus stats durante la simulación
    // Esto hace el código más limpio que manejar mapas y listas por separado.
    @Data
    @AllArgsConstructor
    private static class Combatant {
        private String id;
        private String type; // "PJ" o "EN"
        private CombatantStatsDto stats;
    }

    @Autowired
    public CombatSimulationService(CombatEventProducer combatEventProducer) {
        this.combatEventProducer = combatEventProducer;
    }

    public void simulateAdventure(AventuraCreadaEvent aventuraEvent, ParticipantesListosParaAventuraEvent participantesEvent) {
        LOGGER.info("MS3: Iniciando simulación para aventura: {}", aventuraEvent.getAdventureId());

        // MEJORA 1: Usar las stats del evento enriquecido
        List<Combatant> pjsVivos = convertMapToList(participantesEvent.getCharacters(), "PJ");
        List<Combatant> enemigosVivos = convertMapToList(participantesEvent.getEnemies(), "EN");

        List<String> pjsGanadoresAventura = new ArrayList<>();
        List<String> ensDerrotadosAventura = new ArrayList<>();

        // MEJORA 2: Bucle para simular MÚLTIPLES encuentros
        for (int i = 1; i <= aventuraEvent.getNumEncounters(); i++) {
            if (pjsVivos.isEmpty() || enemigosVivos.isEmpty()) {
                LOGGER.info("MS3: No hay más combatientes de un bando. La aventura termina antes del encuentro {}/{}.", i, aventuraEvent.getNumEncounters());
                break; // Termina la aventura si un bando es aniquilado
            }
            LOGGER.info("--- Iniciando Encuentro {}/{} ---", i, aventuraEvent.getNumEncounters());

            // Lógica para seleccionar participantes para este encuentro
            Combatant pj = pjsVivos.get(random.nextInt(pjsVivos.size()));
            Combatant enemigo = enemigosVivos.get(random.nextInt(enemigosVivos.size()));

            LOGGER.info("MS3: Encuentro {} -> {} ({}) vs {} ({})", i, pj.getStats().getNombre(), pj.getType(), enemigo.getStats().getNombre(), enemigo.getType());

            // MEJORA 3: Simulación de combate por turnos basada en stats
            Combatant ganadorEncuentro = simulateSingleCombat(pj, enemigo, aventuraEvent.getAdventureId(), i);
            Combatant perdedorEncuentro = ganadorEncuentro.getId().equals(pj.getId()) ? enemigo : pj;

            // Eliminar al perdedor de la lista de combatientes vivos para los siguientes encuentros
            if ("PJ".equals(perdedorEncuentro.getType())) {
                pjsVivos.remove(perdedorEncuentro);
            } else {
                enemigosVivos.remove(perdedorEncuentro);
                ensDerrotadosAventura.add(perdedorEncuentro.getId());
            }
        }

        // Lógica de fin de aventura mejorada: ganan si quedan PJs vivos
        String resultadoAventuraStr;
        int oroGanado = 0;

        if (!pjsVivos.isEmpty()) {
            resultadoAventuraStr = "PJs VICTORIOSOS";
            pjsGanadoresAventura = pjsVivos.stream().map(Combatant::getId).collect(Collectors.toList());
            switch (aventuraEvent.getGoldRewardTier().toLowerCase()) {
                case "poor": oroGanado = 100; break;
                case "generous": oroGanado = 150; break;
                case "treasure": oroGanado = 200; break;
                default: oroGanado = 50;
            }
            LOGGER.info("MS3: Aventura {} finalizada con victoria de PJs. Oro por PJ: {}", aventuraEvent.getAdventureId(), oroGanado);
        } else {
            resultadoAventuraStr = "PJs DERROTADOS";
            LOGGER.info("MS3: Aventura {} finalizada con derrota de PJs.", aventuraEvent.getAdventureId());
        }

        AventuraFinalizadaEvent aventuraFinalizada = new AventuraFinalizadaEvent(
                aventuraEvent.getAdventureId(), pjsGanadoresAventura, ensDerrotadosAventura, oroGanado, resultadoAventuraStr
        );
        combatEventProducer.sendAventuraFinalizadaEvent(aventuraFinalizada);
    }

    /**
     * Simula un combate 1 vs 1 por turnos hasta que el HP de uno llegue a 0.
     * Publica el evento del resultado y devuelve al ganador.
     */
    private Combatant simulateSingleCombat(Combatant pj, Combatant enemigo, String adventureId, int encounterNum) {
        // Hacemos una copia de los HP para no modificar el objeto original directamente en este bucle
        int hpPj = pj.getStats().getHpActual();
        int hpEnemigo = enemigo.getStats().getHpActual();

        while (hpPj > 0 && hpEnemigo > 0) {
            // Turno del PJ
            int danoAEnemigo = Math.max(1, pj.getStats().getAtaqueActual() - enemigo.getStats().getDefensaActual());
            hpEnemigo -= danoAEnemigo;
            LOGGER.info(" > {} ataca a {}: {} daño. HP restante de {}: {}", pj.getStats().getNombre(), enemigo.getStats().getNombre(), danoAEnemigo, enemigo.getStats().getNombre(), Math.max(0, hpEnemigo));
            if (hpEnemigo <= 0) break;

            // Turno del Enemigo
            int danoAPJ = Math.max(1, enemigo.getStats().getAtaqueActual() - pj.getStats().getDefensaActual());
            hpPj -= danoAPJ;
            LOGGER.info(" > {} ataca a {}: {} daño. HP restante de {}: {}", enemigo.getStats().getNombre(), pj.getStats().getNombre(), danoAPJ, pj.getStats().getNombre(), Math.max(0, hpPj));
        }

        Combatant ganador = hpPj > 0 ? pj : enemigo;
        Combatant perdedor = hpPj > 0 ? enemigo : pj;

        LOGGER.info("MS3: {} gana el encuentro contra {}", ganador.getStats().getNombre(), perdedor.getStats().getNombre());
        
        ResultadoCombateIndividualEvent resultadoIndividual = new ResultadoCombateIndividualEvent(
                adventureId, encounterNum, ganador.getId(), perdedor.getId(), ganador.getType(), perdedor.getType());
        combatEventProducer.sendResultadoCombateIndividualEvent(resultadoIndividual);

        return ganador;
    }

    /**
     * Helper para convertir el mapa del DTO a una lista de objetos Combatant.
     */
    private List<Combatant> convertMapToList(Map<String, CombatantStatsDto> combatantMap, String type) {
        if (combatantMap == null) return new ArrayList<>();
        return combatantMap.entrySet().stream()
                .map(entry -> {
                    // Creamos una nueva instancia de CombatantStatsDto para evitar modificar el original
                    CombatantStatsDto statsCopy = new CombatantStatsDto(
                        entry.getValue().getNombre(),
                        entry.getValue().getHpActual(),
                        entry.getValue().getAtaqueActual(),
                        entry.getValue().getDefensaActual()
                    );
                    return new Combatant(entry.getKey(), type, statsCopy);
                })
                .collect(Collectors.toList());
    }
}