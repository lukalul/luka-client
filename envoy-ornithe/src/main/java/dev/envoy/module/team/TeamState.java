package dev.envoy.module.team;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.RandomAccess;
import java.util.UUID;
import dev.envoy.module.nametag.AdventureText;

public final class TeamState {
    public static final class Member {
        public final UUID uuid;
        public final double x, y, z;
        public final int markerArgb;
        public final List<AdventureText.Run> name;
        public Member(UUID uuid, double x, double y, double z, int markerArgb, List<AdventureText.Run> name) {
            this.uuid = uuid; this.x = x; this.y = y; this.z = z;
            this.markerArgb = markerArgb; this.name = name;
        }
    }

    private static volatile MemberList snapshot = MemberList.EMPTY;
    private TeamState() { }
    public static void update(List<Member> members) { snapshot = new MemberList(members.toArray(new Member[members.size()])); }
    public static void reset() { snapshot = MemberList.EMPTY; }
    public static List<Member> members() { return snapshot; }

    private static final class MemberList extends AbstractList<Member> implements RandomAccess {
        static final MemberList EMPTY = new MemberList(new Member[0]);
        private final Member[] values;
        MemberList(Member[] values) { this.values = Arrays.copyOf(values, values.length); }
        @Override public Member get(int index) { return values[index]; }
        @Override public int size() { return values.length; }
    }
}
