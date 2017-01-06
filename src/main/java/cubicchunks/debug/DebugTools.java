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
package cubicchunks.debug;

import cubicchunks.debug.item.GetLightValueItem;
import cubicchunks.debug.item.RelightSkyBlockItem;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class DebugTools {

    @SuppressWarnings("NullableProblems") @Nonnull
    @SidedProxy(serverSide = "cubicchunks.debug.DebugProxy", clientSide = "cubicchunks.debug.DebugClientProxy")
    private static DebugProxy proxy;

    static final Item itemRelightSkyBlock = new RelightSkyBlockItem("relight_sky_block");
    static final Item itemGetLightValue = new GetLightValueItem("get_light_value");

    static final CreativeTabs CUBIC_CHUNKS_DEBUG_TAB = new CreativeTabs("cubic_chunks_debug_tab") {
        @SideOnly(Side.CLIENT) @Override public ItemStack getTabIconItem() {
            return itemRelightSkyBlock.getDefaultInstance();
        }
    };

    public static void init() {
        proxy.initItems();
    }
}
