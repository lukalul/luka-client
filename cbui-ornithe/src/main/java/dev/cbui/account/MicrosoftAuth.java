package dev.cbui.account;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Microsoft sign-in for Minecraft: the device-code flow (the person enters a short code at microsoft.com/link), then
 * Xbox Live, XSTS and the Minecraft services. Needs the client ID of an Azure app registration (launcher.properties).
 */
public final class MicrosoftAuth {
  private static final String TENANT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
  private static final String SCOPE = "XboxLive.signin offline_access"; // Prism asks for XboxLive.offline_access, but only its own app registration has that permission

  // Some app registrations only work through login.live.com (the "title auth" flow, MBI_SSL scope) instead of the Entra device-code
  // endpoints. Set msLiveFlow in launcher.properties to choose; the default client ID uses this flow.
  private static final String LIVE = "https://login.live.com/";
  private static final String LIVE_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

  /** What a finished sign-in gives: the Minecraft access token plus who it belongs to. */
  public static final class Session {
    public String name, uuid, accessToken, refreshToken;
  }

  /** Every step and failure goes here (a file next to the launcher), so a failed sign-in can be read afterwards. */
  static volatile Consumer<String> log = line -> { };

  private final String clientId;
  private final boolean liveFlow;

  public MicrosoftAuth(String clientId, boolean liveFlow) {
    this.clientId = clientId;
    this.liveFlow = liveFlow;
  }

  private boolean official() {
    return liveFlow;
  }

  public boolean configured() {
    return clientId != null && !clientId.trim().isEmpty();
  }

  /** Step 1 and 2: shows the code through {@code prompt} (code, url) and waits until it has been entered. */
  public Session signIn(java.util.function.BiConsumer<String, String> prompt, Consumer<String> status, java.util.function.BooleanSupplier cancelled)
      throws IOException, InterruptedException {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_id", clientId);
    form.put("scope", official() ? LIVE_SCOPE : SCOPE);
    if (official()) form.put("response_type", "device_code");
    String start = Http.postForm(official() ? LIVE + "oauth20_connect.srf" : TENANT + "devicecode", form);
    String deviceCode = Http.str(start, "device_code");
    String userCode = Http.str(start, "user_code");
    String url = Http.str(start, "verification_uri");
    if (deviceCode == null || userCode == null) throw new IOException("Microsoft did not give a sign-in code: " + start);
    int interval = Math.max(2, Http.integer(start, "interval", 5));
    int expires = Http.integer(start, "expires_in", 900);
    log.accept("device code issued, waiting for the person to enter it");
    prompt.accept(userCode, url == null ? "https://www.microsoft.com/link" : url);
    long deadline = System.currentTimeMillis() + expires * 1000L;
    while (System.currentTimeMillis() < deadline && !cancelled.getAsBoolean()) {
      Thread.sleep(interval * 1000L);
      Map<String, String> poll = new LinkedHashMap<>();
      poll.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
      poll.put("client_id", clientId);
      poll.put("device_code", deviceCode);
      String answer = Http.postForm(official() ? LIVE + "oauth20_token.srf" : TENANT + "token", poll);
      String access = Http.str(answer, "access_token");
      if (access != null) return finish(access, Http.str(answer, "refresh_token"), status);
      String error = Http.str(answer, "error");
      if (error != null && !error.equals("authorization_pending") && !error.equals("slow_down")) {
        log.accept("token poll error: " + error + " " + Http.str(answer, "error_description"));
        throw new IOException("Sign-in failed: " + error);
      }
    }
    throw new IOException("Sign-in was cancelled or the code expired");
  }

  /** Signs in again silently with a stored refresh token. */
  public Session refresh(String refreshToken, Consumer<String> status) throws IOException {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_id", clientId);
    form.put("grant_type", "refresh_token");
    form.put("refresh_token", refreshToken);
    form.put("scope", official() ? LIVE_SCOPE : SCOPE);
    String answer = Http.postForm(official() ? LIVE + "oauth20_token.srf" : TENANT + "token", form);
    String access = Http.str(answer, "access_token");
    if (access == null) throw new IOException("Microsoft refused the saved login - sign in again");
    String next = Http.str(answer, "refresh_token");
    return finish(access, next == null ? refreshToken : next, status);
  }

  private interface Call {
    String run() throws IOException;
  }

  /** Runs one HTTP step; a failure is logged and tagged with the step's name, so the person sees where it stopped. */
  private static String step(String name, Call call) throws IOException {
    try {
      String answer = call.run();
      log.accept(name + ": ok");
      return answer;
    } catch (IOException e) {
      String message = e.getMessage() == null ? "" : e.getMessage();
      log.accept(name + " FAILED: " + message);
      String xerr = xerrMessage(message);
      if (xerr != null) throw new IOException(xerr);
      if (name.startsWith("Minecraft sign-in") && (message.contains("INVALID_REGISTRATION") || message.contains("HTTP 403"))) {
        throw new IOException("Minecraft refused this app ID: Mojang has to approve an Azure app before it can sign in to Minecraft "
            + "(request it at aka.ms/mce-reviewappid). " + message);
      }
      throw new IOException(name + " failed: " + message);
    }
  }

  /** Xbox's error codes, in Prism's words. Null when the failure is not one of them. */
  private static String xerrMessage(String body) {
    int at = body.indexOf("\"XErr\"");
    if (at < 0) return null;
    int i = body.indexOf(':', at) + 1;
    while (i < body.length() && body.charAt(i) == ' ') i++;
    long code = 0;
    while (i < body.length() && Character.isDigit(body.charAt(i))) code = code * 10 + (body.charAt(i++) - '0');
    switch ((int) (code - 2148916000L)) {
      case 233: return "This Microsoft account has no Xbox profile. Buy Minecraft Java Edition on minecraft.net first (Xbox error " + code + ").";
      case 235: return "Xbox Live is not available in this account's country (Xbox error " + code + ").";
      case 238: return "This is an underage account that is not in a Microsoft family yet (Xbox error " + code + ").";
      case 236: return "Proof of age is required on this Microsoft account (Xbox error " + code + ").";
      case 237: return "This account has reached its playtime limit (Xbox error " + code + ").";
      case 227: return "Xbox banned this account (Xbox error " + code + ").";
      case 229: return "A guardian has to allow online play on this account (Xbox error " + code + ").";
      case 234: return "The Xbox terms of service have not been accepted on this account (Xbox error " + code + ").";
      default: return "Xbox refused the sign-in (Xbox error " + code + ").";
    }
  }

  private Session finish(String msAccess, String refresh, Consumer<String> status) throws IOException {
    log.accept("Microsoft token received");
    status.accept("Signing in to Xbox Live");
    String xbl = step("Xbox Live", () -> Http.postJson(
        "https://user.auth.xboxlive.com/user/authenticate",
        "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"" + (official() ? "t=" : "d=") + msAccess + "\"},"
            + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}",
        null));
    final String xblToken = Http.str(xbl, "Token");
    final String userHash = Http.str(xbl, "uhs");
    status.accept("Getting the Minecraft ticket");
    String xsts = step("Xbox security token", () -> Http.postJson(
        "https://xsts.auth.xboxlive.com/xsts/authorize",
        "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},"
            + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}",
        null));
    final String xstsToken = Http.str(xsts, "Token");
    if (xstsToken == null) throw new IOException("Xbox did not give a security token: " + xsts);
    status.accept("Signing in to Minecraft");
    // the launcher endpoint Prism uses; the older login_with_xbox is the fallback
    String login;
    try {
      login = step("Minecraft sign-in", () -> Http.postJson(
          "https://api.minecraftservices.com/launcher/login",
          "{\"xtoken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\",\"platform\":\"PC_LAUNCHER\"}",
          null));
    } catch (IOException first) {
      log.accept("launcher/login failed, trying login_with_xbox");
      login = step("Minecraft sign-in (login_with_xbox)", () -> Http.postJson(
          "https://api.minecraftservices.com/authentication/login_with_xbox",
          "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}",
          null));
    }
    final String mcToken = Http.str(login, "access_token");
    if (mcToken == null) throw new IOException("Minecraft sign-in failed: " + login);
    String profile = step("Minecraft profile", () -> Http.get("https://api.minecraftservices.com/minecraft/profile", mcToken));
    String uuid = Http.str(profile, "id");
    String name = Http.str(profile, "name");
    if (uuid == null || name == null) throw new IOException("This account does not own Minecraft Java Edition");
    Session session = new Session();
    session.name = name;
    session.uuid = uuid;
    session.accessToken = mcToken;
    session.refreshToken = refresh;
    log.accept("Signed in as " + name);
    return session;
  }
}
