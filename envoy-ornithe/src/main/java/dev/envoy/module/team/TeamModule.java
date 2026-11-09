package dev.envoy.module.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import dev.envoy.Envoy;
import dev.envoy.module.nametag.AdventureText;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;

public final class TeamModule {
    private static final String UPDATE = "lunarclient.apollo.team.v1.UpdateTeamMembersMessage";
    private static final String RESET = "lunarclient.apollo.team.v1.ResetTeamMembersMessage";
    private static final int DEFAULT_MARKER = 0xFFFFFFFF;
    private TeamModule() { }

    public static void register(ApolloNetwork network) {
        network.register(UPDATE, TeamModule::acceptRoster);
        network.register(RESET, message -> clearRoster());
    }

    private static void acceptRoster(Any envelope) throws Exception {
        List<TeamState.Member> replacement = decodeRoster(envelope.getValue().newCodedInput());
        TeamState.update(replacement);
        Envoy.LOGGER.info("[TeamView] {} teammate(s)", replacement.size());
    }

    private static void clearRoster() {
        TeamState.reset();
        Envoy.LOGGER.info("[TeamView] reset");
    }

    private static List<TeamState.Member> decodeRoster(CodedInputStream wire) throws Exception {
        List<TeamState.Member> roster = new ArrayList<>();
        for (int tag = wire.readTag(); tag != 0; tag = wire.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) != 1) { wire.skipField(tag); continue; }
            TeamState.Member member = MemberDraft.decode(wire.readBytes()).finish();
            if (member != null) roster.add(member);
        }
        return roster;
    }

    private static final class MemberDraft {
        UUID id;
        Position position;
        int color = DEFAULT_MARKER;
        List<AdventureText.Run> label = Collections.emptyList();

        static MemberDraft decode(ByteString bytes) throws Exception {
            MemberDraft result = new MemberDraft();
            CodedInputStream wire = bytes.newCodedInput();
            for (int tag = wire.readTag(); tag != 0; tag = wire.readTag()) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 1: result.id = ApolloProtos.parseUuid(wire.readBytes()); break;
                    case 3: result.position = Position.decode(wire.readBytes()); break;
                    case 4: result.color = ApolloProtos.parseColorArgb(wire.readBytes()); break;
                    case 5: result.label = AdventureText.parse(wire.readString()); break;
                    default: wire.skipField(tag);
                }
            }
            return result;
        }

        TeamState.Member finish() {
            return id == null || position == null ? null
                : new TeamState.Member(id, position.x, position.y, position.z, color, label);
        }
    }

    private static final class Position {
        double x, y, z;
        static Position decode(ByteString bytes) throws Exception {
            Position point = new Position();
            CodedInputStream wire = bytes.newCodedInput();
            for (int tag = wire.readTag(); tag != 0; tag = wire.readTag()) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 2: point.x = wire.readDouble(); break;
                    case 3: point.y = wire.readDouble(); break;
                    case 4: point.z = wire.readDouble(); break;
                    default: wire.skipField(tag);
                }
            }
            return point;
        }
    }
}
