package com.xvclemente.dnd.ms3.kafka.producer;

import com.xvclemente.dnd.dtos.events.AventuraFinalizadaEvent;
import com.xvclemente.dnd.dtos.events.ResultadoCombateIndividualEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CombatEventProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatEventProducer.class);

    @Value("${app.kafka.topic.combate-resultados}")
    private String topicCombateResultados;

    @Value("${app.kafka.topic.aventura-finalizada}")
    private String topicAventuraFinalizada;

    private final KafkaTemplate<String, Object> kafkaTemplate; // Object para manejar múltiples tipos de DTO

    @Autowired
    public CombatEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendResultadoCombateIndividualEvent(ResultadoCombateIndividualEvent event) {
        LOGGER.info("MS3: Enviando ResultadoCombateIndividualEvent para adventureId: {}", event.getAdventureId());
        kafkaTemplate.send(topicCombateResultados, event.getAdventureId(), event);
    }

    public void sendAventuraFinalizadaEvent(AventuraFinalizadaEvent event) {
        LOGGER.info("MS3: Enviando AventuraFinalizadaEvent para adventureId: {}", event.getAdventureId());
        kafkaTemplate.send(topicAventuraFinalizada, event.getAdventureId(), event);
    }
}