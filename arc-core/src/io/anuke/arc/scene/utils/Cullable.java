package io.anuke.arc.scene.utils;

import io.anuke.arc.math.geom.Rectangle;
import io.anuke.arc.scene.Group;

/**
 * Allows a parent to set the area that is visible on a child actor to allow the child to cull when drawing itself. This must only
 * be used for actors that are not rotated or scaled.
 * <p>
 * When Group is given a culling rectangle with {@link Group#setCullingArea(Rectangle)}, it will automatically call
 * {@link #setCullingArea(Rectangle)} on its children.
 * @author Nathan Sweet
 */
public interface Cullable{
    /** @param cullingArea The culling area in the child actor's coordinates. */
    void setCullingArea(Rectangle cullingArea);
}
