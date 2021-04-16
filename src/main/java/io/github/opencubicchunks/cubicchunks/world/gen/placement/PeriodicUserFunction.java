package io.github.opencubicchunks.cubicchunks.world.gen.placement;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.github.opencubicchunks.cubicchunks.utils.MathUtil;
import net.minecraft.util.Mth;

//TODO: Datapack Codec for user customization.
public class PeriodicUserFunction {

    // TODO: flatten to float array for performance?
    public Entry[] values;
    private final Entry valPre;
    private final Entry valPost;
    public float minY;
    public float maxY;

    public PeriodicUserFunction(Map<Float, Float> funcMap, float minY, float maxY) {
        values = funcMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new Entry(e.getKey(), e.getValue()))
            .toArray(Entry[]::new);
        this.minY = minY;
        this.maxY = maxY;
        Entry last = values[values.length - 1];
        valPre = new Entry(last.y - (maxY - minY), last.v);
        Entry first = values[0];
        valPost = new Entry(first.y + (maxY - minY), first.v);
    }

    public float getValue(float y) {
        if (values.length == 0) {
            return 0;
        }
        if (values.length == 1) {
            return values[0].v;
        }
        y = repeatY(y);
        Entry e1 = getValue(-1);
        Entry e2 = getValue(0);
        // TODO: binary search? do we want to support functions complex enough for it to be needed? Will it improve performance?
        for (int i = 1; i <= values.length; i++) {
            if (getValue(i - 1).y < y) {
                e1 = e2;
                e2 = getValue(i);
            }
        }
        float yFract = MathUtil.unlerp(y, e1.y, e2.y);
        return MathUtil.lerp(yFract, e1.v, e2.v);
    }

    private float repeatY(float y) {
        y -= minY;
        y = Mth.positiveModulo(y, maxY - minY);
        y += minY;
        return y;
    }

    private Entry getValue(int idx) {
        if (idx == -1) {
            return valPre;
        }
        if (idx == values.length) {
            return valPost;
        }
        return values[idx];
    }

    public static class Builder {

        private Map<Float, Float> map = new HashMap<>();
        private float min;
        private float max;

        public Builder point(float y, float v) {
            this.map.put(y, v);
            return this;
        }

        public Builder repeatRange(float min, float max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public PeriodicUserFunction build() {
            return new PeriodicUserFunction(this.map, min, max);
        }
    }

    public static class Entry {

        public float y;
        public float v;

        public Entry() {
        }

        public Entry(float key, float value) {
            this.y = key;
            this.v = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Entry entry = (Entry) o;
            return Float.compare(entry.y, y) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(y);
        }

        @Override
        public String toString() {
            return "Entry{" +
                "y=" + y +
                ", v=" + v +
                '}';
        }
    }
}
