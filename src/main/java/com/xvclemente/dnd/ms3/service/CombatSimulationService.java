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
        LOGGER.info("=====================================================================");
        LOGGER.info("MS3: Iniciando simulación de AVENTURA GRUPAL para ID: {}", aventuraEvent.getAdventureId());
        LOGGER.info("=====================================================================");

        List<Combatant> pjsVivos = convertMapToList(participantesEvent.getCharacters(), "PJ");
        List<Combatant> enemigosVivos = convertMapToList(participantesEvent.getEnemies(), "EN");

        List<String> pjsGanadoresAventura = new ArrayList<>();
        List<String> ensDerrotadosAventura = new ArrayList<>();

        if (pjsVivos.isEmpty() || enemigosVivos.isEmpty()) {
            LOGGER.warn("MS3: No hay suficientes PJs o ENs para iniciar el combate en aventura {}", aventuraEvent.getAdventureId());
            // Aún así, finalizamos la aventura para que el sistema no se quede esperando
            AventuraFinalizadaEvent eventoFinal = new AventuraFinalizadaEvent(aventuraEvent.getAdventureId(), new ArrayList<>(), new ArrayList<>(), 0, "AVENTURA CANCELADA (Sin participantes)");
            combatEventProducer.sendAventuraFinalizadaEvent(eventoFinal);
            return;
        }
        
        // --- BUCLE DE COMBATE GRUPAL ---
        // La batalla continúa mientras haya combatientes en ambos bandos.
        int round = 1;
        while (!pjsVivos.isEmpty() && !enemigosVivos.isEmpty()) {
            LOGGER.info("--- RONDA DE COMBATE {} ---", round++);
            
            // Turno de los PJs
            // Hacemos una copia de la lista de enemigos para evitar problemas si un enemigo muere en medio del turno de los PJs
            List<Combatant> enemigosParaAtacar = new ArrayList<>(enemigosVivos); 
            for (Combatant pj : pjsVivos) {
                if (enemigosParaAtacar.isEmpty()) break; // Si todos los enemigos mueren, termina el turno de los PJs
                
                Combatant enemigoObjetivo = enemigosParaAtacar.get(random.nextInt(enemigosParaAtacar.size()));
                int dano = Math.max(1, pj.getStats().getAtaqueActual() - enemigoObjetivo.getStats().getDefensaActual());
                enemigoObjetivo.getStats().setHpActual(enemigoObjetivo.getStats().getHpActual() - dano);
                LOGGER.info(" > {} ataca a {}: {} daño. HP restante de {}: {}", pj.getStats().getNombre(), enemigoObjetivo.getStats().getNombre(), dano, enemigoObjetivo.getStats().getNombre(), Math.max(0, enemigoObjetivo.getStats().getHpActual()));

                if (enemigoObjetivo.getStats().getHpActual() <= 0) {
                    LOGGER.info("   !!! {} ha derrotado a {} !!!", pj.getStats().getNombre(), enemigoObjetivo.getStats().getNombre());
                    enemigosVivos.remove(enemigoObjetivo);
                    enemigosParaAtacar.remove(enemigoObjetivo); // También de la lista de objetivos para esta ronda
                    ensDerrotadosAventura.add(enemigoObjetivo.getId());
                    
                    // Publicamos el resultado individual inmediatamente
                    ResultadoCombateIndividualEvent resultado = new ResultadoCombateIndividualEvent(
                        aventuraEvent.getAdventureId(), round, pj.getId(), enemigoObjetivo.getId(), "PJ", "EN");
                    combatEventProducer.sendResultadoCombateIndividualEvent(resultado);
                }
            }

            if (enemigosVivos.isEmpty()) break; // Si todos los enemigos fueron derrotados, la batalla termina

            // Turno de los Enemigos
            List<Combatant> pjsParaAtacar = new ArrayList<>(pjsVivos);
            for (Combatant enemigo : enemigosVivos) {
                if (pjsParaAtacar.isEmpty()) break;

                Combatant pjObjetivo = pjsParaAtacar.get(random.nextInt(pjsParaAtacar.size()));
                int dano = Math.max(1, enemigo.getStats().getAtaqueActual() - pjObjetivo.getStats().getDefensaActual());
                pjObjetivo.getStats().setHpActual(pjObjetivo.getStats().getHpActual() - dano);
                LOGGER.info(" > {} ataca a {}: {} daño. HP restante de {}: {}", enemigo.getStats().getNombre(), pjObjetivo.getStats().getNombre(), dano, pjObjetivo.getStats().getNombre(), Math.max(0, pjObjetivo.getStats().getHpActual()));

                if (pjObjetivo.getStats().getHpActual() <= 0) {
                    LOGGER.info("   !!! {} ha derrotado a {} !!!", enemigo.getStats().getNombre(), pjObjetivo.getStats().getNombre());
                    pjsVivos.remove(pjObjetivo);
                    pjsParaAtacar.remove(pjObjetivo);

                    ResultadoCombateIndividualEvent resultado = new ResultadoCombateIndividualEvent(
                        aventuraEvent.getAdventureId(), round, enemigo.getId(), pjObjetivo.getId(), "EN", "PJ");
                    combatEventProducer.sendResultadoCombateIndividualEvent(resultado);
                }
            }
        }

        // --- FINAL DE LA AVENTURA ---
        String resultadoAventuraStr;
        int oroGanado = 0;

        if (!pjsVivos.isEmpty()) {
            resultadoAventuraStr = "PJs VICTORIOSOS";
            pjsGanadoresAventura = pjsVivos.stream().map(Combatant::getId).collect(Collectors.toList());
            //... (lógica del oro)
            LOGGER.info("MS3: Aventura {} finalizada con victoria de PJs. {} supervivientes.", aventuraEvent.getAdventureId(), pjsVivos.size());
        } else {
            resultadoAventuraStr = "PJs DERROTADOS";
            LOGGER.info("MS3: Aventura {} finalizada con derrota de PJs.", aventuraEvent.getAdventureId());
        }

        AventuraFinalizadaEvent aventuraFinalizada = new AventuraFinalizadaEvent(
                aventuraEvent.getAdventureId(), pjsGanadoresAventura, ensDerrotadosAventura, oroGanado, resultadoAventuraStr
        );
        combatEventProducer.sendAventuraFinalizadaEvent(aventuraFinalizada);
    }

    private List<Combatant> convertMapToList(Map<String, CombatantStatsDto> combatantMap, String type) {
        if (combatantMap == null) return new ArrayList<>();
        return combatantMap.entrySet().stream()
                .map(entry -> {
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