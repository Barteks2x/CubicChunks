/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.util;

import cubicchunks.world.cube.Cube;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class Predicates {

    public static final Predicate<Cube> CUBE_NOT_EMPTY = new Predicate<Cube>() {

        @Override
        public boolean test(Cube cube) {
            return !cube.isEmpty();
        }
    };

    public static final BiPredicate<Chunk, Integer> CHUNK_NOT_EMPTY_AT = new BiPredicate<Chunk, Integer>() {

        @Override
        public boolean test(Chunk chunk, Integer cubePosY) {
            ExtendedBlockStorage ebs = chunk.getBlockStorageArray()[cubePosY];
            return ebs != null && !ebs.isEmpty();
        }
    };

    public static final Predicate<Cube> CUBE_HAS_ENTITIES = new Predicate<Cube>() {

        @Override
        public boolean test(Cube cube) {
            return cube.getEntityContainer().size() != 0;
        }
    };

    public static final BiPredicate<Chunk, Integer> CHUNK_HAS_ENTITIES_AT = new BiPredicate<Chunk, Integer>() {

        @Override
        public boolean test(Chunk chunk, Integer cubePosY) {
            return chunk.getEntityLists()[cubePosY].size() != 0;
        }
    };
}
