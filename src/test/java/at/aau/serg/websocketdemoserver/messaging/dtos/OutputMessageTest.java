package at.aau.serg.websocketdemoserver.messaging.dtos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputMessageTest {

    @Test
    void storesAllFields() {
        OutputMessage msg = new OutputMessage("alice", "hello", "12:00");

        assertThat(msg.getFrom()).isEqualTo("alice");
        assertThat(msg.getText()).isEqualTo("hello");
        assertThat(msg.getTime()).isEqualTo("12:00");
    }
}
