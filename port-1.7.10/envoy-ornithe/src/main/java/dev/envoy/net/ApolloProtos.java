package dev.envoy.net;

import java.io.IOException;
import java.util.UUID;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

public final class ApolloProtos {
    private ApolloProtos() {
        throw new AssertionError("No ApolloProtos instances");
    }

    public static UUID parseUuid(ByteString data) throws IOException {
        final long[] halves = new long[2];
        visitFields(data, new FieldReader() {
            public boolean read(int number, CodedInputStream input) throws IOException {
                if (number != 1 && number != 2) return false;
                halves[number - 1] = input.readFixed64();
                return true;
            }
        });
        return new UUID(halves[0], halves[1]);
    }

    public static long parseDurationMillis(ByteString data) throws IOException {
        final long[] duration = new long[2];
        visitFields(data, new FieldReader() {
            public boolean read(int number, CodedInputStream input) throws IOException {
                if (number == 1) {
                    duration[0] = input.readInt64();
                    return true;
                }
                if (number == 2) {
                    duration[1] = input.readInt32();
                    return true;
                }
                return false;
            }
        });
        return duration[0] * 1000L + duration[1] / 1_000_000L;
    }

    public static int parseColorArgb(ByteString data) throws IOException {
        final int[] color = new int[1];
        visitFields(data, new FieldReader() {
            public boolean read(int number, CodedInputStream input) throws IOException {
                if (number != 1) return false;
                color[0] = input.readInt32();
                return true;
            }
        });
        return color[0];
    }

    private static void visitFields(ByteString bytes, FieldReader reader) throws IOException {
        CodedInputStream input = bytes.newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (!reader.read(WireFormat.getTagFieldNumber(tag), input)) input.skipField(tag);
        }
    }

    private interface FieldReader {
        boolean read(int number, CodedInputStream input) throws IOException;
    }
}
