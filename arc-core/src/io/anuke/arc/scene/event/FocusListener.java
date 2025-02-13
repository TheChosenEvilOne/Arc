package io.anuke.arc.scene.event;

import io.anuke.arc.scene.Element;

/**
 * Listener for {@link FocusEvent}.
 * @author Nathan Sweet
 */
abstract public class FocusListener implements EventListener{
    public boolean handle(Event event){
        if(!(event instanceof FocusEvent)) return false;
        FocusEvent focusEvent = (FocusEvent)event;
        switch(focusEvent.type){
            case keyboard:
                keyboardFocusChanged(focusEvent, event.targetActor, focusEvent.focused);
                break;
            case scroll:
                scrollFocusChanged(focusEvent, event.targetActor, focusEvent.focused);
                break;
        }
        return false;
    }

    /** @param element The event target, which is the element that emitted the focus event. */
    public void keyboardFocusChanged(FocusEvent event, Element element, boolean focused){
    }

    /** @param element The event target, which is the element that emitted the focus event. */
    public void scrollFocusChanged(FocusEvent event, Element element, boolean focused){
    }

    /**
     * Fired when an element gains or loses keyboard or scroll focus. Can be cancelled to prevent losing or gaining focus.
     * @author Nathan Sweet
     */
    public static class FocusEvent extends Event{
        public boolean focused;
        public Type type;
        public Element relatedActor;

        public void reset(){
            super.reset();
            relatedActor = null;
        }

        /** @author Nathan Sweet */
        public enum Type{
            keyboard, scroll
        }
    }
}
