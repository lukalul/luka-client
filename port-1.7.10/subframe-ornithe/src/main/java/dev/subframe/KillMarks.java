package dev.subframe;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S02PacketChat;

/**
 * The kills the recording player got, found in the recorded chat: "Victim was slain by You", "Victim was shot by You" and the
 * other vanilla death messages, which servers (HCF ones included) broadcast to everyone. Each one is placed on the timeline at
 * the moment its message arrived.
 */
final class KillMarks {
    static final class Kill {
        final long ns;
        final String victim;
        final String text;

        Kill(long ns, String victim, String text) {
            this.ns = ns;
            this.victim = victim;
            this.text = text;
        }
    }

    private static final String VERBS = "slain|shot|killed|blown up|fireballed|pummeled|squashed|impaled|speared|stung|doomed|"
            + "finished off|knocked|burned|burnt|struck|obliterated|cooked|hit|slaughtered|eliminated|murdered|destroyed|executed";

    private KillMarks() {}

    static List<Kill> scan(Recording rec) {
        List<Kill> out = new ArrayList<Kill>();
        String me = rec.playerName;
        if (me == null || me.isEmpty()) {
            return out;
        }
        Pattern passive = Pattern.compile("^\\W*(?:\\[[^\\]]*\\]\\s*)*(\\w{3,16}) (?:was|were|got) (?:" + VERBS + ")(?: \\w+)? by " + Pattern.quote(me) + "\\b.*",
                Pattern.CASE_INSENSITIVE);
        Pattern active = Pattern.compile("^\\W*(?:\\[[^\\]]*\\]\\s*)*" + Pattern.quote(me) + " (?:" + VERBS + "|slew|slayed|took down|ended) (\\w{3,16})\\b.*",
                Pattern.CASE_INSENSITIVE);
        Integer chatId = PacketIds.idOf(S02PacketChat.class);
        if (chatId == null) {
            return out;
        }
        long start = rec.startTime();
        long last = Long.MIN_VALUE;
        for (Recording.Pkt p : rec.packets) {
            if (p.id != chatId.intValue() || p.t < start) {
                continue;
            }
            try {
                S02PacketChat chat = new S02PacketChat();
                chat.readPacketData(new PacketBuffer(Unpooled.wrappedBuffer(p.data)));
                String text = chat.m_02570234().getUnformattedText().replaceAll("§.", "").trim();
                Matcher m = passive.matcher(text);
                if (!m.matches()) {
                    m = active.matcher(text);
                    if (!m.matches()) {
                        continue;
                    }
                }
                String victim = m.group(1);
                if (victim.equalsIgnoreCase(me)) {
                    continue;
                }
                if (p.t - last < 100_000_000L && !out.isEmpty() && out.get(out.size() - 1).victim.equalsIgnoreCase(victim)) {
                    continue; // the same death announced twice
                }
                last = p.t;
                out.add(new Kill(p.t - start, victim, text));
            } catch (Throwable ignored) {
                // an unreadable chat packet is just not a kill
            }
        }
        return out;
    }
}
