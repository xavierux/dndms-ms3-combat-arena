# D&D Microservices - MS3: Combat Arena (`dndms-ms3-combat-arena`)

## 🧭 Propósito
Este microservicio es el motor de simulación del sistema. Su función es orquestar y resolver los encuentros de combate, consumiendo la información de la aventura y sus participantes para determinar y publicar los resultados.

## 🧱 Responsabilidades Clave
* **Correlación de Eventos:** Consume y une los eventos `AventuraCreadaEvent` (de MS1) y `ParticipantesListosParaAventuraEvent` (de MS2) para una misma aventura, utilizando un servicio interno (`PendingAdventureService`) para esperar a que todos los datos estén listos.
* **Simulación de Combate Grupal:** Una vez que los datos de una aventura están completos, ejecuta una simulación de combate por turnos entre el bando de los PJs y el de los ENs.
* **Publicación de Resultados de Combate:** Emite un evento `ResultadoCombateIndividualEvent` cada vez que un combatiente derrota a otro.
* **Publicación del Resultado Final:** Al concluir la batalla, determina el resultado general de la aventura (victoria o derrota de los PJs) y publica un único evento `AventuraFinalizadaEvent` con el resumen.

---
## ⚙️ Stack Tecnológico
* **Lenguaje/Framework:** Java 17, Spring Boot 3.3.0
* **Gestión de Dependencias:** Maven
* **Comunicación de Eventos:** Spring Kafka (Productor y Consumidor).
* **DTOs Compartidos:** Consumidos como un Git Submodule desde el repositorio `dndms-event-dtos`.
* **Contenerización:** Docker.

---
## 📤 Arquitectura de Eventos

#### Eventos Publicados
* `ResultadoCombateIndividualEvent` (al topic: `combate-resultados-topic`)
* `AventuraFinalizadaEvent` (al topic: `aventura-finalizada-topic`)

#### Eventos Consumidos
* `AventuraCreadaEvent` (del topic: `aventuras-topic`)
* `ParticipantesListosParaAventuraEvent` (del topic: `participantes-topic`)

---
## 📡 API Endpoints
* Ninguno. Este servicio opera como un procesador de backend puro y no expone ninguna API REST pública. Su única interacción con el exterior es a través de eventos de Kafka.

---
## 🐳 Entorno de Desarrollo y Configuración

### Configuración
La configuración de la aplicación se gestiona a través de perfiles de Spring.

* **`application.properties`**: Contiene la configuración para ejecutar localmente desde un IDE, apuntando a `localhost`.
    * `server.port=8083`
* **`application-docker.properties`**: Anula propiedades para el entorno Docker, apuntando a los nombres de servicio de la red de Docker (ej. `kafka:29092`).

### Ejecución
Este microservicio está diseñado para ser orquestado por el archivo `docker-compose.yml` principal ubicado en el repositorio de `dndms-ms1-adventure-forge`.

1.  **Asegúrate de que la definición** para `dndms-ms3-combat-arena-app` esté presente y correcta en el `docker-compose.yml`.
2.  **Desde la raíz del proyecto `dndms-ms1-adventure-forge`**, ejecuta:
    ```bash
    # El flag --build es importante si has hecho cambios en el código de MS3
    docker-compose up -d --build
    ```