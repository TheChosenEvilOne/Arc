package io.anuke.arc.scene;

import io.anuke.arc.Core;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.DelayedRemovalArray;
import io.anuke.arc.function.BooleanProvider;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.function.PositionConsumer;
import io.anuke.arc.function.Supplier;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Rectangle;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.scene.actions.Actions;
import io.anuke.arc.scene.event.*;
import io.anuke.arc.scene.event.InputEvent.Type;
import io.anuke.arc.scene.utils.Disableable;
import io.anuke.arc.scene.utils.Layout;
import io.anuke.arc.graphics.g2d.ScissorStack;
import io.anuke.arc.util.Align;
import io.anuke.arc.util.pooling.Pools;

import static io.anuke.arc.util.Align.*;

public class Element implements Layout{
    public final Color color = new Color(1, 1, 1, 1);

    private final DelayedRemovalArray<EventListener> listeners = new DelayedRemovalArray<>(0);
    private final DelayedRemovalArray<EventListener> captureListeners = new DelayedRemovalArray<>(0);
    private final Array<Action> actions = new Array<>(0);

    /** DO NOT modify without calling positionChanged. */
    protected float x, y;
    /** DO NOT modify without calling sizeChanged. */
    protected float width, height;
    /** Alpha value of the parent. Should be multiplied with the actor's alpha, allowing a parent's alpha to affect all children. */
    protected float parentAlpha = 1f;
    protected Vector2 translation = new Vector2(0, 0);
    Group parent;
    float originX, originY;
    float scaleX = 1, scaleY = 1;
    float rotation;
    private Scene stage;
    private String name;
    private Touchable touchable = Touchable.enabled;
    private boolean visible = true;
    private Object userObject;
    private boolean needsLayout = true;
    protected boolean fillParent;
    private boolean layoutEnabled = true;
    private BooleanProvider visibility;
    private Runnable update;
    private Supplier<Touchable> touchableSupplier = null;

    /** Draws the element. Does nothing by default. */
    public void draw(){
        validate();
    }

    /**
     * Updates the actor based on time. Typically this is called each frame by {@link Scene#act(float)}.
     * <p>
     * The default implementation calls {@link Action#act(float)} on each action and removes actions that are complete.
     * @param delta Time in seconds since the last frame.
     */
    public void act(float delta){
        Array<Action> actions = this.actions;
        if(actions.size > 0){
            if(stage != null && stage.getActionsRequestRendering()) Core.graphics.requestRendering();
            for(int i = 0; i < actions.size; i++){
                Action action = actions.get(i);
                if(action.act(delta) && i < actions.size){
                    Action current = actions.get(i);
                    int actionIndex = current == action ? i : actions.indexOf(action, true);
                    if(actionIndex != -1){
                        actions.remove(actionIndex);
                        action.setActor(null);
                        i--;
                    }
                }
            }
        }

        if(touchableSupplier != null)
            touchable(touchableSupplier.get());
        if(update != null)
            update.run();
    }

    public void updateVisibility(){
        if(visibility != null)
            visible(visibility.get());
    }

    public boolean hasMouse(){
        Element e = Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true);
        return e == this || (e != null && e.isDescendantOf(this));
    }

    @SuppressWarnings("unchecked")
    public boolean fire(Event event){
        event.targetActor = this;

        // Collect ancestors so event propagation is unaffected by hierarchy changes.
        Array<Group> ancestors = Pools.obtain(Array.class, Array::new);
        Group parent = this.parent;
        while(parent != null){
            ancestors.add(parent);
            parent = parent.parent;
        }

        try{
            // Notify all parent capture listeners, starting at the root. Ancestors may stop an event before children receive it.
            Object[] ancestorsArray = ancestors.items;
            for(int i = ancestors.size - 1; i >= 0; i--){
                Group currentTarget = (Group)ancestorsArray[i];
                currentTarget.notify(event, true);
                if(event.stopped) return event.cancelled;
            }

            // Notify the target capture listeners.
            notify(event, true);
            if(event.stopped) return event.cancelled;

            // Notify the target listeners.
            notify(event, false);
            if(!event.bubbles) return event.cancelled;
            if(event.stopped) return event.cancelled;

            // Notify all parent listeners, starting at the target. Children may stop an event before ancestors receive it.
            for(int i = 0, n = ancestors.size; i < n; i++){
                ((Group)ancestorsArray[i]).notify(event, false);
                if(event.stopped) return event.cancelled;
            }

            return event.cancelled;
        }finally{
            ancestors.clear();
            Pools.free(ancestors);
        }
    }

    public boolean notify(Event event, boolean capture){
        if(event.targetActor == null) throw new IllegalArgumentException("The event target cannot be null.");

        DelayedRemovalArray<EventListener> listeners = capture ? captureListeners : this.listeners;
        if(listeners.size == 0) return event.cancelled;

        event.listenerActor = this;
        event.capture = capture;

        listeners.begin();
        for(int i = 0, n = listeners.size; i < n; i++){
            EventListener listener = listeners.get(i);
            if(listener.handle(event)){
                event.handle();
                if(event instanceof InputEvent){
                    InputEvent inputEvent = (InputEvent)event;
                    if(inputEvent.type == Type.touchDown){
                        getScene().addTouchFocus(listener, this, inputEvent.targetActor, inputEvent.pointer,
                        inputEvent.keyCode);
                    }
                }
            }
        }
        listeners.end();

        return event.cancelled;
    }

    /**
     * Returns the deepest actor that contains the specified point and is {@link #getTouchable() touchable} and
     * {@link #isVisible() visible}, or null if no actor was hit. The point is specified in the actor's local coordinate system
     * (0,0 is the bottom left of the actor and width,height is the upper right).
     * <p>
     * This method is used to delegate touchDown, mouse, and enter/exit events. If this method returns null, those events will not
     * occur on this Actor.
     * <p>
     * The default implementation returns this actor if the point is within this actor's bounds.
     * @param touchable If true, the hit detection will respect the {@link #touchable(Touchable) touchability}.
     * @see Touchable
     */
    public Element hit(float x, float y, boolean touchable){
        if(touchable && this.touchable != Touchable.enabled) return null;
        Element e = this;
        return x >= e.translation.x && x < width + e.translation.x && y >= e.translation.y && y < height + e.translation.y ? this : null;
    }

    /**
     * Removes this actor from its parent, if it has a parent.
     * @see Group#removeChild(Element)
     */
    public boolean remove(){
        return parent != null && parent.removeChild(this, true);
    }


    /**Adds a listener which listens for drag (touch down and move) events.
     * Results are returned in positive deltas.*/
    public void dragged(PositionConsumer cons){
        addListener(new InputListener(){
            float lastX, lastY;
            @Override
            public void touchDragged(InputEvent event, float mx, float my, int pointer){
                cons.accept(mx - lastX, my - lastY);
                lastX = mx;
                lastY = my;
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                lastX = x;
                lastY = y;
                return true;
            }
        });
    }

    /**
     * Add a listener to receive events that {@link #hit(float, float, boolean) hit} this actor. See {@link #fire(Event)}.
     * @see InputListener
     * @see ClickListener
     */
    public boolean addListener(EventListener listener){
        if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
        if(!listeners.contains(listener, true)){
            listeners.add(listener);
            return true;
        }
        return false;
    }

    public boolean removeListener(EventListener listener){
        if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
        return listeners.removeValue(listener, true);
    }

    public Array<EventListener> getListeners(){
        return listeners;
    }

    /**
     * Adds a listener that is only notified during the capture phase.
     * @see #fire(Event)
     */
    public boolean addCaptureListener(EventListener listener){
        if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
        if(!captureListeners.contains(listener, true)) captureListeners.add(listener);
        return true;
    }

    public boolean removeCaptureListener(EventListener listener){
        if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
        return captureListeners.removeValue(listener, true);
    }

    public Array<EventListener> getCaptureListeners(){
        return captureListeners;
    }

    public void addAction(Action action){
        action.setActor(this);
        actions.add(action);

        if(stage != null && stage.getActionsRequestRendering()) Core.graphics.requestRendering();
    }

    public void actions(Action... actions){
        addAction(Actions.sequence(actions));
    }

    public void removeAction(Action action){
        if(actions.removeValue(action, true)) action.setActor(null);
    }

    public Array<Action> getActions(){
        return actions;
    }

    /** Returns true if the actor has one or more actions. */
    public boolean hasActions(){
        return actions.size > 0;
    }

    /** Removes all actions on this actor. */
    public void clearActions(){
        for(int i = actions.size - 1; i >= 0; i--)
            actions.get(i).setActor(null);
        actions.clear();
    }

    /** Removes all listeners on this actor. */
    public void clearListeners(){
        listeners.clear();
        captureListeners.clear();
    }

    /** Removes all actions and listeners on this actor. */
    public void clear(){
        clearActions();
        clearListeners();
    }

    /** Returns the stage that this actor is currently in, or null if not in a stage. */
    public Scene getScene(){
        return stage;
    }

    /**
     * Called by the framework when this actor or any parent is added to a group that is in the stage.
     * @param stage May be null if the actor or any parent is no longer in a stage.
     */
    protected void setScene(Scene stage){
        this.stage = stage;
    }

    /** Returns true if this actor is the same as or is the descendant of the specified actor. */
    public boolean isDescendantOf(Element actor){
        if(actor == null) throw new IllegalArgumentException("actor cannot be null.");
        Element parent = this;
        while(true){
            if(parent == null) return false;
            if(parent == actor) return true;
            parent = parent.parent;
        }
    }

    /** Returns true if this actor is the same as or is the ascendant of the specified actor. */
    public boolean isAscendantOf(Element actor){
        if(actor == null) throw new IllegalArgumentException("actor cannot be null.");
        while(true){
            if(actor == null) return false;
            if(actor == this) return true;
            actor = actor.parent;
        }
    }

    /** Returns true if the actor's parent is not null. */
    public boolean hasParent(){
        return parent != null;
    }

    /** Returns the parent actor, or null if not in a group. */
    public Group getParent(){
        return parent;
    }

    /**
     * Called by the framework when an actor is added to or removed from a group.
     * @param parent May be null if the actor has been removed from the parent.
     */
    protected void setParent(Group parent){
        this.parent = parent;
    }

    /** Returns true if input events are processed by this actor. */
    public boolean isTouchable(){
        return touchable == Touchable.enabled;
    }

    public Touchable getTouchable(){
        return touchable;
    }

    /** Determines how touch events are distributed to this actor. Default is {@link Touchable#enabled}. */
    public void touchable(Touchable touchable){
        this.touchable = touchable;
    }

    public boolean isVisible(){
        return visible;
    }

    /** If false, the actor will not be drawn and will not receive touch events. Default is true. */
    public void visible(boolean visible){
        this.visible = visible;
    }

    /** Returns an application specific object for convenience, or null. */
    public Object getUserObject(){
        return userObject;
    }

    /** Sets an application specific object for convenience. */
    public void setUserObject(Object userObject){
        this.userObject = userObject;
    }

    /** Returns the X position of the actor's left edge. */
    public float getX(){
        return x;
    }

    public void setX(float x){
        if(this.x != x){
            this.x = x;
            positionChanged();
        }
    }

    /** Returns the X position of the specified {@link Align alignment}. */
    public float getX(int alignment){
        float x = this.x;
        if((alignment & right) != 0)
            x += width;
        else if((alignment & left) == 0) //
            x += width / 2;
        return x;
    }

    /** Returns the Y position of the actor's bottom edge. */
    public float getY(){
        return y;
    }

    public void setY(float y){
        if(this.y != y){
            this.y = y;
            positionChanged();
        }
    }

    /** Returns the Y position of the specified {@link Align alignment}. */
    public float getY(int alignment){
        float y = this.y;
        if((alignment & top) != 0)
            y += height;
        else if((alignment & bottom) == 0) //
            y += height / 2;
        return y;
    }

    /** Sets the position of the actor's bottom left corner. */
    public void setPosition(float x, float y){
        if(this.x != x || this.y != y){
            this.x = x;
            this.y = y;
            positionChanged();
        }
    }

    /**
     * Sets the position using the specified {@link Align alignment}. Note this may set the position to non-integer
     * coordinates.
     */
    public void setPosition(float x, float y, int alignment){
        if((alignment & right) != 0)
            x -= width;
        else if((alignment & left) == 0) //
            x -= width / 2;

        if((alignment & top) != 0)
            y -= height;
        else if((alignment & bottom) == 0) //
            y -= height / 2;

        if(this.x != x || this.y != y){
            this.x = x;
            this.y = y;
            positionChanged();
        }
    }

    /** Add x and y to current position */
    public void moveBy(float x, float y){
        if(x != 0 || y != 0){
            this.x += x;
            this.y += y;
            positionChanged();
        }
    }

    public float getWidth(){
        return width;
    }

    public void setWidth(float width){
        if(this.width != width){
            this.width = width;
            sizeChanged();
        }
    }

    public float getHeight(){
        return height;
    }

    public void setHeight(float height){
        if(this.height != height){
            this.height = height;
            sizeChanged();
        }
    }

    /** Returns y plus height. */
    public float getTop(){
        return y + height;
    }

    /** Returns x plus width. */
    public float getRight(){
        return x + width;
    }

    /** Called when the actor's position has been changed. */
    protected void positionChanged(){
    }

    /** Called when the actor's size has been changed. */
    protected void sizeChanged(){
        invalidate();
    }

    /** Called when the actor's rotation has been changed. */
    protected void rotationChanged(){
    }

    public void setSize(float size){
        setSize(size, size);
    }

    /** Sets the width and height. */
    public void setSize(float width, float height){
        if(this.width != width || this.height != height){
            this.width = width;
            this.height = height;
            sizeChanged();
        }
    }

    /** Adds the specified size to the current size. */
    public void sizeBy(float size){
        if(size != 0){
            width += size;
            height += size;
            sizeChanged();
        }
    }

    /** Adds the specified size to the current size. */
    public void sizeBy(float width, float height){
        if(width != 0 || height != 0){
            this.width += width;
            this.height += height;
            sizeChanged();
        }
    }

    /** Set bounds the x, y, width, and height. */
    public void setBounds(float x, float y, float width, float height){
        if(this.x != x || this.y != y){
            this.x = x;
            this.y = y;
            positionChanged();
        }
        if(this.width != width || this.height != height){
            this.width = width;
            this.height = height;
            sizeChanged();
        }
    }

    public float getOriginX(){
        return originX;
    }

    public void setOriginX(float originX){
        this.originX = originX;
    }

    public float getOriginY(){
        return originY;
    }

    public void setOriginY(float originY){
        this.originY = originY;
    }

    /** Sets the origin position which is relative to the actor's bottom left corner. */
    public void setOrigin(float originX, float originY){
        this.originX = originX;
        this.originY = originY;
    }

    /** Sets the origin position to the specified {@link Align alignment}. */
    public void setOrigin(int alignment){
        if((alignment & left) != 0)
            originX = 0;
        else if((alignment & right) != 0)
            originX = width;
        else
            originX = width / 2;

        if((alignment & bottom) != 0)
            originY = 0;
        else if((alignment & top) != 0)
            originY = height;
        else
            originY = height / 2;
    }

    public float getScaleX(){
        return scaleX;
    }

    public void setScaleX(float scaleX){
        this.scaleX = scaleX;
    }

    public float getScaleY(){
        return scaleY;
    }

    public void setScaleY(float scaleY){
        this.scaleY = scaleY;
    }

    /** Sets the scale for both X and Y */
    public void setScale(float scaleXY){
        this.scaleX = scaleXY;
        this.scaleY = scaleXY;
    }

    /** Sets the scale X and scale Y. */
    public void setScale(float scaleX, float scaleY){
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    /** Adds the specified scale to the current scale. */
    public void scaleBy(float scale){
        scaleX += scale;
        scaleY += scale;
    }

    /** Adds the specified scale to the current scale. */
    public void scaleBy(float scaleX, float scaleY){
        this.scaleX += scaleX;
        this.scaleY += scaleY;
    }

    public float getRotation(){
        return rotation;
    }

    public void setRotation(float degrees){
        if(this.rotation != degrees){
            this.rotation = degrees;
            rotationChanged();
        }
    }

    public void setRotationOrigin(float degrees, int align){
        setOrigin(align);
        if(this.rotation != degrees){
            this.rotation = degrees;
            rotationChanged();
        }
    }

    /** Adds the specified rotation to the current rotation. */
    public void rotateBy(float amountInDegrees){
        if(amountInDegrees != 0){
            rotation += amountInDegrees;
            rotationChanged();
        }
    }

    public void setColor(float r, float g, float b, float a){
        color.set(r, g, b, a);
    }

    /** Returns the color the actor will be tinted when drawn. The returned instance can be modified to change the color. */
    public Color getColor(){
        return color;
    }

    public void setColor(Color color){
        this.color.set(color);
    }

    /**
     * @return May be null.
     */
    public String getName(){
        return name;
    }

    /**
     * Set the actor's name, which is used for identification convenience and by {@link #toString()}.
     * @param name May be null.
     */
    public void setName(String name){
        this.name = name;
    }

    /** Changes the z-order for this actor so it is in front of all siblings. */
    public void toFront(){
        setZIndex(Integer.MAX_VALUE);
    }

    /** Changes the z-order for this actor so it is in back of all siblings. */
    public void toBack(){
        setZIndex(0);
    }

    /**
     * Returns the z-index of this actor.
     * @see #setZIndex(int)
     */
    public int getZIndex(){
        Group parent = this.parent;
        if(parent == null) return -1;
        return parent.children.indexOf(this, true);
    }

    /**
     * Sets the z-index of this actor. The z-index is the index into the parent's {@link Group#getChildren() children}, where a
     * lower index is below a higher index. Setting a z-index higher than the number of children will move the child to the front.
     * Setting a z-index less than zero is invalid.
     */
    public void setZIndex(int index){
        if(index < 0) throw new IllegalArgumentException("ZIndex cannot be < 0.");
        Group parent = this.parent;
        if(parent == null) return;
        Array<Element> children = parent.children;
        if(children.size == 1) return;
        index = Math.min(index, children.size - 1);
        if(children.get(index) == this) return;
        if(!children.removeValue(this, true)) return;
        children.insert(index, this);
    }

    /** Calls {@link #clipBegin(float, float, float, float)} to clip this actor's bounds. */
    public boolean clipBegin(){
        return clipBegin(x, y, width, height);
    }

    /**
     * Clips the specified screen aligned rectangle, specified relative to the transform matrix of the stage's Batch. The
     * transform matrix and the stage's camera must not have rotational components. Calling this method must be followed by a call
     * to {@link #clipEnd()} if true is returned.
     * @return false if the clipping area is zero and no drawing should occur.
     * @see ScissorStack
     */
    public boolean clipBegin(float x, float y, float width, float height){
        if(width <= 0 || height <= 0) return false;
        Rectangle tableBounds = Rectangle.tmp;
        tableBounds.x = x;
        tableBounds.y = y;
        tableBounds.width = width;
        tableBounds.height = height;
        Scene stage = this.stage;
        Rectangle scissorBounds = Pools.obtain(Rectangle.class, Rectangle::new);
        stage.calculateScissors(tableBounds, scissorBounds);
        if(ScissorStack.pushScissors(scissorBounds)) return true;
        Pools.free(scissorBounds);
        return false;
    }

    /** Ends clipping begun by {@link #clipBegin(float, float, float, float)}. */
    public void clipEnd(){
        Pools.free(ScissorStack.popScissors());
    }

    /** Transforms the specified point in screen coordinates to the actor's local coordinate system. */
    public Vector2 screenToLocalCoordinates(Vector2 screenCoords){
        Scene stage = this.stage;
        if(stage == null) return screenCoords;
        return stageToLocalCoordinates(stage.screenToStageCoordinates(screenCoords));
    }

    /** Transforms the specified point in the stage's coordinates to the actor's local coordinate system. */
    public Vector2 stageToLocalCoordinates(Vector2 stageCoords){
        if(parent != null) parent.stageToLocalCoordinates(stageCoords);
        parentToLocalCoordinates(stageCoords);
        return stageCoords;
    }

    /**
     * Transforms the specified point in the actor's coordinates to be in the stage's coordinates.
     */
    public Vector2 localToStageCoordinates(Vector2 localCoords){
        return localToAscendantCoordinates(null, localCoords);
    }

    /** Transforms the specified point in the actor's coordinates to be in the parent's coordinates. */
    public Vector2 localToParentCoordinates(Vector2 localCoords){
        final float rotation = -this.rotation;
        final float scaleX = this.scaleX;
        final float scaleY = this.scaleY;
        final float x = this.x;
        final float y = this.y;
        if(rotation == 0){
            if(scaleX == 1 && scaleY == 1){
                localCoords.x += x;
                localCoords.y += y;
            }else{
                final float originX = this.originX;
                final float originY = this.originY;
                localCoords.x = (localCoords.x - originX) * scaleX + originX + x;
                localCoords.y = (localCoords.y - originY) * scaleY + originY + y;
            }
        }else{
            final float cos = (float)Math.cos(rotation * Mathf.degreesToRadians);
            final float sin = (float)Math.sin(rotation * Mathf.degreesToRadians);
            final float originX = this.originX;
            final float originY = this.originY;
            final float tox = (localCoords.x - originX) * scaleX;
            final float toy = (localCoords.y - originY) * scaleY;
            localCoords.x = (tox * cos + toy * sin) + originX + x;
            localCoords.y = (tox * -sin + toy * cos) + originY + y;
        }
        return localCoords;
    }

    /** Converts coordinates for this actor to those of a parent actor. The ascendant does not need to be a direct parent. */
    public Vector2 localToAscendantCoordinates(Element ascendant, Vector2 localCoords){
        Element actor = this;
        while(actor != null){
            actor.localToParentCoordinates(localCoords);
            actor = actor.parent;
            if(actor == ascendant) break;
        }
        return localCoords;
    }

    /** Converts the coordinates given in the parent's coordinate system to this actor's coordinate system. */
    public Vector2 parentToLocalCoordinates(Vector2 parentCoords){
        final float rotation = this.rotation;
        final float scaleX = this.scaleX;
        final float scaleY = this.scaleY;
        final float childX = x + this.translation.x;
        final float childY = y + this.translation.y;
        if(rotation == 0){
            if(scaleX == 1 && scaleY == 1){
                parentCoords.x -= childX;
                parentCoords.y -= childY;
            }else{
                final float originX = this.originX;
                final float originY = this.originY;
                parentCoords.x = (parentCoords.x - childX - originX) / scaleX + originX;
                parentCoords.y = (parentCoords.y - childY - originY) / scaleY + originY;
            }
        }else{
            final float cos = (float)Math.cos(rotation * Mathf.degreesToRadians);
            final float sin = (float)Math.sin(rotation * Mathf.degreesToRadians);
            final float originX = this.originX;
            final float originY = this.originY;
            final float tox = parentCoords.x - childX - originX;
            final float toy = parentCoords.y - childY - originY;
            parentCoords.x = (tox * cos + toy * sin) / scaleX + originX;
            parentCoords.y = (tox * -sin + toy * cos) / scaleY + originY;
        }
        return parentCoords;
    }

    public float getMinWidth(){
        return getPrefWidth();
    }

    public float getMinHeight(){
        return getPrefHeight();
    }

    public float getPrefWidth(){
        return 0;
    }

    public float getPrefHeight(){
        return 0;
    }

    public float getMaxWidth(){
        return 0;
    }

    public float getMaxHeight(){
        return 0;
    }

    public void setLayoutEnabled(boolean enabled){
        layoutEnabled = enabled;
        if(enabled) invalidateHierarchy();
    }

    @Override
    public void validate(){
        if(!layoutEnabled) return;

        Group parent = getParent();
        if(fillParent && parent != null){
            float parentWidth, parentHeight;
            Scene stage = getScene();
            if(stage != null && parent == stage.root){
                parentWidth = stage.getWidth();
                parentHeight = stage.getHeight();
            }else{
                parentWidth = parent.getWidth();
                parentHeight = parent.getHeight();
            }
            setSize(parentWidth, parentHeight);
        }

        if(!needsLayout) return;
        needsLayout = false;
        layout();
    }

    /** Returns true if the widget's layout has been {@link #invalidate() invalidated}. */
    public boolean needsLayout(){
        return needsLayout;
    }

    public void invalidate(){
        needsLayout = true;
    }

    public void invalidateHierarchy(){
        if(!layoutEnabled) return;
        invalidate();
        Group parent = getParent();
        if(parent != null) parent.invalidateHierarchy();
    }


    public void pack(){
        setSize(getPrefWidth(), getPrefHeight());
        validate();
    }

    public void setFillParent(boolean fillParent){
        this.fillParent = fillParent;
    }

    public void layout(){}

    public void setTranslation(float x, float y){
        translation.x = x;
        translation.y = y;
    }

    public Vector2 getTranslation(){
        return translation;
    }

    public void keyDown(KeyCode key, Runnable l){
        keyDown(k -> {
            if(k == key)
                l.run();
        });
    }

    /** Adds a keydown input listener. */
    public void keyDown(Consumer<KeyCode> cons){
        addListener(new InputListener(){
            @Override
            public boolean keyDown(InputEvent event, KeyCode keycode){
                cons.accept(keycode);
                return true;
            }
        });
    }

    /** Fakes a click event on all ClickListeners. */
    public void fireClick(){
        for(EventListener listener : getListeners()){
            if(listener instanceof ClickListener){
                ((ClickListener)listener).clicked(new InputEvent(), -1, -1);
            }
        }
    }

    /** Adds a click listener. */
    public ClickListener clicked(Runnable r){
        return clicked(KeyCode.MOUSE_LEFT, r);
    }

    /** Adds a click listener. */
    public ClickListener clicked(KeyCode button, Runnable r){
        return clicked(l -> l.setButton(button), r);
    }

    /** Adds a click listener. */
    public ClickListener clicked(Consumer<ClickListener> tweaker, Runnable r){
        return clicked(tweaker, e -> r.run());
    }

    public ClickListener clicked(Consumer<ClickListener> tweaker, Consumer<ClickListener> runner){
        ClickListener click;
        Element elem = this;
        addListener(click = new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                if(runner != null && !(elem instanceof Disableable && ((Disableable)elem).isDisabled())) runner.accept(this);
            }
        });
        tweaker.accept(click);
        return click;
    }

    /** Adds a touch listener. */
    public void tapped(Runnable r){
        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                r.run();
                return true;
            }
        });
    }

    /** Adds a hover/mouse enter listener. */
    public void hovered(Runnable r){
        addListener(new InputListener(){
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                r.run();
            }
        });
    }

    /** Adds a hover/mouse exit listener. */
    public void exited(Runnable r){
        addListener(new InputListener(){
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element fromActor){
                r.run();
            }
        });
    }

    /** Adds a mouse up listener. */
    public void released(Runnable r){
        addListener(new InputListener(){
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                return true;
            }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                r.run();
            }
        });
    }

    /** Fires a change event on all listeners. */
    public void change(){
        fire(new ChangeListener.ChangeEvent());
    }

    /** Adds a click listener. */
    public void changed(Runnable r){
        Element elem = this;
        addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Element actor){
                if(!(elem instanceof Disableable && ((Disableable)elem).isDisabled())) r.run();
            }
        });
    }

    public void update(Runnable r){
        update = r;
    }

    public Element visible(BooleanProvider vis){
        visibility = vis;
        return this;
    }

    public void touchable(Supplier<Touchable> touch){
        this.touchableSupplier = touch;
    }

    @Override
    public String toString(){
        String name = this.name;
        if(name == null){
            name = super.toString().split("@")[0];
            int dotIndex = name.lastIndexOf('.');
            if(dotIndex != -1) name = name.substring(dotIndex + 1);
        }
        return name;
    }
}
