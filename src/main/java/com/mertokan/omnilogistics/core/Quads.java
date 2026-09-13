package com.mertokan.omnilogistics.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/** Client only. Axis-aligned boxes for block entity renderers: CCW winding = outward normal, one sprite, one colour. */
public final class Quads {
    private Quads() {}

    /** skip = bitmask of Direction ordinals whose face is left out. */
    public static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int skip,
                           TextureAtlasSprite sp, float r, float g, float b, float a, int light, int overlay) {
        if ((skip & 1 << Direction.UP.ordinal()) == 0) quad(vc, p, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, sp, r, g, b, a, light, overlay);
        if ((skip & 1 << Direction.DOWN.ordinal()) == 0) quad(vc, p, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, sp, r, g, b, a, light, overlay);
        if ((skip & 1 << Direction.NORTH.ordinal()) == 0) quad(vc, p, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, sp, r, g, b, a, light, overlay);
        if ((skip & 1 << Direction.SOUTH.ordinal()) == 0) quad(vc, p, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, sp, r, g, b, a, light, overlay);
        if ((skip & 1 << Direction.WEST.ordinal()) == 0) quad(vc, p, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, sp, r, g, b, a, light, overlay);
        if ((skip & 1 << Direction.EAST.ordinal()) == 0) quad(vc, p, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, sp, r, g, b, a, light, overlay);
    }

    /** The four walls of a vertical column, alpha aBottom at y0 fading to aTop at y1, no caps. */
    public static void column(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                              TextureAtlasSprite sp, float r, float g, float b, float aBottom, float aTop, int light, int overlay) {
        wall(vc, p, x0, z0, x1, z0, y0, y1, 0, -1, sp, r, g, b, aBottom, aTop, light, overlay);   // north
        wall(vc, p, x1, z1, x0, z1, y0, y1, 0, 1, sp, r, g, b, aBottom, aTop, light, overlay);    // south
        wall(vc, p, x0, z1, x0, z0, y0, y1, -1, 0, sp, r, g, b, aBottom, aTop, light, overlay);   // west
        wall(vc, p, x1, z0, x1, z1, y0, y1, 1, 0, sp, r, g, b, aBottom, aTop, light, overlay);    // east
    }

    private static void wall(VertexConsumer vc, PoseStack.Pose p, float ax, float az, float bx, float bz, float y0, float y1, float nx, float nz,
                             TextureAtlasSprite sp, float r, float g, float b, float a0, float a1, int light, int overlay) {
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        vertex(vc, p, ax, y0, az, u0, v1, nx, 0, nz, r, g, b, a0, light, overlay);
        vertex(vc, p, ax, y1, az, u0, v0, nx, 0, nz, r, g, b, a1, light, overlay);
        vertex(vc, p, bx, y1, bz, u1, v0, nx, 0, nz, r, g, b, a1, light, overlay);
        vertex(vc, p, bx, y0, bz, u1, v1, nx, 0, nz, r, g, b, a0, light, overlay);
    }

    public static void quad(VertexConsumer vc, PoseStack.Pose p, float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
                            TextureAtlasSprite sp, float r, float g, float b, float a, int light, int overlay) {
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        vertex(vc, p, ax, ay, az, u0, v0, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(vc, p, bx, by, bz, u0, v1, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(vc, p, cx, cy, cz, u1, v1, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(vc, p, dx, dy, dz, u1, v0, nx, ny, nz, r, g, b, a, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, float nx, float ny, float nz,
                               float r, float g, float b, float a, int light, int overlay) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
    }
}
