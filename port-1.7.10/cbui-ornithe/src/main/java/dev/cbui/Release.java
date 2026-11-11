package dev.cbui;

/** What this build ships with. Luka Online talks to the luka.wtf service (api.luka.wtf). */
public final class Release {
  /** Luka Online: sign-in (verified with Mojang), friends, and the luka icon beside other luka players' nametags. */
  public static final boolean SOCIAL = true;
  /** Capes and the Cosmetics screen: the service does not hand them out yet. */
  public static final boolean COSMETICS = false;
  /** Direct messages between friends: not on the service yet. */
  public static final boolean MESSAGES = false;
  /** The Discord application Rich Presence shows under ("Playing luka"); empty = no Discord status. */
  public static final String DISCORD_APP_ID = "";
  /** Friend pings (waypoints): not on the service yet. */
  public static final boolean PINGS = false;

  private Release() { }
}
