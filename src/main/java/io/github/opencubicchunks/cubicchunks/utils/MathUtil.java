package io.github.opencubicchunks.cubicchunks.utils;

import com.mojang.math.Matrix4f;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.Matrix4fAccess;

public class MathUtil {
    public static int ceilDiv(int a, int b) {
        return -Math.floorDiv(-a, b);
    }

    public static int log2(int n) {
        return (int) (Math.log(n) / Math.log(2));
    }

    public static Matrix4f createMatrix(float[] data) {
        Matrix4fAccess m = (Matrix4fAccess) (Object) new Matrix4f();
        m.setM00(data[0]);
        m.setM01(data[1]);
        m.setM02(data[2]);
        m.setM03(data[3]);
        m.setM10(data[4]);
        m.setM11(data[5]);
        m.setM12(data[6]);
        m.setM13(data[7]);
        m.setM20(data[8]);
        m.setM21(data[9]);
        m.setM22(data[10]);
        m.setM23(data[11]);
        m.setM30(data[12]);
        m.setM31(data[13]);
        m.setM32(data[14]);
        m.setM33(data[15]);
        return (Matrix4f) (Object) m;
    }

    public static int unpackXZSize(int xzPacked) {
        return (1 << (xzPacked - 1));
    }

    public static int unpackYSize(int xzPacked) {
        return ((1 << (64 - xzPacked - xzPacked)) - 32);
    }

    public static float unlerp(final float v, final float min, final float max) {
        return (v - min) / (max - min);
    }

    public static float lerp(final float a, final float min, final float max) {
        return min + a * (max - min);
    }
}