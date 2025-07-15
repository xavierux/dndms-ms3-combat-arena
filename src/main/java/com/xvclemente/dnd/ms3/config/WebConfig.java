package com.xvclemente.dnd.ms3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Permite peticiones a cualquier ruta bajo /api/
                registry.addMapping("/api/**") 
                        // Permite explícitamente peticiones desde el servidor de desarrollo de Vue
                        .allowedOrigins("http://localhost:5173") 
                        // Permite los métodos HTTP estándar
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") 
                        // Permite todas las cabeceras en la petición
                        .allowedHeaders("*")
                        // Permite que se envíen credenciales (como cookies o tokens de autorización)
                        .allowCredentials(true);
            }
        };
    }
}