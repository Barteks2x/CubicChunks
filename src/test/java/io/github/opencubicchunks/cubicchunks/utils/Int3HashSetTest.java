package io.github.opencubicchunks.cubicchunks.utils;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

import net.minecraft.core.Vec3i;
import org.junit.Test;

public class Int3HashSetTest {
    @Test
    public void test1000BigCoordinates() {
        this.test(1000, ThreadLocalRandom::nextInt);
    }

    @Test
    public void test1000000BigCoordinates() {
        this.test(1000000, ThreadLocalRandom::nextInt);
    }

    @Test
    public void test1000SmallCoordinates() {
        this.test(1000, r -> r.nextInt(1000));
    }

    @Test
    public void test1000000SmallCoordinates() {
        this.test(1000000, r -> r.nextInt(1000));
    }

    protected void test(int nPoints, ToIntFunction<ThreadLocalRandom> rng) {
        Set<Vec3i> reference = new HashSet<>(nPoints);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        try (Int3HashSet test = new Int3HashSet()) {
            for (int i = 0; i < nPoints; i++) { //insert some random values
                int x = rng.applyAsInt(r);
                int y = rng.applyAsInt(r);
                int z = rng.applyAsInt(r);

                boolean b0 = reference.add(new Vec3i(x, y, z));
                boolean b1 = test.add(x, y, z);
                if (b0 != b1) {
                    throw new IllegalStateException();
                }
            }

            this.ensureEqual(reference, test);

            for (Iterator<Vec3i> itr = reference.iterator(); itr.hasNext(); ) { //remove some positions at random
                Vec3i pos = itr.next();

                if (r.nextInt(4) == 0) {
                    itr.remove();
                    test.remove(pos.getX(), pos.getY(), pos.getZ());
                }
            }

            this.ensureEqual(reference, test);
        }
    }

    protected void ensureEqual(Set<Vec3i> reference, Int3HashSet test) {
        checkState(reference.size() == test.size());

        reference.forEach(v -> checkState(test.contains(v.getX(), v.getY(), v.getZ())));
        test.forEach((x, y, z) -> checkState(reference.contains(new Vec3i(x, y, z))));
    }

    @Test
    public void testDuplicateInsertionBigCoordinates() {
        this.testDuplicateInsertion(ThreadLocalRandom::nextInt);
    }

    @Test
    public void testDuplicateInsertionSmallCoordinates() {
        this.testDuplicateInsertion(r -> r.nextInt(1000));
    }

    protected void testDuplicateInsertion(ToIntFunction<ThreadLocalRandom> rng) {
        Set<Vec3i> reference = new HashSet<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        try (Int3HashSet test = new Int3HashSet()) {
            this.ensureEqual(reference, test);

            for (int i = 0; i < 10000; i++) {
                int x = rng.applyAsInt(r);
                int y = rng.applyAsInt(r);
                int z = rng.applyAsInt(r);

                if (!reference.add(new Vec3i(x, y, z))) {
                    i--;
                    continue;
                }

                checkState(test.add(x, y, z)); //should be true the first time
                checkState(!test.add(x, y, z)); //should be false the second time
            }

            this.ensureEqual(reference, test);
        }
    }

    @Test
    public void testDuplicateRemovalBigCoordinates() {
        this.testDuplicateRemoval(ThreadLocalRandom::nextInt);
    }

    @Test
    public void testDuplicateRemovalSmallCoordinates() {
        this.testDuplicateRemoval(r -> r.nextInt(1000));
    }

    protected void testDuplicateRemoval(ToIntFunction<ThreadLocalRandom> rng) {
        Set<Vec3i> reference = new HashSet<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        try (Int3HashSet test = new Int3HashSet()) {
            this.ensureEqual(reference, test);

            for (int i = 0; i < 10000; i++) {
                int x = rng.applyAsInt(r);
                int y = rng.applyAsInt(r);
                int z = rng.applyAsInt(r);

                checkState(reference.add(new Vec3i(x, y, z)) == test.add(x, y, z));
            }

            this.ensureEqual(reference, test);

            reference.forEach(pos -> {
                checkState(test.remove(pos.getX(), pos.getY(), pos.getZ()));
                checkState(!test.remove(pos.getX(), pos.getY(), pos.getZ()));
            });

            this.ensureEqual(Collections.emptySet(), test);
        }
    }
}
