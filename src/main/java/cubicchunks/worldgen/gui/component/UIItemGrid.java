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
package cubicchunks.worldgen.gui.component;

import cubicchunks.worldgen.gui.ExtraGui;
import net.malisis.core.client.gui.component.UIComponent;

public class UIItemGrid extends UILayout<UIItemGrid, Integer> {

    private final UIFlatTerrainLayer layer;
    private final int gridSize;
    private boolean isLayoutDone = false;
    int location = 0;

    public UIItemGrid(ExtraGui guiFor, UIFlatTerrainLayer layerFor, int gridSize) {
        super(guiFor);
        this.layer = layerFor;
        this.gridSize = gridSize;
    }

    @Override protected boolean checkLayoutCorrect() {
        return isLayoutDone;
    }

    @Override
    protected void layout() {
        this.checkInitialized();
        if (getParent() == null) {
            return;
        }
        int lastElementPosX = 0;
        int lastElementPosY = 0;
        for (UIComponent<?> component : components) {
            if (lastElementPosX + gridSize > this.getWidth() - this.getHorizontalPadding() * 2) {
                lastElementPosX = 0;
                lastElementPosY += gridSize;
            }
            component.setSize(gridSize, gridSize);
            component.setPosition(lastElementPosX, lastElementPosY);
            lastElementPosX += gridSize;
        }
        isLayoutDone = true;
    }

    @Override
    protected void initLayout() {
    }

    @Override
    protected void onAdd(UIComponent<?> comp, Integer at) {
    }

    @Override
    protected void onRemove(UIComponent<?> comp, Integer at) {
    }

    @Override
    protected Integer findNextLocation() {
        return location++;
    }
}
