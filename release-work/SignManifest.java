import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Signs update.json so the launcher can tell it came from the luka team.
 *
 *   java SignManifest.java genkey <key file>                      makes a key pair and prints the public half
 *   java SignManifest.java sign <key file> <body.json> <out.json> writes the signed update.json
 *
 * The private key never leaves the key file and is never printed.
 */
public final class SignManifest {
  public static void main(String[] args) throws Exception {
    if (args.length == 2 && args[0].equals("genkey")) genkey(Paths.get(args[1]));
    else if (args.length == 4 && args[0].equals("sign")) sign(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
    else throw new IllegalArgumentException("usage: genkey <key file> | sign <key file> <body.json> <out.json>");
  }

  private static void genkey(Path file) throws Exception {
    if (Files.exists(file)) throw new IllegalStateException("A key already exists there. Replacing it would lock out every installed launcher.");
    KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    if (file.getParent() != null) Files.createDirectories(file.getParent());
    Files.write(file, Base64.getEncoder().encode(pair.getPrivate().getEncoded()), StandardOpenOption.CREATE_NEW);
    System.out.println(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
  }

  private static void sign(Path keyFile, Path body, Path out) throws Exception {
    byte[] text = Files.readAllBytes(body);
    byte[] key = Base64.getDecoder().decode(new String(Files.readAllBytes(keyFile), StandardCharsets.US_ASCII).trim());
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(key)));
    signer.update(text);
    String signature = Base64.getEncoder().encodeToString(signer.sign());
    String json = "{\n \"signature\": \"" + signature + "\",\n \"manifest\": \"" + escape(new String(text, StandardCharsets.UTF_8)) + "\"\n}\n";
    Files.write(out, json.getBytes(StandardCharsets.UTF_8));
  }

  private static String escape(String s) {
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c == '"' || c == '\\') b.append('\\').append(c);
      else if (c == '\n') b.append("\\n");
      else if (c == '\r') b.append("\\r");
      else if (c == '\t') b.append("\\t");
      else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
      else b.append(c);
    }
    return b.toString();
  }
}
