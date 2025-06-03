package com.xvclemente.dnd.ms3.kafka.consumer;

import com.xvclemente.dnd.dtos.events.AventuraCreadaEvent;
import com.xvclemente.dnd.dtos.events.ParticipantesListosParaAventuraEvent;
import com.xvclemente.dnd.ms3.service.CombatSimulationService;
import com.xvclemente.dnd.ms3.service.PendingAdventureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class AdventureDataConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdventureDataConsumer.class);

    private final PendingAdventureService pendingAdventureService;
    private final CombatSimulationService combatSimulationService;

    @Autowired
    public AdventureDataConsumer(PendingAdventureService pendingAdventureService, CombatSimulationService combatSimulationService) {
        this.pendingAdventureService = pendingAdventureService;
        this.combatSimulationService = combatSimulationService;
    }

    @KafkaListener(topics = "${app.kafka.topic.aventuras-creadas}",
                   groupId = "${spring.kafka.consumer.group-id}",
                   containerFactory = "aventuraCreadaEventKafkaListenerContainerFactory") // Necesitaremos un bean para esto
    public void handleAventuraCreada(@Payload AventuraCreadaEvent event) {
        LOGGER.info("MS3: AventuraCreadaEvent recibido para adventureId: {}", event.getAdventureId());
        pendingAdventureService.storeAventuraCreada(event);
        tryToSimulateCombat(event.getAdventureId());
    }

    @KafkaListener(topics = "${app.kafka.topic.participantes-listos}",
                   groupId = "${spring.kafka.consumer.group-id}",
                   containerFactory = "participantesListosEventKafkaListenerContainerFactory") // Y otro para este
    public void handleParticipantesListos(@Payload ParticipantesListosParaAventuraEvent event) {
        LOGGER.info("MS3: ParticipantesListosParaAventuraEvent recibido para adventureId: {}", event.getAdventureId());
        pendingAdventureService.storeParticipantesListos(event);
        tryToSimulateCombat(event.getAdventureId());
    }

    private void tryToSimulateCombat(String adventureId) {
        pendingAdventureService.getReadyAdventure(adventureId).ifPresent(adventureData -> {
            LOGGER.info("MS3: Todos los datos listos para la aventura {}. Iniciando simulación...", adventureId);
            pendingAdventureService.markAsProcessed(adventureId); // Marcar para no procesar dos veces
            combatSimulationService.simulateAdventure(
                    adventureData.getAventuraCreadaEvent(),
                    adventureData.getParticipantesEvent()
            );
            // Considera limpiar el adventureData de pendingAdventureService después de un tiempo o si la simulación fue exitosa
            // para evitar que el mapa crezca indefinidamente.
            pendingAdventureService.clearProcessedAdventure(adventureId);
        });
    }
}