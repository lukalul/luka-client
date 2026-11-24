package dev.cbui.feature.shotchat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;

/**
 * Re-packs an 8-bit RGB or RGBA PNG with a per-row filter choice and maximum deflate effort, trying a couple of
 * compressor strategies, and keeps the result only when it is smaller and decodes to exactly the same pixels.
 */
final class PngRepack {
  private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  private PngRepack() {}

  /** Returns true when the file was replaced by a smaller one. */
  static boolean shrink(File file) throws IOException {
    if (!plainEightBit(file)) return false;
    BufferedImage image = ImageIO.read(file);
    if (image == null) return false;
    int w = image.getWidth();
    int h = image.getHeight();
    boolean alpha = image.getColorModel().hasAlpha();
    int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
    byte[] best = null;
    for (int strategy : new int[] {Deflater.DEFAULT_STRATEGY, Deflater.FILTERED}) {
      byte[] candidate = encode(argb, w, h, alpha, strategy);
      if (best == null || candidate.length < best.length) best = candidate;
    }
    long before = file.length();
    if (best == null || best.length >= before) return false;
    BufferedImage check;
    try {
      check = ImageIO.read(new ByteArrayInputStream(best));
    } catch (IOException unreadable) {
      return false;
    }
    if (check == null || check.getWidth() != w || check.getHeight() != h) return false;
    int[] again = check.getRGB(0, 0, w, h, null, 0, w);
    if (!alpha) {
      for (int i = 0; i < again.length; i++) {
        again[i] |= 0xFF000000;
        argb[i] |= 0xFF000000;
      }
    }
    if (!Arrays.equals(argb, again)) return false;
    File temp = new File(file.getParentFile(), file.getName() + ".repack");
    Files.write(temp.toPath(), best);
    try {
      Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException noAtomic) {
      Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp.toPath());
    }
    return true;
  }

  /** Only plain 8-bit truecolour files are re-packed, so reading and writing them can never change a pixel. */
  private static boolean plainEightBit(File file) throws IOException {
    try (InputStream raw = new FileInputStream(file); DataInputStream in = new DataInputStream(raw)) {
      byte[] sig = new byte[8];
      in.readFully(sig);
      if (!Arrays.equals(sig, SIGNATURE)) return false;
      int length = in.readInt();
      byte[] type = new byte[4];
      in.readFully(type);
      if (length != 13 || !"IHDR".equals(new String(type, StandardCharsets.US_ASCII))) return false;
      in.readInt(); // width
      in.readInt(); // height
      int depth = in.readUnsignedByte();
      int colourType = in.readUnsignedByte();
      in.readUnsignedByte(); // compression
      in.readUnsignedByte(); // filter method
      int interlace = in.readUnsignedByte();
      return depth == 8 && (colourType == 2 || colourType == 6) && interlace == 0;
    }
  }

  private static byte[] encode(int[] argb, int w, int h, boolean alpha, int strategy) throws IOException {
    int bpp = alpha ? 4 : 3;
    int stride = w * bpp;
    byte[] previous = new byte[stride];
    byte[] current = new byte[stride];
    byte[] candidate = new byte[stride];
    byte[] chosen = new byte[stride + 1]; // filter type byte, then the filtered row
    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
    deflater.setStrategy(strategy);
    ByteArrayOutputStream idat = new ByteArrayOutputStream(argb.length);
    byte[] buffer = new byte[1 << 16];
    try {
      for (int y = 0; y < h; y++) {
        int at = 0;
        for (int x = 0, i = y * w; x < w; x++, i++) {
          int p = argb[i];
          current[at++] = (byte) (p >>> 16);
          current[at++] = (byte) (p >>> 8);
          current[at++] = (byte) p;
          if (alpha) current[at++] = (byte) (p >>> 24);
        }
        int bestType = 0;
        long bestScore = Long.MAX_VALUE;
        for (int type = 0; type <= 4; type++) {
          long score = filter(type, current, previous, candidate, bpp);
          if (score < bestScore) {
            bestScore = score;
            bestType = type;
            System.arraycopy(candidate, 0, chosen, 1, stride);
          }
        }
        chosen[0] = (byte) bestType;
        deflater.setInput(chosen, 0, stride + 1);
        while (!deflater.needsInput()) {
          int n = deflater.deflate(buffer);
          idat.write(buffer, 0, n);
        }
        byte[] swap = previous;
        previous = current;
        current = swap;
      }
      deflater.finish();
      while (!deflater.finished()) {
        int n = deflater.deflate(buffer);
        idat.write(buffer, 0, n);
      }
    } finally {
      deflater.end();
    }
    ByteArrayOutputStream png = new ByteArrayOutputStream(idat.size() + 64);
    png.write(SIGNATURE);
    ByteArrayOutputStream header = new ByteArrayOutputStream(13);
    writeInt(header, w);
    writeInt(header, h);
    header.write(8);
    header.write(alpha ? 6 : 2);
    header.write(0);
    header.write(0);
    header.write(0);
    chunk(png, "IHDR", header.toByteArray());
    chunk(png, "IDAT", idat.toByteArray());
    chunk(png, "IEND", new byte[0]);
    return png.toByteArray();
  }

  /** Applies PNG filter {@code type} to a row; returns the sum of the filtered bytes as signed values (smaller packs better). */
  private static long filter(int type, byte[] row, byte[] up, byte[] out, int bpp) {
    long score = 0;
    for (int i = 0; i < row.length; i++) {
      int a = i >= bpp ? row[i - bpp] & 0xFF : 0;
      int b = up[i] & 0xFF;
      int c = i >= bpp ? up[i - bpp] & 0xFF : 0;
      int x = row[i] & 0xFF;
      int v;
      if (type == 0) v = x;
      else if (type == 1) v = x - a;
      else if (type == 2) v = x - b;
      else if (type == 3) v = x - ((a + b) >> 1);
      else v = x - paeth(a, b, c);
      out[i] = (byte) v;
      score += Math.abs((byte) v);
    }
    return score;
  }

  private static int paeth(int a, int b, int c) {
    int p = a + b - c;
    int pa = Math.abs(p - a);
    int pb = Math.abs(p - b);
    int pc = Math.abs(p - c);
    if (pa <= pb && pa <= pc) return a;
    return pb <= pc ? b : c;
  }

  private static void chunk(ByteArrayOutputStream png, String type, byte[] data) throws IOException {
    byte[] name = type.getBytes(StandardCharsets.US_ASCII);
    writeInt(png, data.length);
    png.write(name);
    png.write(data);
    CRC32 crc = new CRC32();
    crc.update(name);
    crc.update(data);
    writeInt(png, (int) crc.getValue());
  }

  private static void writeInt(ByteArrayOutputStream out, int v) {
    out.write(v >>> 24);
    out.write(v >>> 16);
    out.write(v >>> 8);
    out.write(v);
  }
}
