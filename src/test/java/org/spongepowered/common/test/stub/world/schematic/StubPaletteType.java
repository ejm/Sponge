/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.test.stub.world.schematic;

import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.registry.Registry;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.common.test.stub.StubKey;
import org.spongepowered.common.test.stub.block.StubState;
import org.spongepowered.common.world.schematic.SpongePaletteType;

import java.util.Optional;

@SuppressWarnings({"rawtypes", "unchecked"})
public class StubPaletteType<T, R> extends SpongePaletteType<T, R> {
    @SuppressWarnings("UnnecessaryLocalVariable")
    public StubPaletteType() {
        super((s, r) -> {
            final String[] split = s.split(":");
            final StubKey key = new StubKey(split[0], split[1]);
            if (r.type() == RegistryTypes.BLOCK_TYPE) {
                final Optional t = r.findValue(key).map(v -> ((BlockType) v).defaultState());
                return t;
            }
            final Optional value = r.findValue(key);
            return value;
        }, ((rRegistry, t) -> (t instanceof StubState)
            ? ((StubState) t).key.asString()
            : ((Registry) rRegistry).valueKey(t).asString()));
    }
}
