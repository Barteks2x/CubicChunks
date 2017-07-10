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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import cubicchunks.worldgen.gui.ExtraGui;
import net.malisis.core.client.gui.Anchor;
import net.malisis.core.client.gui.GuiRenderer;
import net.malisis.core.client.gui.component.UIComponent;
import net.malisis.core.client.gui.component.control.UIScrollBar;

import java.util.Iterator;
import java.util.Map;

public abstract class UILayout<T extends UILayout<T, LOC>, LOC> extends UIAutoResizableContainer<T> {

    private int lastWidth = Integer.MIN_VALUE, lastHeight = Integer.MIN_VALUE;
    private UIOptionScrollbar scrollbar;

    private boolean isInit = false;
    private BiMap<UIComponent<?>, LOC> entries = HashBiMap.create();
    private boolean scheduledLayout;

    public UILayout(ExtraGui gui) {
        super(gui);
    }

    protected abstract LOC findNextLocation();

    /**
     * Recalculate positions and sizes of components
     */
    protected abstract void layout();

    /**
     * Done before the first layout, when init() is called.
     * <p>
     * Allows to add initialization logic for example creating data structures that help with layout.
     * <p>
     * After this method is called no components can be added or removed.
     */
    protected abstract void initLayout();

    protected abstract void onAdd(UIComponent<?> comp, LOC at);

    protected abstract void onRemove(UIComponent<?> comp, LOC at);

    protected boolean isInit() {
        return this.isInit;
    }

    protected int getLayoutWidth(UIComponent<?> comp) {
        return (comp instanceof ISpecialLayoutSize) ? ((ISpecialLayoutSize) comp).getLayoutWidth() : comp.getWidth();
    }

    protected int getLayoutHeight(UIComponent<?> comp) {
        return (comp instanceof ISpecialLayoutSize) ? ((ISpecialLayoutSize) comp).getLayoutHeight() : comp.getHeight();
    }

    protected void checkNotInitialized() {
        if (isInit()) {
            throw new IllegalStateException("Already initialized");
        }
    }

    protected void checkInitialized() {
        if (!isInit()) {
            throw new IllegalStateException("Not initialized");
        }
    }

    protected Map<UIComponent<?>, LOC> componentToLocationMap() {
        return entries;
    }

    protected Map<LOC, UIComponent<?>> locationToComponentMap() {
        return entries.inverse();
    }

    protected LOC locationOf(UIComponent<?> comp) {
        return entries.get(comp);
    }

    public T init() {
        this.isInit = true;
        this.entries.forEach((c, loc) -> super.add(c));
        this.entries = Maps.unmodifiableBiMap(entries);
        this.initLayout();
        this.scrollbar = new UIOptionScrollbar((ExtraGui) getGui(), (T) this, UIScrollBar.Type.VERTICAL);
        this.scrollbar.setPosition(6, 0, Anchor.RIGHT);
        this.scrollbar.setVisible(true);
        layout();
        return (T) this;
    }

    /**
     * Checks if the layout is correct. this should be reasonably fast as it's called every frame.
     * This method can ignore changes to the container size.
     *
     * layout() should not rely on this method being called before layout.
     */
    protected abstract boolean checkLayoutCorrect();

    public T add(UIComponent<?> component, LOC at) {
        this.checkNotInitialized();
        this.entries.put(component, at);
        this.onAdd(component, at);
        this.onContentUpdate();
        return (T) this;
    }

    @Override
    public void add(UIComponent<?>... components) {
        this.checkNotInitialized();
        for (UIComponent c : components) {
            add(c, findNextLocation());
        }
    }

    @Override
    public void remove(UIComponent<?> component) {
        this.checkNotInitialized();
        LOC loc = this.entries.remove(component);
        this.onRemove(component, loc);
    }

    @Override
    public void removeAll() {
        this.checkNotInitialized();
        Iterator<BiMap.Entry<UIComponent<?>, LOC>> it = this.entries.entrySet().iterator();
        while (it.hasNext()) {
            BiMap.Entry<UIComponent<?>, LOC> e = it.next();
            it.remove();
            this.onRemove(e.getKey(), e.getValue());
        }
    }

    @Override
    public float getScrollStep() {
        float contentSize = getContentHeight() - getHeight();
        float scrollStep = super.getScrollStep() * 1000;
        float scrollFraction = scrollStep / contentSize;
        return scrollFraction;
    }

    @Override
    public void drawForeground(GuiRenderer renderer, int mouseX, int mouseY, float partialTick) {
        this.checkInitialized();
        if (getWidth() != lastWidth || getHeight() != lastHeight || !checkLayoutCorrect()) {
            lastWidth = getWidth();
            lastHeight = getHeight();
            layout();
        }
        super.drawForeground(renderer, mouseX, mouseY, partialTick);
    }
}
