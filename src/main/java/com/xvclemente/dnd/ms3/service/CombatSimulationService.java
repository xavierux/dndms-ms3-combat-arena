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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CombatSimulationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatSimulationService.class);
    private final CombatEventProducer combatEventProducer;
    private final Random random = new Random();

    // --- Mapa para guardar los logs por ID de aventura ---
    private final Map<String, List<String>> combatLogs = new ConcurrentHashMap<>();

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

    // --- Método para que el Controller obtenga el log ---
    public List<String> getCombatLogForAdventure(String adventureId) {
        return combatLogs.getOrDefault(adventureId, Collections.singletonList("No se encontró log para esta aventura."));
    }

    public void simulateAdventure(AventuraCreadaEvent aventuraEvent, ParticipantesListosParaAventuraEvent participantesEvent) {
        // --- Preparar la lista para el log de esta aventura ---
        List<String> currentLog = new ArrayList<>();
        combatLogs.put(aventuraEvent.getAdventureId(), currentLog);
        
        String header = "=====================================================================";
        String startMsg = String.format("MS3: Iniciando simulación de AVENTURA GRUPAL para ID: %s", aventuraEvent.getAdventureId());
        LOGGER.info(header);
        LOGGER.info(startMsg);
        LOGGER.info(header);
        currentLog.add(header);
        currentLog.add(startMsg);
        currentLog.add(header);

        List<Combatant> pjsVivos = convertMapToList(participantesEvent.getCharacters(), "PJ");
        List<Combatant> enemigosVivos = convertMapToList(participantesEvent.getEnemies(), "EN");

        List<String> pjsGanadoresAventura = new ArrayList<>();
        List<String> ensDerrotadosAventura = new ArrayList<>();

        if (pjsVivos.isEmpty() || enemigosVivos.isEmpty()) {
            String warnMsg = String.format("MS3: No hay suficientes PJs o ENs para iniciar el combate en aventura %s", aventuraEvent.getAdventureId());
            LOGGER.warn(warnMsg);
            currentLog.add(warnMsg);
            // Aún así, finalizamos la aventura para que el sistema no se quede esperando
            AventuraFinalizadaEvent eventoFinal = new AventuraFinalizadaEvent(aventuraEvent.getAdventureId(), new ArrayList<>(), new ArrayList<>(), 0, "AVENTURA CANCELADA (Sin participantes)");
            combatEventProducer.sendAventuraFinalizadaEvent(eventoFinal);
            return;
        }
        
        int round = 1;
        while (!pjsVivos.isEmpty() && !enemigosVivos.isEmpty()) {
            String roundLog = String.format("--- RONDA DE COMBATE %d ---", round++);
            LOGGER.info(roundLog);
            currentLog.add(roundLog);
            
            List<Combatant> enemigosParaAtacar = new ArrayList<>(enemigosVivos); 
            for (Combatant pj : pjsVivos) {
                if (enemigosParaAtacar.isEmpty()) break; // Si todos los enemigos mueren, termina el turno de los PJs
                
                Combatant enemigoObjetivo = enemigosParaAtacar.get(random.nextInt(enemigosParaAtacar.size()));
                int dano = Math.max(1, pj.getStats().getAtaqueActual() - enemigoObjetivo.getStats().getDefensaActual());
                enemigoObjetivo.getStats().setHpActual(enemigoObjetivo.getStats().getHpActual() - dano);
                
                String pjAttackLog = String.format(" > %s ataca a %s: %d daño. HP restante de %s: %d", pj.getStats().getNombre(), enemigoObjetivo.getStats().getNombre(), dano, enemigoObjetivo.getStats().getNombre(), Math.max(0, enemigoObjetivo.getStats().getHpActual()));
                LOGGER.info(pjAttackLog);
                currentLog.add(pjAttackLog);

                if (enemigoObjetivo.getStats().getHpActual() <= 0) {
                    String defeatLog = String.format("   !!! %s ha derrotado a %s !!!", pj.getStats().getNombre(), enemigoObjetivo.getStats().getNombre());
                    LOGGER.info(defeatLog);
                    currentLog.add(defeatLog);
                    
                    enemigosVivos.remove(enemigoObjetivo);
                    enemigosParaAtacar.remove(enemigoObjetivo); // También de la lista de objetivos para esta ronda
                    ensDerrotadosAventura.add(enemigoObjetivo.getId());
                    
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

                String enAttackLog = String.format(" > %s ataca a %s: %d daño. HP restante de %s: %d", enemigo.getStats().getNombre(), pjObjetivo.getStats().getNombre(), dano, pjObjetivo.getStats().getNombre(), Math.max(0, pjObjetivo.getStats().getHpActual()));
                LOGGER.info(enAttackLog);
                currentLog.add(enAttackLog);

                if (pjObjetivo.getStats().getHpActual() <= 0) {
                    String defeatLog = String.format("   !!! %s ha derrotado a %s !!!", enemigo.getStats().getNombre(), pjObjetivo.getStats().getNombre());
                    LOGGER.info(defeatLog);
                    currentLog.add(defeatLog);

                    pjsVivos.remove(pjObjetivo);
                    pjsParaAtacar.remove(pjObjetivo);

                    ResultadoCombateIndividualEvent resultado = new ResultadoCombateIndividualEvent(
                        aventuraEvent.getAdventureId(), round, enemigo.getId(), pjObjetivo.getId(), "EN", "PJ");
                    combatEventProducer.sendResultadoCombateIndividualEvent(resultado);
                }
            }
        }

        String resultadoAventuraStr;
        int oroGanado = 0;

        if (!pjsVivos.isEmpty()) {
            resultadoAventuraStr = "PJs VICTORIOSOS";
            pjsGanadoresAventura = pjsVivos.stream().map(Combatant::getId).collect(Collectors.toList());
            // Lógica del oro...
            String finalMsg = String.format("MS3: Aventura %s finalizada con victoria de PJs. %d supervivientes.", aventuraEvent.getAdventureId(), pjsVivos.size());
            LOGGER.info(finalMsg);
            currentLog.add(finalMsg);
        } else {
            resultadoAventuraStr = "PJs DERROTADOS";
            String finalMsg = String.format("MS3: Aventura %s finalizada con derrota de PJs.", aventuraEvent.getAdventureId());
            LOGGER.info(finalMsg);
            currentLog.add(finalMsg);
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