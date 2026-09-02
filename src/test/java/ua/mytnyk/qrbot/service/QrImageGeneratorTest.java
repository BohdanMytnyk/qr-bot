package ua.mytnyk.qrbot.service;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QrImageGeneratorTest {
    @Test
    void generatesReadablePngWithExpectedDimensions() throws Exception {
        byte[] png = new QrImageGenerator().generatePng("https://qr.twob.cc/example");
        assertThat(png).startsWith(0x89, 0x50, 0x4e, 0x47);
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(768);
        assertThat(image.getHeight()).isEqualTo(768);
    }
}
