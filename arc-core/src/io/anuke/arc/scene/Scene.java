package io.anuke.arc.scene;

import io.anuke.arc.Application.*;
import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.function.*;
import io.anuke.arc.graphics.*;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.input.*;
import io.anuke.arc.math.*;
import io.anuke.arc.math.geom.*;
import io.anuke.arc.scene.event.*;
import io.anuke.arc.scene.event.FocusListener.*;
import io.anuke.arc.scene.event.InputEvent.*;
import io.anuke.arc.scene.style.*;
import io.anuke.arc.scene.ui.*;
import io.anuke.arc.scene.ui.layout.*;
import io.anuke.arc.util.*;
import io.anuke.arc.util.pooling.Pool.*;
import io.anuke.arc.util.pooling.*;
import io.anuke.arc.util.viewport.*;

import static io.anuke.arc.Core.graphics;


public class Scene implements InputProcessor, Disposable{
    //public final Skin skin;
    public final Group root;
    private final ObjectMap<Class, Object> styleDefaults = new ObjectMap<>();
    private final Vector2 tempCoords = new Vector2();
    private final Element[] pointerOverActors = new Element[20];
    private final boolean[] pointerTouched = new boolean[20];
    private final int[] pointerScreenX = new int[20];
    private final int[] pointerScreenY = new int[20];
    private final SnapshotArray<TouchFocus> touchFocuses = new SnapshotArray<>(true, 4, TouchFocus.class);
    private Viewport viewport;
    private int mouseScreenX, mouseScreenY;
    private Element mouseOverElement;
    private Element keyboardFocus, scrollFocus;
    private boolean actionsRequestRendering = true;

    public Scene(){
        this.viewport = new ScreenViewport(){
            @Override
            public void calculateScissors(Matrix3 batchTransform, Rectangle area, Rectangle scissor){
                ScissorStack.calculateScissors(
                getCamera(), getScreenX(), getScreenY(), getScreenWidth(), getScreenHeight(), batchTransform, area, scissor);
            }
        };

        root = new Group(){};
        root.setScene(this);

        viewport.update(graphics.getWidth(), graphics.getHeight(), true);
    }

    public Scene( Viewport viewport){
        this();
        this.viewport = viewport;
    }

    @SuppressWarnings("unchecked")
    public <T> T getStyle(Class<T> type){
        return (T)styleDefaults.getThrow(type, () -> new IllegalArgumentException("No default style for type: " + type.getSimpleName()));
    }

    public <T> void addStyle(Class<T> type, T style){
        styleDefaults.put(type, style);
    }

    public boolean hasMouse(){
        return hit(Core.input.mouseX(), Core.input.mouseY(), true) != null;
    }

    public boolean hasMouse(float mousex, float mousey){
        return hit(mousex, mousey, true) != null;
    }

    public boolean hasDialog(){
        return getKeyboardFocus() instanceof Dialog || getScrollFocus() instanceof Dialog;
    }

    public Dialog getDialog(){
        if(getKeyboardFocus() instanceof Dialog){
            return (Dialog)getKeyboardFocus();
        }else if(getScrollFocus() instanceof Dialog){
            return (Dialog)getScrollFocus();
        }
        return null;
    }

    public void draw(){
        Camera camera = viewport.getCamera();
        camera.update();

        if(!root.isVisible()) return;

        Draw.proj(camera.projection());

        root.draw();
        Draw.flush();
    }

    /** Calls {@link #act(float)} with {@link Graphics#getDeltaTime()}. */
    public void act(){
        act(graphics.getDeltaTime());
    }

    /**
     * Calls the {@link Element#act(float)} method on each actor in the stage. Typically called each frame. This method also fires
     * enter and exit events.
     * @param delta Time in seconds since the last frame.
     */
    public void act(float delta){
        // Update over actors. Done in act() because actors may change position, which can fire enter/exit without an input event.
        for(int pointer = 0, n = pointerOverActors.length; pointer < n; pointer++){
            Element overLast = pointerOverActors[pointer];
            // Check if pointer is gone.
            if(!pointerTouched[pointer]){
                if(overLast != null){
                    pointerOverActors[pointer] = null;
                    screenToStageCoordinates(tempCoords.set(pointerScreenX[pointer], pointerScreenY[pointer]));
                    // Exit over last.
                    InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
                    event.type = (InputEvent.Type.exit);
                    event.stageX = (tempCoords.x);
                    event.stageY = (tempCoords.y);
                    event.relatedActor = (overLast);
                    event.pointer = (pointer);
                    overLast.fire(event);
                    Pools.free(event);
                }
                continue;
            }
            // Update over actor for the pointer.
            pointerOverActors[pointer] = fireEnterAndExit(overLast, pointerScreenX[pointer], pointerScreenY[pointer], pointer);
        }
        // Update over element for the mouse on the desktop.
        ApplicationType type = Core.app.getType();
        if(type == ApplicationType.Desktop || type == ApplicationType.WebGL)
            mouseOverElement = fireEnterAndExit(mouseOverElement, mouseScreenX, mouseScreenY, -1);

        root.act(delta);
    }

    public Element find(String name){
        return root.find(name);
    }

    public Element findVisible(String name){
        return root.findVisible(name);
    }

    public Element find(Predicate<Element> pred){
        return root.find(pred);
    }

    /** Adds and returns a table. This table will fill the whole scene. */
    public Table table(){
        Table table = new Table();
        table.setFillParent(true);
        add(table);
        return table;
    }

    /** Adds and returns a table. This table will fill the whole scene. */
    public Table table(Consumer<Table> cons){
        Table table = new Table();
        table.setFillParent(true);
        add(table);
        cons.accept(table);
        return table;
    }

    /** Adds and returns a table. This table will fill the whole scene. */
    public Table table(Drawable style, Consumer<Table> cons){
        Table table = new Table(style);
        table.setFillParent(true);
        add(table);
        cons.accept(table);
        return table;
    }

    private Element fireEnterAndExit(Element overLast, int screenX, int screenY, int pointer){
        // Find the actor under the point.
        screenToStageCoordinates(tempCoords.set(screenX, screenY));
        Element over = hit(tempCoords.x, tempCoords.y, true);
        if(over == overLast) return overLast;

        // Exit overLast.
        if(overLast != null){
            InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
            event.stageX = (tempCoords.x);
            event.stageY = (tempCoords.y);
            event.pointer = (pointer);
            event.type = (InputEvent.Type.exit);
            event.relatedActor = (over);
            overLast.fire(event);
            Pools.free(event);
        }
        // Enter over.
        if(over != null){
            InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
            event.stageX = (tempCoords.x);
            event.stageY = (tempCoords.y);
            event.pointer = (pointer);
            event.type = (InputEvent.Type.enter);
            event.relatedActor = (overLast);
            over.fire(event);
            Pools.free(event);
        }
        return over;
    }

    /**
     * Applies a touch down event to the stage and returns true if an actor in the scene {@link Event#handle() handled} the
     * event.
     */
    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button){
        if(!isInsideViewport(screenX, screenY)) return false;

        pointerTouched[pointer] = true;
        pointerScreenX[pointer] = screenX;
        pointerScreenY[pointer] = screenY;

        screenToStageCoordinates(tempCoords.set(screenX, screenY));

        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (Type.touchDown);
        event.stageX = (tempCoords.x);
        event.stageY = (tempCoords.y);
        event.pointer = (pointer);
        event.keyCode = (button);

        Element target = hit(tempCoords.x, tempCoords.y, true);
        if(target == null){
            if(root.getTouchable() == Touchable.enabled) root.fire(event);
        }else{
            target.fire(event);
        }

        boolean handled = event.handled;
        Pools.free(event);
        return handled;
    }

    /**
     * Applies a touch moved event to the stage and returns true if an actor in the scene {@link Event#handle() handled} the
     * event. Only {@link InputListener listeners} that returned true for touchDown will receive this event.
     */
    public boolean touchDragged(int screenX, int screenY, int pointer){
        pointerScreenX[pointer] = screenX;
        pointerScreenY[pointer] = screenY;
        mouseScreenX = screenX;
        mouseScreenY = screenY;

        if(touchFocuses.size == 0) return false;

        screenToStageCoordinates(tempCoords.set(screenX, screenY));

        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (Type.touchDragged);
        event.stageX = (tempCoords.x);
        event.stageY = (tempCoords.y);
        event.pointer = (pointer);

        SnapshotArray<TouchFocus> touchFocuses = this.touchFocuses;
        TouchFocus[] focuses = touchFocuses.begin();
        for(int i = 0, n = touchFocuses.size; i < n; i++){
            TouchFocus focus = focuses[i];
            if(focus.pointer != pointer) continue;
            if(!touchFocuses.contains(focus, true)) continue; // Touch focus already gone.
            event.targetActor = focus.target;
            event.listenerActor = focus.listenerActor;
            if(focus.listener.handle(event)) event.handle();
        }
        touchFocuses.end();

        boolean handled = event.handled;
        Pools.free(event);
        return handled;
    }

    /**
     * Applies a touch up event to the stage and returns true if an actor in the scene {@link Event#handle() handled} the event.
     * Only {@link InputListener listeners} that returned true for touchDown will receive this event.
     */
    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button){
        pointerTouched[pointer] = false;
        pointerScreenX[pointer] = screenX;
        pointerScreenY[pointer] = screenY;

        if(touchFocuses.size == 0) return false;

        screenToStageCoordinates(tempCoords.set(screenX, screenY));

        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (Type.touchUp);
        event.stageX = (tempCoords.x);
        event.stageY = (tempCoords.y);
        event.pointer = (pointer);
        event.keyCode = (button);

        SnapshotArray<TouchFocus> touchFocuses = this.touchFocuses;
        TouchFocus[] focuses = touchFocuses.begin();
        for(int i = 0, n = touchFocuses.size; i < n; i++){
            TouchFocus focus = focuses[i];
            if(focus.pointer != pointer || focus.button != button) continue;
            if(!touchFocuses.removeValue(focus, true)) continue; // Touch focus already gone.
            event.targetActor = focus.target;
            event.listenerActor = focus.listenerActor;
            if(focus.listener.handle(event)) event.handle();
            Pools.free(focus);
        }
        touchFocuses.end();

        boolean handled = event.handled;
        Pools.free(event);
        return handled;
    }

    /**
     * Applies a mouse moved event to the stage and returns true if an actor in the scene {@link Event#handle() handled} the
     * event. This event only occurs on the desktop.
     */
    @Override
    public boolean mouseMoved(int screenX, int screenY){
        if(!isInsideViewport(screenX, screenY)) return false;

        mouseScreenX = screenX;
        mouseScreenY = screenY;

        screenToStageCoordinates(tempCoords.set(screenX, screenY));

        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (Type.mouseMoved);
        event.stageX = (tempCoords.x);
        event.stageY = (tempCoords.y);

        Element target = hit(tempCoords.x, tempCoords.y, true);
        if(target == null) target = root;

        target.fire(event);
        boolean handled = event.handled;
        Pools.free(event);
        return handled;
    }

    /**
     * Applies a mouse scroll event to the stage and returns true if an actor in the scene {@link Event#handle() handled} the
     * event. This event only occurs on the desktop.
     */
    @Override
    public boolean scrolled(float amountX, float amountY){
        Element target = scrollFocus == null ? root : scrollFocus;

        screenToStageCoordinates(tempCoords.set(mouseScreenX, mouseScreenY));

        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (InputEvent.Type.scrolled);
        event.scrollAmountX = amountX;
        event.scrollAmountY = amountY;
        event.stageX = (tempCoords.x);
        event.stageY = (tempCoords.y);
        target.fire(event);
        boolean handled = event.handled;
        Pools.free(event);
        return handled;
    }

    /**
     * Applies a key down event to the actor that has {@link Scene#setKeyboardFocus(Element) keyboard focus}, if any, and returns
     * true if the event was {@link Event#handle() handled}.
     */
    @Override
    public boolean keyDown(KeyCode keyCode){
        Element target = keyboardFocus == null ? root : keyboardFocus;
        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (InputEvent.Type.keyDown);
        event.keyCode = keyCode;
        target.fire(event);
        boolean handled = event.handled;
        Pools.free(event);
        return handled;
    }

    /**
     * Applies a key up event to the actor that has {@link Scene#setKeyboardFocus(Element) keyboard focus}, if any, and returns true
     * if the event was {@link Event#handle() handled}.
     */
    @Override
    public boolean keyUp(KeyCode keyCode){
        Element target = keyboardFocus == null ? root : keyboardFocus;
        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (InputEvent.Type.keyUp);
        event.keyCode = keyCode;
        target.fire(event);
        boolean handled = event.handled;
        Pools.free(event);
        return handled;
    }

    /**
     * Applies a key typed event to the actor that has {@link Scene#setKeyboardFocus(Element) keyboard focus}, if any, and returns
     * true if the event was {@link Event#handle() handled}.
     */
    @Override
    public boolean keyTyped(char character){
        Element target = keyboardFocus == null ? root : keyboardFocus;
        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (InputEvent.Type.keyTyped);
        event.character = character;
        target.fire(event);
        boolean handled = event.handled;
        Pools.free(event);
        return handled;
    }

    /** Adds the listener to be notified for all touchDragged and touchUp events for the specified pointer and button. */
    public void addTouchFocus(EventListener listener, Element listenerActor, Element target, int pointer, KeyCode button){
        TouchFocus focus = Pools.obtain(TouchFocus.class, TouchFocus::new);
        focus.listenerActor = listenerActor;
        focus.target = target;
        focus.listener = listener;
        focus.pointer = pointer;
        focus.button = button;
        touchFocuses.add(focus);
    }

    /**
     * Removes the listener from being notified for all touchDragged and touchUp events for the specified pointer and button. Note
     * the listener may never receive a touchUp event if this method is used.
     */
    public void removeTouchFocus(EventListener listener, Element listenerActor, Element target, int pointer, KeyCode button){
        SnapshotArray<TouchFocus> touchFocuses = this.touchFocuses;
        for(int i = touchFocuses.size - 1; i >= 0; i--){
            TouchFocus focus = touchFocuses.get(i);
            if(focus.listener == listener && focus.listenerActor == listenerActor && focus.target == target
            && focus.pointer == pointer && focus.button == button){
                touchFocuses.remove(i);
                Pools.free(focus);
            }
        }
    }

    /**
     * Cancels touch focus for the specified actor.
     * @see #cancelTouchFocus()
     */
    public void cancelTouchFocus(Element actor){
        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (InputEvent.Type.touchUp);
        event.stageX = (Integer.MIN_VALUE);
        event.stageY = (Integer.MIN_VALUE);

        // Cancel all current touch focuses for the specified listener, allowing for concurrent modification, and never cancel the
        // same focus twice.
        SnapshotArray<TouchFocus> touchFocuses = this.touchFocuses;
        TouchFocus[] items = touchFocuses.begin();
        for(int i = 0, n = touchFocuses.size; i < n; i++){
            TouchFocus focus = items[i];
            if(focus.listenerActor != actor) continue;
            if(!touchFocuses.removeValue(focus, true)) continue; // Touch focus already gone.
            event.targetActor = focus.target;
            event.listenerActor = focus.listenerActor;
            event.pointer = (focus.pointer);
            event.keyCode = (focus.button);
            focus.listener.handle(event);
            // Cannot return TouchFocus to pool, as it may still be in use (eg if cancelTouchFocus is called from touchDragged).
        }
        touchFocuses.end();

        Pools.free(event);
    }

    /**
     * Sends a touchUp event to all listeners that are registered to receive touchDragged and touchUp events and removes their
     * touch focus. This method removes all touch focus listeners, but sends a touchUp event so that the state of the listeners
     * remains consistent (listeners typically expect to receive touchUp eventually). The location of the touchUp is
     * Integer#MIN_VALUE. Listeners can use {@link InputEvent#isTouchFocusCancel()} to ignore this event if needed.
     */
    public void cancelTouchFocus(){
        cancelTouchFocusExcept(null, null);
    }

    /**
     * Cancels touch focus for all listeners except the specified listener.
     * @see #cancelTouchFocus()
     */
    public void cancelTouchFocusExcept(EventListener exceptListener, Element exceptActor){
        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);
        event.type = (InputEvent.Type.touchUp);
        event.stageX = (Integer.MIN_VALUE);
        event.stageY = (Integer.MIN_VALUE);

        // Cancel all current touch focuses except for the specified listener, allowing for concurrent modification, and never
        // cancel the same focus twice.
        SnapshotArray<TouchFocus> touchFocuses = this.touchFocuses;
        TouchFocus[] items = touchFocuses.begin();
        for(int i = 0, n = touchFocuses.size; i < n; i++){
            TouchFocus focus = items[i];
            if(focus.listener == exceptListener && focus.listenerActor == exceptActor) continue;
            if(!touchFocuses.removeValue(focus, true)) continue; // Touch focus already gone.
            event.targetActor = focus.target;
            event.listenerActor = focus.listenerActor;
            event.pointer = (focus.pointer);
            event.keyCode = (focus.button);
            focus.listener.handle(event);
            // Cannot return TouchFocus to pool, as it may still be in use (eg if cancelTouchFocus is called from touchDragged).
        }
        touchFocuses.end();

        Pools.free(event);
    }

    /**
     * Adds an actor to the root of the stage.
     * @see Group#addChild(Element)
     */
    public void add(Element actor){
        root.addChild(actor);
    }

    /**
     * Adds an action to the root of the stage.
     * @see Group#addAction(Action)
     */
    public void addAction(Action action){
        root.addAction(action);
    }

    /**
     * Returns the root's child actors.
     * @see Group#getChildren()
     */
    public Array<Element> getElements(){
        return root.children;
    }

    /**
     * Adds a listener to the root.
     * @see Element#addListener(EventListener)
     */
    public boolean addListener(EventListener listener){
        return root.addListener(listener);
    }

    /**
     * Removes a listener from the root.
     * @see Element#removeListener(EventListener)
     */
    public boolean removeListener(EventListener listener){
        return root.removeListener(listener);
    }

    /**
     * Adds a capture listener to the root.
     * @see Element#addCaptureListener(EventListener)
     */
    public boolean addCaptureListener(EventListener listener){
        return root.addCaptureListener(listener);
    }

    /**
     * Removes a listener from the root.
     * @see Element#removeCaptureListener(EventListener)
     */
    public boolean removeCaptureListener(EventListener listener){
        return root.removeCaptureListener(listener);
    }

    /** Removes the root's children, actions, and listeners. */
    public void clear(){
        unfocusAll();
        root.clear();
    }

    /** Removes the touch, keyboard, and scroll focused actors. */
    public void unfocusAll(){
        setScrollFocus(null);
        setKeyboardFocus(null);
        cancelTouchFocus();
    }

    /** Removes the touch, keyboard, and scroll focus for the specified actor and any descendants. */
    public void unfocus(Element actor){
        cancelTouchFocus(actor);
        if(scrollFocus != null && scrollFocus.isDescendantOf(actor)) setScrollFocus(null);
        if(keyboardFocus != null && keyboardFocus.isDescendantOf(actor)) setKeyboardFocus(null);
    }

    /**
     * Sets the actor that will receive key events.
     * @param actor May be null.
     * @return true if the unfocus and focus events were not cancelled by a {@link FocusListener}.
     */
    public boolean setKeyboardFocus(Element actor){
        if(keyboardFocus == actor) return true;
        FocusEvent event = Pools.obtain(FocusEvent.class, FocusEvent::new);
        event.type = (FocusEvent.Type.keyboard);
        Element oldKeyboardFocus = keyboardFocus;
        if(oldKeyboardFocus != null){
            event.focused = false;
            event.relatedActor = (actor);
            oldKeyboardFocus.fire(event);
        }
        boolean success = !event.cancelled;
        if(success){
            keyboardFocus = actor;
            if(actor != null){
                event.focused = true;
                event.relatedActor = (oldKeyboardFocus);
                actor.fire(event);
                success = !event.cancelled;
                if(!success) setKeyboardFocus(oldKeyboardFocus);
            }
        }
        Pools.free(event);
        return success;
    }

    /**
     * Gets the actor that will receive key events.
     * @return May be null.
     */
    public Element getKeyboardFocus(){
        return keyboardFocus;
    }

    /**
     * Sets the actor that will receive scroll events.
     * @param actor May be null.
     * @return true if the unfocus and focus events were not cancelled by a {@link FocusListener}.
     */
    public boolean setScrollFocus(Element actor){
        if(scrollFocus == actor) return true;
        FocusEvent event = Pools.obtain(FocusEvent.class, FocusEvent::new);
        event.type = (FocusEvent.Type.scroll);
        Element oldScrollFocus = scrollFocus;
        if(oldScrollFocus != null){
            event.focused = false;
            event.relatedActor = (actor);
            oldScrollFocus.fire(event);
        }
        boolean success = !event.cancelled;
        if(success){
            scrollFocus = actor;
            if(actor != null){
                event.focused = true;
                event.relatedActor = (oldScrollFocus);
                actor.fire(event);
                success = !event.cancelled;
                if(!success) setScrollFocus(oldScrollFocus);
            }
        }
        Pools.free(event);
        return success;
    }

    /**
     * Gets the actor that will receive scroll events.
     * @return May be null.
     */
    public Element getScrollFocus(){
        return scrollFocus;
    }

    public Viewport getViewport(){
        return viewport;
    }

    public void setViewport(Viewport viewport){
        this.viewport = viewport;
    }

    /** The viewport's world width. */
    public float getWidth(){
        return viewport.getWorldWidth();
    }

    /** The viewport's world height. */
    public float getHeight(){
        return viewport.getWorldHeight();
    }

    /** The viewport's camera. */
    public Camera getCamera(){
        return viewport.getCamera();
    }

    /**
     * Returns the {@link Element} at the specified location in stage coordinates. Hit testing is performed in the order the actors
     * were inserted into the stage, last inserted actors being tested first. To get stage coordinates from screen coordinates, use
     * {@link #screenToStageCoordinates(Vector2)}.
     * @param touchable If true, the hit detection will respect the {@link Element#touchable(Touchable) touchability}.
     * @return May be null if no actor was hit.
     */
    public Element hit(float stageX, float stageY, boolean touchable){
        root.parentToLocalCoordinates(tempCoords.set(stageX, stageY));
        return root.hit(tempCoords.x, tempCoords.y, touchable);
    }

    /**
     * Transforms the screen coordinates to stage coordinates.
     * @param screenCoords Input screen coordinates and output for resulting stage coordinates.
     */
    public Vector2 screenToStageCoordinates(Vector2 screenCoords){
        viewport.unproject(screenCoords);
        return screenCoords;
    }

    /**
     * Transforms the stage coordinates to screen coordinates.
     * @param stageCoords Input stage coordinates and output for resulting screen coordinates.
     */
    public Vector2 stageToScreenCoordinates(Vector2 stageCoords){
        viewport.project(stageCoords);
        stageCoords.y = viewport.getScreenHeight() - stageCoords.y;
        return stageCoords;
    }

    /**
     * Transforms the coordinates to screen coordinates. The coordinates can be anywhere in the stage since the transform matrix
     * describes how to convert them.
     * @see Element#localToStageCoordinates(Vector2)
     */
    public Vector2 toScreenCoordinates(Vector2 coords, Matrix3 transformMatrix){
        return viewport.toScreenCoordinates(coords, transformMatrix);
    }

    /** Calculates window scissor coordinates from local coordinates using the batch's current transformation matrix. */
    public void calculateScissors(Rectangle localRect, Rectangle scissorRect){
        Matrix3 transformMatrix = Draw.trans();
        viewport.calculateScissors(transformMatrix, localRect, scissorRect);
    }

    public boolean getActionsRequestRendering(){
        return actionsRequestRendering;
    }

    /**
     * If true, any actions executed during a call to {@link #act()}) will result in a call to {@link Graphics#requestRendering()}
     * . Widgets that animate or otherwise require additional rendering may check this setting before calling
     * {@link Graphics#requestRendering()}. Default is true.
     */
    public void setActionsRequestRendering(boolean actionsRequestRendering){
        this.actionsRequestRendering = actionsRequestRendering;
    }

    /** Check if screen coordinates are inside the viewport's screen area. */
    protected boolean isInsideViewport(int screenX, int screenY){
        int x0 = viewport.getScreenX();
        int x1 = x0 + viewport.getScreenWidth();
        int y0 = viewport.getScreenY();
        int y1 = y0 + viewport.getScreenHeight();
        screenY = graphics.getHeight() - screenY;
        return screenX >= x0 && screenX < x1 && screenY >= y0 && screenY < y1;
    }

    /** Updates the viewport. */
    public void resize(int width, int height){
        viewport.update(width, height, true);
    }

    @Override
    public void dispose(){
        //skin.dispose();
    }

    /**
     * Internal class for managing touch focus. Public only for GWT.
     * @author Nathan Sweet
     */
    public static final class TouchFocus implements Poolable{
        EventListener listener;
        Element listenerActor, target;
        int pointer;
        KeyCode button;

        public void reset(){
            listenerActor = null;
            listener = null;
            target = null;
        }
    }
}
