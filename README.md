# D&D Microservices - MS3: Combat Arena (`dndms-ms3-combat-arena`)

## Propósito
Este microservicio orquesta y simula los encuentros de combate definidos por una aventura y los participantes (PJs y ENs) que se han unido a ella.

## Responsabilidades Clave
- Consumir la información de la aventura y los participantes.
- Simular el número de encuentros especificado en la aventura.
- Aplicar reglas de combate simplificadas para determinar los resultados de cada enfrentamiento.
- Publicar los resultados de cada combate individual.
- Publicar el resultado final de la aventura (PJs ganadores, oro otorgado).

## Tecnologías
- Java, Spring Boot
- Spring Kafka (Productor y Consumidor)
- DTOs compartidos vía Git Submodule (`dndms-event-dtos` referenciado en `shared-dtos-module`)

## Eventos Publicados
- `ResultadoCombateIndividualEvent` (al topic: `combate-resultados-topic`)
- `AventuraFinalizadaEvent` (al topic: `aventura-finalizada-topic`)

## Eventos Consumidos
- `AventuraCreadaEvent` (del topic: `aventuras-topic`)
- `ParticipantesListosParaAventuraEvent` (del topic: `participantes-topic`)

## API Endpoints
- Ninguno planeado inicialmente para exposición pública. Podría tener APIs internas si otros servicios necesitan consultarlo.

## Cómo Construir y Ejecutar Localmente
1. Asegúrate de que los submódulos Git estén inicializados y actualizados:
   `git submodule init`
   `git submodule update --remote`
2. Construye con Maven:
   `mvn clean package`
3. Ejecuta la aplicación (requiere Kafka corriendo):
   `java -jar target/dndms-ms3-combat-arena-0.0.1-SNAPSHOT.jar`