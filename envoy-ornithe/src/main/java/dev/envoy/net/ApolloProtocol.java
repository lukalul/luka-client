package dev.envoy.net;

public final class ApolloProtocol {
    public static final String CHANNEL = "lunar:apollo";
    public static final String REGISTER_CHANNEL = "REGISTER";
    public static final String TYPE_URL_PREFIX = "type.googleapis.com/";

    private ApolloProtocol() {
        throw new AssertionError("No ApolloProtocol instances");
    }

    public static String messageType(String typeUrl) {
        return typeUrl.substring(typeUrl.lastIndexOf('/') + 1);
    }
}
