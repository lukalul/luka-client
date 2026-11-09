package dev.fastpacks.index;

public final class PackMetadata {
    public static final PackMetadata MISSING = new PackMetadata("Missing pack.mcmeta", -1);
    public final String description;
    public final int format;
    public PackMetadata(String description, int format) { this.description = description; this.format = format; }
}
