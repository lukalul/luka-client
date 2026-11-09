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

/**
 * The accounts the launcher knows: offline names and Microsoft accounts (kept with their refresh token). The running game edits the
 * same file, so the list is read again whenever the file has changed; otherwise the launcher would hide those accounts and overwrite them.
 */
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
  private long loadedAt = -1;

  Accounts(File file) {
    this.file = file;
    reload();
  }

  /** Reads the file if it changed since the last read. Account objects are kept, so a sign-in in progress still points at the right one. */
  private void reload() {
    long changed = file.lastModified();
    if (changed == loadedAt) return;
    Properties p = new Properties();
    if (file.isFile()) {
      try (InputStream in = new FileInputStream(file)) {
        p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
      } catch (IOException | IllegalArgumentException unreadable) {
        return; // mid-write or damaged: keep what is in memory and try again later
      }
    }
    try {
      int count = Integer.parseInt(p.getProperty("count", "0"));
      List<Account> read = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        String type = p.getProperty(i + ".type", "offline");
        String name = p.getProperty(i + ".name", "Player");
        String uuid = p.getProperty(i + ".uuid", offlineUuid(name));
        Account a = find(type, uuid);
        if (a == null) a = new Account(type, name, uuid);
        a.name = name;
        a.refreshToken = p.getProperty(i + ".refresh", "");
        read.add(a);
      }
      accounts.clear();
      accounts.addAll(read);
      selected = Math.max(0, Math.min(accounts.size() - 1, Integer.parseInt(p.getProperty("selected", "0"))));
      loadedAt = changed;
    } catch (NumberFormatException damaged) {
      // keep what is in memory
    }
  }

  private Account find(String type, String uuid) {
    for (Account a : accounts) if (a.type.equals(type) && a.uuid.equals(uuid)) return a;
    return null;
  }

  static String offlineUuid(String name) {
    return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
  }

  synchronized List<Account> all() {
    reload();
    return new ArrayList<>(accounts);
  }

  synchronized Account selected() {
    reload();
    return accounts.isEmpty() ? null : accounts.get(Math.min(selected, accounts.size() - 1));
  }

  synchronized void select(Account account) {
    reload();
    selected = Math.max(0, accounts.indexOf(account));
    save();
  }

  synchronized Account addOffline(String name) {
    reload();
    for (Account a : accounts) if (!a.microsoft() && a.name.equalsIgnoreCase(name)) { select(a); return a; }
    Account a = new Account("offline", name, offlineUuid(name));
    accounts.add(a);
    select(a);
    return a;
  }

  synchronized Account addMicrosoft(String name, String uuid, String refreshToken) {
    reload();
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

  synchronized void remove(Account account) {
    reload();
    accounts.remove(account);
    selected = 0;
    save();
  }

  synchronized void save() {
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
      File temp = new File(file.getPath() + ".tmp");
      try (OutputStream out = new FileOutputStream(temp)) {
        p.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), "luka accounts (the refresh token is a login: keep this file private)");
      }
      java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      loadedAt = file.lastModified();
    } catch (IOException ignored) {
      // accounts are re-added on the next sign in
    }
  }
}
