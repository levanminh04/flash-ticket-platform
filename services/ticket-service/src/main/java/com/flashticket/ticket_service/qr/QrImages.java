package com.flashticket.ticket_service.qr;

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

/** Render an existing bearer token locally; never upload it or mint a replacement. */
public final class QrImages {
  private QrImages() {}

  public static byte[] png(String token) {
    try {
      if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new QrUnavailableException();
      var matrix =
          new QRCodeWriter()
              .encode(
                  token,
                  BarcodeFormat.QR_CODE,
                  300,
                  300,
                  Map.of(
                      EncodeHintType.MARGIN,
                      4,
                      EncodeHintType.ERROR_CORRECTION,
                      ErrorCorrectionLevel.M));
      var image =
          new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
      for (int y = 0; y < matrix.getHeight(); y++)
        for (int x = 0; x < matrix.getWidth(); x++)
          image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
      var output = new ByteArrayOutputStream();
      if (!ImageIO.write(image, "PNG", output)) throw new QrUnavailableException();
      return output.toByteArray();
    } catch (WriterException | IOException | RuntimeException failure) {
      throw new QrUnavailableException();
    }
  }
}
