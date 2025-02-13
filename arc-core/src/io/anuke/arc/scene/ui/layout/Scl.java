package io.anuke.arc.scene.ui.layout;

import io.anuke.arc.Application.ApplicationType;
import io.anuke.arc.Core;

/** Utilities for UI scale.*/
public class Scl{
    private static float scl = -1;
    private static float addition = 0f;
    private static float product = 1f;

    public static void setProduct(float product){
        Scl.product = product;
        scl = -1;
    }

    public static void setAddition(float addition){
        Scl.addition = addition;
        scl = -1;
    }

    public static float scl(){
        return scl(1f);
    }

    public static float scl(float amount){
        if(scl < 0f){
            //calculate scaling value if it hasn't been set yet
            if(Core.app.getType() == ApplicationType.Desktop){
                scl = 1f * product;
            }else if(Core.app.getType() == ApplicationType.WebGL){
                scl = 1f * product;
            }else{
                //mobile scaling
                scl = Math.max(Math.round((Core.graphics.getDensity() / 1.5f + addition) / 0.5) * 0.5f, 1f) * product;
            }
        }
        return amount * scl;
    }
}
