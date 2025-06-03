package com.xvclemente.dnd.ms3.service;

import com.xvclemente.dnd.dtos.events.AventuraCreadaEvent;
import com.xvclemente.dnd.dtos.events.ParticipantesListosParaAventuraEvent;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingAdventureService {

    // Clase interna para guardar los datos de la aventura pendiente
    @Data
    public static class PendingAdventureData {
        private AventuraCreadaEvent aventuraCreadaEvent;
        private ParticipantesListosParaAventuraEvent participantesEvent;
        private boolean processed = false; // Para evitar doble procesamiento
    }

    private final Map<String, PendingAdventureData> pendingAdventures = new ConcurrentHashMap<>();

    public void storeAventuraCreada(AventuraCreadaEvent event) {
        pendingAdventures.computeIfAbsent(event.getAdventureId(), k -> new PendingAdventureData())
                         .setAventuraCreadaEvent(event);
    }

    public void storeParticipantesListos(ParticipantesListosParaAventuraEvent event) {
        pendingAdventures.computeIfAbsent(event.getAdventureId(), k -> new PendingAdventureData())
                         .setParticipantesEvent(event);
    }

    public Optional<PendingAdventureData> getReadyAdventure(String adventureId) {
        PendingAdventureData data = pendingAdventures.get(adventureId);
        if (data != null && data.getAventuraCreadaEvent() != null && data.getParticipantesEvent() != null && !data.isProcessed()) {
            return Optional.of(data);
        }
        return Optional.empty();
    }

    public void markAsProcessed(String adventureId) {
        PendingAdventureData data = pendingAdventures.get(adventureId);
        if (data != null) {
            data.setProcessed(true);
            // Opcional: podrías querer limpiar el mapa después de un tiempo o si ya se procesó
            // pendingAdventures.remove(adventureId); // Considera las implicaciones si los eventos llegan muy desfasados
        }
    }
     public void clearProcessedAdventure(String adventureId) {
        pendingAdventures.remove(adventureId);
    }
}