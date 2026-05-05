package at.aau.serg.websocketdemoserver.game;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfig {

    @Bean
    public WorldGraph worldGraph() {
        try {
            return WorldGraph.loadFromResources();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load WorldGraph from resources", e);
        }
    }

    @Bean
    public MovementEngine movementEngine() {
        return new MovementEngine();
    }
}
