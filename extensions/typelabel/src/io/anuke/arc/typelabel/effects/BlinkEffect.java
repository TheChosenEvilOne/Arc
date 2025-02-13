package io.anuke.arc.typelabel.effects;

import io.anuke.arc.graphics.Color;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.typelabel.*;

/** Blinks the entire text in two different colors at once, without interpolation. */
public class BlinkEffect extends Effect{
    private static final float DEFAULT_FREQUENCY = 1f;

    private Color color1 = null; // First color of the effect.
    private Color color2 = null; // Second color of the effect.
    private float frequency = 1; // How frequently the color pattern should move through the text.
    private float threshold = 0.5f; // Point to switch colors.

    public BlinkEffect(TypeLabel label){
        super(label);

        // Validate parameters
        if(this.color1 == null) this.color1 = new Color(Color.WHITE);
        if(this.color2 == null) this.color2 = new Color(Color.WHITE);
        this.threshold = Mathf.clamp(this.threshold, 0, 1);
    }

    @Override
    protected void onApply(TypingGlyph glyph, int localIndex, float delta){
        // Calculate progress
        float frequencyMod = (1f / frequency) * DEFAULT_FREQUENCY;
        float progress = calculateProgress(frequencyMod);

        // Calculate color
        if(glyph.color == null) glyph.color = new Color(Color.WHITE);
        glyph.color.set(progress <= threshold ? color1 : color2);
    }

}
