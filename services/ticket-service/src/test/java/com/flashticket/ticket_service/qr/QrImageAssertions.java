package com.flashticket.ticket_service.qr;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

public final class QrImageAssertions {
  private QrImageAssertions() {}

  public static String decode(byte[] png) throws Exception {
    var image = ImageIO.read(new ByteArrayInputStream(png));
    int width = image.getWidth(), height = image.getHeight();
    var pixels = image.getRGB(0, 0, width, height, null, 0, width);
    return new QRCodeReader()
        .decode(
            new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))))
        .getText();
  }
}
