package dev.luka.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** The accounts the launcher knows: offline names and Microsoft accounts (kept with their refresh token). */
final class Accounts {
  static final class Account {
    final String type; // "offline" or "msa"
    String name;
    String uuid;
    String refreshToken = "";

    Account(String type, String name, String uuid) {
      this.type = type;
      this.name = name;
      this.uuid = uuid;
    }

    boolean microsoft() {
      return "msa".equals(type);
    }
  }

  private final File file;
  private final List<Account> accounts = new ArrayList<>();
  private int selected;

  Accounts(File file) {
    this.file = file;
    Properties p = new Properties();
    if (file.isFile()) {
      try (InputStream in = new FileInputStream(file)) {
        p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
      } catch (IOException ignored) {
        // start empty
      }
    }
    int count = Integer.parseInt(p.getProperty("count", "0"));
    for (int i = 0; i < count; i++) {
      String type = p.getProperty(i + ".type", "offline");
      Account a = new Account(type, p.getProperty(i + ".name", "Player"), p.getProperty(i + ".uuid", offlineUuid(p.getProperty(i + ".name", "Player"))));
      a.refreshToken = p.getProperty(i + ".refresh", "");
      accounts.add(a);
    }
    selected = Math.max(0, Math.min(accounts.size() - 1, Integer.parseInt(p.getProperty("selected", "0"))));
  }

  static String offlineUuid(String name) {
    return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
  }

  List<Account> all() {
    return accounts;
  }

  Account selected() {
    return accounts.isEmpty() ? null : accounts.get(selected);
  }

  void select(Account account) {
    selected = Math.max(0, accounts.indexOf(account));
    save();
  }

  Account addOffline(String name) {
    for (Account a : accounts) if (!a.microsoft() && a.name.equalsIgnoreCase(name)) { select(a); return a; }
    Account a = new Account("offline", name, offlineUuid(name));
    accounts.add(a);
    select(a);
    return a;
  }

  Account addMicrosoft(String name, String uuid, String refreshToken) {
    for (Account a : accounts) {
      if (a.microsoft() && a.uuid.equals(uuid)) {
        a.name = name;
        a.refreshToken = refreshToken;
        select(a);
        return a;
      }
    }
    Account a = new Account("msa", name, uuid);
    a.refreshToken = refreshToken;
    accounts.add(a);
    select(a);
    return a;
  }

  void remove(Account account) {
    accounts.remove(account);
    selected = 0;
    save();
  }

  void save() {
    Properties p = new Properties();
    p.setProperty("count", String.valueOf(accounts.size()));
    p.setProperty("selected", String.valueOf(selected));
    for (int i = 0; i < accounts.size(); i++) {
      Account a = accounts.get(i);
      p.setProperty(i + ".type", a.type);
      p.setProperty(i + ".name", a.name);
      p.setProperty(i + ".uuid", a.uuid);
      p.setProperty(i + ".refresh", a.refreshToken);
    }
    try {
      file.getParentFile().mkdirs();
      try (OutputStream out = new FileOutputStream(file)) {
        p.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), "luka accounts (the refresh token is a login: keep this file private)");
      }
    } catch (IOException ignored) {
      // accounts are re-added on the next sign in
    }
  }
}
