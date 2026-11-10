package dev.luka.compat;

/** The vertex format names of Minecraft 1.8. 1.7.10's Tessellator works the format out from the vertices themselves. */
public final class DefaultVertexFormats {
  public static final int POSITION = 0;
  public static final int POSITION_COLOR = 1;
  public static final int POSITION_TEX = 2;
  public static final int POSITION_TEX_COLOR = 3;
  public static final int POSITION_NORMAL = 4;
  public static final int POSITION_TEX_NORMAL = 5;

  private DefaultVertexFormats() {}
}
