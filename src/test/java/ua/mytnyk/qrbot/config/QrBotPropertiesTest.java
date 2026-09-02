package ua.mytnyk.qrbot.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrBotPropertiesTest {
    @Test void rejectsMissingUsername() {
        var p = new QrBotProperties(); p.setContentChannelId(1);
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsBlankUsername() {
        var p = new QrBotProperties(); p.setBotUsername("   "); p.setContentChannelId(1);
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsZeroChannel() {
        var p = new QrBotProperties(); p.setBotUsername("bot");
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
    @Test void acceptsCompleteConfiguration() {
        var p = new QrBotProperties(); p.setBotUsername("bot"); p.setContentChannelId(1); p.validate();
    }
}
