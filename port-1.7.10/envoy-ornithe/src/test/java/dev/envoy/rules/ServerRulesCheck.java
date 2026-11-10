package dev.envoy.rules;

import dev.envoy.module.combat.CombatProtocol;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Plain-Java assertions against the recorded, player-data-free server payloads. */
public final class ServerRulesCheck {
    public static void main(String[] args) throws Exception {
        Path samples = Paths.get("src/test/resources/server-samples");
        ServerRules.reset();
        BadlionRules.parse(Files.readAllBytes(samples.resolve("hypixel-badlion-mods.json")));
        check(ServerRules.modDisabled("freelook"), "Hypixel freelook");
        check(ServerRules.modDisabled("minimap"), "Hypixel minimap");
        check(ServerRules.modDisabled("schematica"), "Hypixel schematica");
        check(!ServerRules.macrosBlocked("/lobby"), "Hypixel /lobby");
        check(ServerRules.macrosBlocked("/sellall 5"), "Hypixel /sellall");
        check(ServerRules.macrosBlocked("hello"), "Hypixel plain chat");

        ServerRules.reset();
        BadlionRules.parse(Files.readAllBytes(samples.resolve("minemen-badlion-modapi.json")));
        List<CombatProtocol.Configurable> json = ApolloJson.parse(Files.readAllBytes(samples.resolve("minemen-apollo-json.json")));
        check(json.size() == 1 && "combat".equals(json.get(0).module), "MineMen combat JSON");
        check(json.get(0).properties.get("disable-miss-penalty").getBoolValue(), "MineMen miss penalty");

        ServerRules.reset();
        String hex = new String(Files.readAllBytes(samples.resolve("strafepvp-lunar-apollo-configurable.hex")), StandardCharsets.US_ASCII).replaceAll("\\s", "");
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        List<CombatProtocol.Configurable> modules = CombatProtocol.parseOverride(bytes);
        check(modules.size() == 36, "StrafePvP module count");
        for (CombatProtocol.Configurable module : modules) ServerRules.updateApollo(module.module, module.enable, module.properties);
        check(ServerRules.macrosBlocked("/sellall 1"), "StrafePvP /sellall");
        check(ServerRules.macrosBlocked("/msg a b"), "StrafePvP /msg");
        check(ServerRules.macrosBlocked("/pay a 5"), "StrafePvP /pay");
        check(!ServerRules.macrosBlocked("/hub"), "StrafePvP /hub");
        check(has(modules, "mod_setting"), "StrafePvP mod_setting present");
        check(!ServerRules.modDisabled("freelook") && !ServerRules.modDisabled("minimap")
                && !ServerRules.modDisabled("schematica"), "StrafePvP mod_setting has no disabled mods");
        check(!enabled(modules, "height_limit") && !enabled(modules, "packet_enrichment"), "StrafePvP disabled modules");
        check(((Number)ServerRules.serverRule("max-chat-length")).intValue() == 256, "max chat length");
        check(((Number)ServerRules.serverRule("nametag-render-distance")).intValue() == 64, "nametag distance");
        System.out.println("Server rules check: Hypixel, MineMen and StrafePvP samples passed");
    }

    private static boolean enabled(List<CombatProtocol.Configurable> modules, String name) {
        for (CombatProtocol.Configurable module : modules) if (name.equals(module.module)) return module.enable;
        throw new AssertionError("Missing module " + name);
    }
    private static boolean has(List<CombatProtocol.Configurable> modules, String name) {
        for (CombatProtocol.Configurable module : modules) if (name.equals(module.module)) return true;
        return false;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
