package ua.mytnyk.qrbot.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class QrImageGenerator {
    public byte[] generatePng(String value) {
        try {
            var matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 768, 768,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (var y = 0; y < matrix.getHeight(); y++) {
                for (var x = 0; x < matrix.getWidth(); x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "PNG", output)) {
                throw new IllegalStateException("PNG encoder is unavailable");
            }
            return output.toByteArray();
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Could not generate QR image", exception);
        }
    }
}
