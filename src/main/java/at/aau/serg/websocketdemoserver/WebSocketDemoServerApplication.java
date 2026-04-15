package at.aau.serg.websocketdemoserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Haupteinstiegspunkt für den Spring Boot Server der Weltreise App.
 */
@SpringBootApplication
public class WebSocketDemoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSocketDemoServerApplication.class, args);
    }

}
