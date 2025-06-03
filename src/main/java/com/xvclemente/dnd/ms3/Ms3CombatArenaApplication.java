package com.xvclemente.dnd.ms3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka; // Habilitar Kafka

@SpringBootApplication
@EnableKafka // Asegúrate de tener esta anotación si usas @KafkaListener
public class Ms3CombatArenaApplication {

    public static void main(String[] args) {
        SpringApplication.run(Ms3CombatArenaApplication.class, args);
    }

}