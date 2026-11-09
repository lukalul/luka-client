package dev.cbui.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.EnumFacing;

/** Better Grass, Fast: the sides of a grass block use its top texture and biome colour, so hillsides read as solid green. */
public final class BetterGrassModel implements IBakedModel {
  private static final int STRIDE = 7, U = 4, V = 5;

  private final IBakedModel base;
  private final List<BakedQuad>[] faces;

  @SuppressWarnings("unchecked")
  public BetterGrassModel(IBakedModel base) {
    this.base = base;
    this.faces = new List[EnumFacing.values().length];
    List<BakedQuad> top = base.getFaceQuads(EnumFacing.UP);
    for (EnumFacing face : EnumFacing.values()) {
      List<BakedQuad> quads = base.getFaceQuads(face);
      faces[face.ordinal()] = face.getAxis().isHorizontal() && !quads.isEmpty() && !top.isEmpty()
          ? Collections.singletonList(topTextured(quads.get(0), top.get(0), face))
          : quads;
    }
  }

  private static BakedQuad topTextured(BakedQuad side, BakedQuad top, EnumFacing face) {
    int[] vertices = side.getVertexData().clone();
    int[] source = top.getVertexData();
    for (int i = 0; i < 4; i++) {
      vertices[i * STRIDE + U] = source[i * STRIDE + U];
      vertices[i * STRIDE + V] = source[i * STRIDE + V];
    }
    return new BakedQuad(vertices, top.getTintIndex(), face);
  }

  @Override public List<BakedQuad> getFaceQuads(EnumFacing face) { return faces[face.ordinal()]; }
  @Override public List<BakedQuad> getGeneralQuads() { return new ArrayList<BakedQuad>(base.getGeneralQuads()); }
  @Override public boolean isAmbientOcclusion() { return base.isAmbientOcclusion(); }
  @Override public boolean isGui3d() { return base.isGui3d(); }
  @Override public boolean isBuiltInRenderer() { return base.isBuiltInRenderer(); }
  @Override public TextureAtlasSprite getParticleTexture() { return base.getParticleTexture(); }
  @Override public ItemCameraTransforms getItemCameraTransforms() { return base.getItemCameraTransforms(); }
}
