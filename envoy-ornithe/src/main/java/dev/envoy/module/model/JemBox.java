package dev.envoy.module.model;

import org.lwjgl.opengl.GL11;

public final class JemBox {
    public static final int FACE_DOWN = 0;
    public static final int FACE_UP = 1;
    public static final int FACE_NORTH = 2;
    public static final int FACE_SOUTH = 3;
    public static final int FACE_WEST = 4;
    public static final int FACE_EAST = 5;

    private static final float PIXEL = 0.0625F;
    private static final int[][] CORNER_ORDER = {
        {5, 4, 0, 1}, {2, 3, 7, 6}, {1, 0, 3, 2},
        {4, 5, 6, 7}, {0, 4, 7, 3}, {5, 1, 2, 6}
    };
    private static final float[][] NORMAL = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, -1},
        {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
    private static final int[] FACE_UV_SLOT = {1, 0, 2, 3, 5, 4};

    private final Face[] faces = new Face[6];

    public JemBox(float[] coordinates, float sizeAdd, int[] textureOffset, int[][] faceUvs,
                  float textureWidth, float textureHeight, boolean mirrorU, boolean mirrorV) {
        float[][] corners = corners(coordinates, sizeAdd);
        float width = coordinates[3];
        float height = coordinates[4];
        float depth = coordinates[5];
        for (int side = 0; side < this.faces.length; side++) {
            int[] explicit = faceUvs == null ? null : faceUvs[FACE_UV_SLOT[side]];
            float[] rectangle = explicit == null
                ? atlasRectangle(side, textureOffset, width, height, depth)
                : explicitRectangle(side, explicit);
            if (rectangle == null || rectangle[0] == rectangle[2] || rectangle[1] == rectangle[3]) continue;
            this.faces[side] = new Face(side, corners, rectangle, textureWidth, textureHeight, mirrorU, mirrorV);
        }
    }

    private static float[][] corners(float[] box, float padding) {
        float left = box[0] - padding;
        float bottom = box[1] - padding;
        float near = box[2] - padding;
        float right = box[0] + box[3] + padding;
        float top = box[1] + box[4] + padding;
        float far = box[2] + box[5] + padding;
        return new float[][] {
            point(left, bottom, near), point(right, bottom, near),
            point(right, top, near), point(left, top, near),
            point(left, bottom, far), point(right, bottom, far),
            point(right, top, far), point(left, top, far)
        };
    }

    private static float[] point(float x, float y, float z) {
        return new float[] {x * PIXEL, y * PIXEL, z * PIXEL};
    }

    private static float[] explicitRectangle(int side, int[] uv) {
        if (side == FACE_DOWN || side == FACE_UP) return new float[] {uv[2], uv[3], uv[0], uv[1]};
        return new float[] {uv[0], uv[1], uv[2], uv[3]};
    }

    private static float[] atlasRectangle(int side, int[] origin, float width, float height, float depth) {
        if (origin == null) return null;
        float u = origin[0];
        float v = origin[1];
        switch (side) {
            case FACE_DOWN: return rect(u + depth, v, width, depth);
            case FACE_UP: return new float[] {u + depth + width, v + depth, u + depth + width * 2, v};
            case FACE_NORTH: return rect(u + depth, v + depth, width, height);
            case FACE_SOUTH: return rect(u + depth * 2 + width, v + depth, width, height);
            case FACE_WEST: return rect(u, v + depth, depth, height);
            default: return rect(u + depth + width, v + depth, depth, height);
        }
    }

    private static float[] rect(float u, float v, float width, float height) {
        return new float[] {u, v, u + width, v + height};
    }

    public void render() {
        for (Face face : this.faces) if (face != null) face.emit();
    }

    private static final class Face {
        private final int side;
        private final float[][] vertices = new float[4][];
        private final float[][] uv = new float[4][2];

        private Face(int side, float[][] corners, float[] area, float atlasWidth, float atlasHeight,
                     boolean mirrorU, boolean mirrorV) {
            this.side = side;
            for (int vertex = 0; vertex < 4; vertex++) this.vertices[vertex] = corners[CORNER_ORDER[side][vertex]];
            float left = area[0] / atlasWidth;
            float top = area[1] / atlasHeight;
            float right = area[2] / atlasWidth;
            float bottom = area[3] / atlasHeight;
            if (mirrorU) { float swap = left; left = right; right = swap; }
            if (mirrorV) { float swap = top; top = bottom; bottom = swap; }
            this.uv[0] = new float[] {right, top};
            this.uv[1] = new float[] {left, top};
            this.uv[2] = new float[] {left, bottom};
            this.uv[3] = new float[] {right, bottom};
        }

        private void emit() {
            GL11.glNormal3f(NORMAL[this.side][0], NORMAL[this.side][1], NORMAL[this.side][2]);
            for (int vertex = 0; vertex < 4; vertex++) {
                GL11.glTexCoord2f(this.uv[vertex][0], this.uv[vertex][1]);
                GL11.glVertex3f(this.vertices[vertex][0], this.vertices[vertex][1], this.vertices[vertex][2]);
            }
        }
    }
}
