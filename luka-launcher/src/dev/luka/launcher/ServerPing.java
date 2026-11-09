package dev.luka.launcher;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** The Minecraft server list ping: how many are online and how long the server took to answer. */
final class ServerPing {
  final boolean online;
  final int players;
  final int max;
  final long millis;

  private ServerPing(boolean online, int players, int max, long millis) {
    this.online = online;
    this.players = players;
    this.max = max;
    this.millis = millis;
  }

  static ServerPing offline() {
    return new ServerPing(false, 0, 0, -1);
  }

  static ServerPing query(String host, int port) {
    try (Socket socket = new Socket()) {
      long start = System.nanoTime();
      socket.connect(new InetSocketAddress(host, port), 4000);
      socket.setSoTimeout(4000);
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      DataInputStream in = new DataInputStream(socket.getInputStream());
      ByteArrayOutputStream handshake = new ByteArrayOutputStream();
      DataOutputStream h = new DataOutputStream(handshake);
      writeVarInt(h, 0);
      writeVarInt(h, 47);
      byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
      writeVarInt(h, hostBytes.length);
      h.write(hostBytes);
      h.writeShort(port);
      writeVarInt(h, 1);
      writeVarInt(out, handshake.size());
      out.write(handshake.toByteArray());
      writeVarInt(out, 1);
      out.write(0);
      out.flush();
      readVarInt(in);
      readVarInt(in);
      int length = readVarInt(in);
      byte[] data = new byte[length];
      in.readFully(data);
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String json = new String(data, StandardCharsets.UTF_8);
      int players = Http.integer(json.substring(Math.max(0, json.indexOf("\"players\""))), "online", 0);
      int max = Http.integer(json.substring(Math.max(0, json.indexOf("\"players\""))), "max", 0);
      return new ServerPing(true, players, max, ms);
    } catch (IOException | RuntimeException e) {
      return offline();
    }
  }

  private static void writeVarInt(DataOutputStream out, int value) throws IOException {
    while ((value & -128) != 0) {
      out.write(value & 127 | 128);
      value >>>= 7;
    }
    out.write(value);
  }

  private static int readVarInt(DataInputStream in) throws IOException {
    int value = 0, shift = 0, b;
    do {
      b = in.readByte();
      value |= (b & 127) << shift;
      shift += 7;
      if (shift > 35) throw new IOException("bad varint");
    } while ((b & 128) != 0);
    return value;
  }
}
