package com.xvclemente.dnd.ms3.service;

import com.xvclemente.dnd.dtos.events.AventuraCreadaEvent;
import com.xvclemente.dnd.dtos.events.AventuraFinalizadaEvent;
import com.xvclemente.dnd.dtos.events.ParticipantesListosParaAventuraEvent;
import com.xvclemente.dnd.dtos.events.ResultadoCombateIndividualEvent;
import com.xvclemente.dnd.ms3.kafka.producer.CombatEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class CombatSimulationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatSimulationService.class);
    private final CombatEventProducer combatEventProducer;
    private final Random random = new Random();

    @Autowired
    public CombatSimulationService(CombatEventProducer combatEventProducer) {
        this.combatEventProducer = combatEventProducer;
    }

    public void simulateAdventure(AventuraCreadaEvent aventuraEvent, ParticipantesListosParaAventuraEvent participantesEvent) {
        LOGGER.info("MS3: Iniciando simulación para aventura: {}", aventuraEvent.getAdventureId());
        List<String> characterIds = new ArrayList<>(participantesEvent.getCharacterIds());
        List<String> enemyIds = new ArrayList<>(participantesEvent.getEnemyIds());
        List<String> pjsGanadoresAventura = new ArrayList<>();
        List<String> ensDerrotadosAventura = new ArrayList<>();
        int oroGanado = 0;

        // MVP: Simular solo un encuentro, incluso si numEncounters es > 1
        // Lógica de combate súper simple para el primer encuentro
        if (!characterIds.isEmpty() && !enemyIds.isEmpty()) {
            String pjId = characterIds.get(0); // Tomar el primer PJ
            String enId = enemyIds.get(0);   // Tomar el primer EN

            LOGGER.info("MS3: Encuentro 1 para aventura {} -> PJ {} vs EN {}", aventuraEvent.getAdventureId(), pjId, enId);

            boolean pjGanaEncuentro = random.nextBoolean(); // 50/50 chance
            ResultadoCombateIndividualEvent resultadoIndividual;

            if (pjGanaEncuentro) {
                LOGGER.info("MS3: PJ {} gana el encuentro contra EN {}", pjId, enId);
                resultadoIndividual = new ResultadoCombateIndividualEvent(
                        aventuraEvent.getAdventureId(), 1, pjId, enId, "PJ", "EN");
                ensDerrotadosAventura.add(enId);
                // Para MVP, si el PJ gana el (único) encuentro, gana la aventura
                pjsGanadoresAventura.add(pjId); 
            } else {
                LOGGER.info("MS3: EN {} gana el encuentro contra PJ {}", enId, pjId);
                resultadoIndividual = new ResultadoCombateIndividualEvent(
                        aventuraEvent.getAdventureId(), 1, enId, pjId, "EN", "PJ");
                // Para MVP, si el PJ pierde el (único) encuentro, no hay ganadores PJs
            }
            combatEventProducer.sendResultadoCombateIndividualEvent(resultadoIndividual);
        } else {
            LOGGER.warn("MS3: No hay suficientes PJs o ENs para el encuentro en aventura {}", aventuraEvent.getAdventureId());
        }

        // Determinar resultado final de la aventura (para MVP, basado en el único encuentro)
        String resultadoAventuraStr;
        if (!pjsGanadoresAventura.isEmpty()) {
            resultadoAventuraStr = "PJs VICTORIOSOS";
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
                aventuraEvent.getAdventureId(),
                pjsGanadoresAventura,
                ensDerrotadosAventura, // En MVP, solo el primer enemigo si fue derrotado
                oroGanado,
                resultadoAventuraStr
        );
        combatEventProducer.sendAventuraFinalizadaEvent(aventuraFinalizada);
    }
}