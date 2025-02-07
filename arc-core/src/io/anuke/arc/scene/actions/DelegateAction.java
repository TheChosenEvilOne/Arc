package io.anuke.arc.scene.actions;

import io.anuke.arc.scene.Action;
import io.anuke.arc.scene.Element;
import io.anuke.arc.util.pooling.Pool;

/**
 * Base class for an action that wraps another action.
 * @author Nathan Sweet
 */
abstract public class DelegateAction extends Action{
    protected Action action;

    public Action getAction(){
        return action;
    }

    /** Sets the wrapped action. */
    public void setAction(Action action){
        this.action = action;
    }

    abstract protected boolean delegate(float delta);

    public final boolean act(float delta){
        Pool pool = getPool();
        setPool(null); // Ensure this action can't be returned to the pool inside the delegate action.
        try{
            return delegate(delta);
        }finally{
            setPool(pool);
        }
    }

    public void restart(){
        if(action != null) action.restart();
    }

    public void reset(){
        super.reset();
        action = null;
    }

    public void setActor(Element actor){
        if(action != null) action.setActor(actor);
        super.setActor(actor);
    }

    public void setTarget(Element target){
        if(action != null) action.setTarget(target);
        super.setTarget(target);
    }

    public String toString(){
        return super.toString() + (action == null ? "" : "(" + action + ")");
    }
}
