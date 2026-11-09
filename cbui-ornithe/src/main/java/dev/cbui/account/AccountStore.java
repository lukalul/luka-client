package dev.cbui.account;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

/**
 * The account list shared with the luka launcher (the same accounts.properties file), plus switching the running game's
 * player name. Only offline names can be switched in game; a Microsoft account needs its token from the launcher, so it is
 * listed and kept but not switchable here.
 */
public final class AccountStore {
  public static final class Account {
    public final String type;
    public String name;
    public String uuid;
    public String refreshToken = "";
    /** Minecraft access token of the account signed in this run (never saved). */
    String accessToken = "";

    Account(String type, String name, String uuid) {
      this.type = type;
      this.name = name;
      this.uuid = uuid;
    }

    public boolean microsoft() {
      return "msa".equals(type);
    }
  }

  private final File file;
  private final List<Account> accounts = new ArrayList<>();
  private int selected;

  public AccountStore() {
    this(false);
  }

  /** {@code keepFileSelection}: use the account the launcher saved as selected instead of the one the game runs as. */
  public AccountStore(boolean keepFileSelection) {
    File gameDir = Minecraft.getMinecraft().mcDataDir;
    File launcherFile = new File(gameDir.getAbsoluteFile().getParentFile(), "accounts.properties");
    File jar = AccountLauncher.jar();
    if (!launcherFile.isFile() && jar != null) launcherFile = new File(new File(jar.getParentFile(), "luka"), "accounts.properties");
    // inside the luka launcher the game folder is luka/game and the shared file sits one level up
    this.file = launcherFile.isFile() ? launcherFile : new File(gameDir, "config/luka-accounts.properties");
    load();
    if (accounts.isEmpty()) {
      String current = Minecraft.getMinecraft().getSession().getUsername();
      accounts.add(new Account("offline", current, offlineUuid(current)));
      selected = 0;
    }
    if (!keepFileSelection) {
      int active = indexOfActive();
      if (active >= 0) selected = active;
    }
  }

  public static String offlineUuid(String name) {
    return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
  }

  private static final String DEFAULT_CLIENT_ID = "00000000402b5328";

  /** The app ID Microsoft sign-in uses: the launcher's own setting when it is there, else luka's default. */
  public String clientId() {
    File props = new File(file.getAbsoluteFile().getParentFile(), "launcher.properties");
    if (props.isFile()) {
      Properties p = new Properties();
      try (InputStreamReader in = new InputStreamReader(new FileInputStream(props), StandardCharsets.UTF_8)) {
        p.load(in);
        String id = p.getProperty("msClientId", "").trim();
        if (!id.isEmpty()) return id;
      } catch (IOException ignored) {
        // use the default
      }
    }
    return DEFAULT_CLIENT_ID;
  }

  /** Same file as the launcher: msLiveFlow=true selects the login.live.com flow. */
  public boolean liveFlow() {
    File props = new File(file.getAbsoluteFile().getParentFile(), "launcher.properties");
    if (props.isFile()) {
      Properties p = new Properties();
      try (InputStreamReader in = new InputStreamReader(new FileInputStream(props), StandardCharsets.UTF_8)) {
        p.load(in);
        String set = p.getProperty("msLiveFlow");
        if (set != null) return Boolean.parseBoolean(set.trim());
      } catch (IOException ignored) {
        // use the default
      }
    }
    return DEFAULT_CLIENT_ID.equals(clientId());
  }

  /** Keeps (or updates) a Microsoft account after a sign-in and makes the game use it. */
  public Account addMicrosoft(MicrosoftAuth.Session session) {
    Account account = null;
    for (Account a : accounts) if (a.microsoft() && a.uuid.equals(session.uuid)) account = a;
    if (account == null) {
      account = new Account("msa", session.name, session.uuid);
      accounts.add(account);
    }
    account.name = session.name;
    account.refreshToken = session.refreshToken == null ? "" : session.refreshToken;
    apply(account, session);
    save();
    return account;
  }

  private boolean apply(Account account, MicrosoftAuth.Session session) {
    account.accessToken = session.accessToken;
    boolean ok = setSession(new Session(session.name, session.uuid, session.accessToken, "msa"));
    if (ok) selected = accounts.indexOf(account);
    return ok;
  }

  /** Signs a saved Microsoft account in again (network, so call it off the render thread) and makes the game use it. */
  public String switchToMicrosoft(Account account) {
    try {
      MicrosoftAuth auth = new MicrosoftAuth(clientId(), liveFlow());
      if (account.refreshToken.isEmpty()) return "Sign in to " + account.name + " again";
      MicrosoftAuth.Session session = auth.refresh(account.refreshToken, text -> { });
      account.name = session.name;
      account.refreshToken = session.refreshToken == null ? account.refreshToken : session.refreshToken;
      boolean ok = apply(account, session);
      save();
      return ok ? "Playing as " + session.name : "Could not switch the game's account";
    } catch (Throwable error) {
      return String.valueOf(error.getMessage());
    }
  }

  private boolean setSession(Session session) {
    try {
      Minecraft mc = Minecraft.getMinecraft();
      for (Field field : Minecraft.class.getDeclaredFields()) {
        if (field.getType() == Session.class) {
          field.setAccessible(true);
          field.set(mc, session);
          return true;
        }
      }
    } catch (Throwable ignored) {
      // fall through
    }
    return false;
  }

  public List<Account> all() {
    return accounts;
  }

  public Account selected() {
    return accounts.isEmpty() ? null : accounts.get(Math.max(0, Math.min(accounts.size() - 1, selected)));
  }

  /** The account whose name is the one the game is running as now. */
  private int indexOfActive() {
    String current = Minecraft.getMinecraft().getSession().getUsername();
    for (int i = 0; i < accounts.size(); i++) if (accounts.get(i).name.equalsIgnoreCase(current)) return i;
    return -1;
  }

  public boolean isActive(Account account) {
    return account.name.equalsIgnoreCase(Minecraft.getMinecraft().getSession().getUsername());
  }

  public Account addOffline(String name) {
    for (Account a : accounts) if (!a.microsoft() && a.name.equalsIgnoreCase(name)) return a;
    Account account = new Account("offline", name, offlineUuid(name));
    accounts.add(account);
    save();
    return account;
  }

  public void remove(Account account) {
    if (accounts.size() <= 1 || isActive(account)) return;
    accounts.remove(account);
    selected = Math.max(0, indexOfActive());
    save();
  }

  /** Makes the game use this offline account. Returns false for a Microsoft account (see switchToMicrosoft) or on failure. */
  public boolean switchTo(Account account) {
    if (account.microsoft()) return false;
    if (!setSession(new Session(account.name, account.uuid, "0", "legacy"))) return false;
    selected = accounts.indexOf(account);
    save();
    return true;
  }

  private void load() {
    if (!file.isFile()) return;
    Properties p = new Properties();
    try (InputStreamReader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
      p.load(in);
    } catch (IOException e) {
      return;
    }
    int count;
    try {
      count = Integer.parseInt(p.getProperty("count", "0"));
    } catch (NumberFormatException e) {
      return;
    }
    for (int i = 0; i < count; i++) {
      String name = p.getProperty(i + ".name", "Player");
      Account a = new Account(p.getProperty(i + ".type", "offline"), name, p.getProperty(i + ".uuid", offlineUuid(name)));
      a.refreshToken = p.getProperty(i + ".refresh", "");
      accounts.add(a);
    }
    try {
      selected = Integer.parseInt(p.getProperty("selected", "0"));
    } catch (NumberFormatException e) {
      selected = 0;
    }
  }

  private void save() {
    Properties p = new Properties();
    p.setProperty("count", String.valueOf(accounts.size()));
    p.setProperty("selected", String.valueOf(Math.max(0, selected)));
    for (int i = 0; i < accounts.size(); i++) {
      Account a = accounts.get(i);
      p.setProperty(i + ".type", a.type);
      p.setProperty(i + ".name", a.name);
      p.setProperty(i + ".uuid", a.uuid);
      p.setProperty(i + ".refresh", a.refreshToken);
    }
    try {
      file.getParentFile().mkdirs();
      try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
        p.store(out, "luka accounts (the refresh token is a login: keep this file private)");
      }
    } catch (IOException ignored) {
      // the list simply is not saved
    }
  }
}
