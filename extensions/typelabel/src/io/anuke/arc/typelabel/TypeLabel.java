package io.anuke.arc.typelabel;

import io.anuke.arc.collection.*;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.graphics.g2d.BitmapFont.Glyph;
import io.anuke.arc.graphics.g2d.GlyphLayout.GlyphRun;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.scene.style.Drawable;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.util.Align;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.pooling.Pools;

/**
 * An extension of {@link Label} that progressively shows the text as if it was being typed in real time, and allows the
 * use of tokens in the following format: <tt>{TOKEN=PARAMETER}</tt>.
 * Code taken and ported from https://github.com/rafaskb/typing-label
 */
public class TypeLabel extends Label{

    // Collections
    private final ObjectMap<String, String> variables = new ObjectMap<>();
    protected final Array<TokenEntry> tokenEntries = new Array<>();

    // Config
    private Color clearColor = new Color(TypingConfig.DEFAULT_CLEAR_COLOR);
    private TypingListener listener = null;
    boolean forceMarkupColor = TypingConfig.FORCE_COLOR_MARKUP_BY_DEFAULT;

    // Internal state
    private final StringBuilder originalText = new StringBuilder();
    private final Array<TypingGlyph> glyphCache = new Array<>();
    private final IntArray glyphRunCapacities = new IntArray();
    private final IntArray offsetCache = new IntArray();
    private final IntArray layoutLineBreaks = new IntArray();
    private final Array<Effect> activeEffects = new Array<>();
    private float textSpeed = TypingConfig.DEFAULT_SPEED_PER_CHAR;
    private float charCooldown = textSpeed;
    private int rawCharIndex = -2; // All chars, including color codes
    private int glyphCharIndex = -1; // Only renderable chars, excludes color codes
    private int glyphCharCompensation = 0;
    private int cachedGlyphCharIndex = -1; // Last glyphCharIndex sent to the cache
    private float lastLayoutX = 0;
    private float lastLayoutY = 0;
    private boolean parsed = false;
    private boolean paused = false;
    private boolean ended = false;
    private boolean skipping = false;
    private boolean ignoringEvents = false;
    private boolean ignoringEffects = false;
    private String defaultToken = "";

    public TypeLabel(CharSequence text){
        super(text);
        saveOriginalText();
    }

    /**
     * Modifies the text of this label. If the char progression is already running, it's highly recommended to use
     * {@link #restart(CharSequence)} instead.
     */
    @Override
    public void setText(CharSequence newText){
        this.setText(newText, true);
    }

    /**
     * Sets the text of this label.
     *
     * @param modifyOriginalText Flag determining if the original text should be modified as well. If {@code false},
     * only the display text is changed while the original text is untouched.
     * @see #restart(CharSequence)
     */
    protected void setText(CharSequence newText, boolean modifyOriginalText){
        super.setText(newText);
        if(modifyOriginalText && originalText != null) saveOriginalText();
    }

    /** Similar to {@link #getText()}, but returns the original text with all the tokens unchanged. */
    public StringBuilder getOriginalText(){
        return originalText;
    }

    /**
     * Copies the content of {@link #getText()} to the {@link StringBuilder} containing the original text with all
     * tokens unchanged.
     */
    protected void saveOriginalText(){
        originalText.setLength(0);
        originalText.insert(0, this.getText());
        originalText.trimToSize();
    }

    /**
     * Restores the original text with all tokens unchanged to this label. Make sure to call {@link #parseTokens()} to
     * parse the tokens again.
     */
    protected void restoreOriginalText(){
        super.setText(originalText);
        this.parsed = false;
    }

    /** Returns the {@link TypingListener} associated with this label. May be {@code null}. */
    public TypingListener getTypingListener(){
        return listener;
    }

    /** Sets the {@link TypingListener} associated with this label, or {@code null} to remove the current one. */
    public void setTypingListener(TypingListener listener){
        this.listener = listener;
    }

    /**
     * Returns a {@link Color} instance with the color to be used on {@code CLEARCOLOR} tokens. Modify this instance to
     * change the token color. Default value is specified by {@link TypingConfig}.
     *
     * @see TypingConfig#DEFAULT_CLEAR_COLOR
     */
    public Color getClearColor(){
        return clearColor;
    }

    /**
     * Sets whether or not this instance should enable markup color by force.
     *
     * @see TypingConfig#FORCE_COLOR_MARKUP_BY_DEFAULT
     */
    public void setForceMarkupColor(boolean forceMarkupColor){
        this.forceMarkupColor = forceMarkupColor;
    }

    /** Returns the default token being used in this label. Defaults to empty string. */
    public String getDefaultToken(){
        return defaultToken;
    }

    /**
     * Sets the default token being used in this label. This token will be used before the label's text, and after each
     * {RESET} call. Useful if you want a certain token to be active at all times without having to type it all the
     * time.
     */
    public void setDefaultToken(String defaultToken){
        this.defaultToken = defaultToken == null ? "" : defaultToken;
        this.parsed = false;
    }

    /** Parses all tokens of this label. Use this after setting the text and any variables that should be replaced. */
    public void parseTokens(){
        this.setText(getDefaultToken() + getText(), false);
        Parser.parseTokens(this);
        parsed = true;
    }

    /**
     * Skips the char progression to the end, showing the entire label. Useful for when users don't want to wait for too
     * long. Ignores all subsequent events by default.
     */
    public void skipToTheEnd(){
        skipToTheEnd(true);
    }

    /**
     * Skips the char progression to the end, showing the entire label. Useful for when users don't want to wait for too
     * long.
     *
     * @param ignoreEvents If {@code true}, skipped events won't be reported to the listener.
     */
    public void skipToTheEnd(boolean ignoreEvents){
        skipToTheEnd(ignoreEvents, false);
    }

    /**
     * Skips the char progression to the end, showing the entire label. Useful for when users don't want to wait for too
     * long.
     *
     * @param ignoreEvents If {@code true}, skipped events won't be reported to the listener.
     * @param ignoreEffects If {@code true}, all text effects will be instantly cancelled.
     */
    public void skipToTheEnd(boolean ignoreEvents, boolean ignoreEffects){
        skipping = true;
        ignoringEvents = ignoreEvents;
        ignoringEffects = ignoreEffects;
    }

    /**
     * Cancels calls to {@link #skipToTheEnd()}. Useful if you need to restore the label's normal behavior at some event
     * after skipping.
     */
    public void cancelSkipping(){
        if(skipping){
            skipping = false;
            ignoringEvents = false;
            ignoringEffects = false;
        }
    }

    /** Returns whether or not this label is paused. */
    public boolean isPaused(){
        return paused;
    }

    /** Pauses this label's character progression. */
    public void pause(){
        paused = true;
    }

    /** Resumes this label's character progression. */
    public void resume(){
        paused = false;
    }

    /** Returns whether or not this label's char progression has ended. */
    public boolean hasEnded(){
        return ended;
    }

    /**
     * Restarts this label with the original text and starts the char progression right away. All tokens are
     * automatically parsed.
     */
    public void restart(){
        restart(getOriginalText());
    }

    /**
     * Restarts this label with the given text and starts the char progression right away. All tokens are automatically
     * parsed.
     */
    public void restart(CharSequence newText){
        // Reset cache collections
        Pools.freeAll(glyphCache);
        glyphCache.clear();
        glyphRunCapacities.clear();
        offsetCache.clear();
        layoutLineBreaks.clear();
        activeEffects.clear();

        // Reset state
        textSpeed = TypingConfig.DEFAULT_SPEED_PER_CHAR;
        charCooldown = textSpeed;
        rawCharIndex = -2;
        glyphCharIndex = -1;
        glyphCharCompensation = 0;
        cachedGlyphCharIndex = -1;
        lastLayoutX = 0;
        lastLayoutY = 0;
        parsed = false;
        paused = false;
        ended = false;
        skipping = false;
        ignoringEvents = false;
        ignoringEffects = false;

        // Set new text
        this.setText(newText);
        invalidate();

        // Parse tokens
        tokenEntries.clear();
        parseTokens();
    }

    /** Returns an {@link ObjectMap} with all the variable names and their respective replacement values. */
    public ObjectMap<String, String> getVariables(){
        return variables;
    }

    /** Registers a variable and its respective replacement value to this label. */
    public void setVariable(String var, String value){
        variables.put(var.toUpperCase(), value);
    }

    /** Registers a set of variables and their respective replacement values to this label. */
    public void setVariables(ObjectMap<String, String> variableMap){
        this.variables.clear();
        variableMap.each((key, val) -> variables.put(key.toUpperCase(), val));
    }

    /** Removes all variables from this label. */
    public void clearVariables(){
        this.variables.clear();
    }

    @Override
    public void act(float delta){
        super.act(delta);

        // Force token parsing
        if(!parsed){
            parseTokens();
        }

        // Update cooldown and process char progression
        if(skipping || (!ended && !paused)){
            if(skipping || (charCooldown -= delta) < 0.0f){
                processCharProgression();
            }
        }

        // Restore glyph offsets
        if(activeEffects.size > 0){
            for(int i = 0; i < glyphCache.size; i++){
                TypingGlyph glyph = glyphCache.get(i);
                glyph.xoffset = offsetCache.get(i * 2);
                glyph.yoffset = offsetCache.get(i * 2 + 1);
            }
        }

        // Apply effects
        if(!ignoringEffects){
            for(int i = activeEffects.size - 1; i >= 0; i--){
                Effect effect = activeEffects.get(i);
                effect.update(delta);
                int start = effect.indexStart;
                int end = effect.indexEnd >= 0 ? effect.indexEnd : glyphCharIndex;

                // If effect is finished, remove it
                if(effect.isFinished()){
                    activeEffects.remove(i);
                    continue;
                }

                // Apply effect to glyph
                for(int j = Math.max(0, start); j <= glyphCharIndex && j <= end && j < glyphCache.size; j++){
                    TypingGlyph glyph = glyphCache.get(j);
                    effect.apply(glyph, j, delta);
                }
            }
        }
    }

    /** Proccess char progression according to current cooldown and process all tokens in the current index. */
    private void processCharProgression(){
        // Keep a counter of how many chars we're processing in this tick.
        int charCounter = 0;

        // Process chars while there's room for it
        while(skipping || charCooldown < 0.0f){
            // Apply compensation to glyph index, if any
            if(glyphCharCompensation != 0){
                if(glyphCharCompensation > 0){
                    glyphCharIndex++;
                    glyphCharCompensation--;
                }else{
                    glyphCharIndex--;
                    glyphCharCompensation++;
                }

                // Increment cooldown and wait for it
                charCooldown += textSpeed;
                continue;
            }

            // Increase raw char index
            rawCharIndex++;

            // Get next character and calculate cooldown increment
            int safeIndex = Mathf.clamp(glyphCharIndex + 1, 0, glyphCache.size - 1);
            char primitiveChar = '\u0000'; // Null character by default
            if(glyphCache.size > 0){
                primitiveChar = (char)glyphCache.get(safeIndex).id;//getText().charAt(safeIndex);
                float intervalMultiplier = TypingConfig.INTERVAL_MULTIPLIERS_BY_CHAR.get(primitiveChar, 1);
                charCooldown += textSpeed * intervalMultiplier;
            }

            // If char progression is finished, or if text is empty, notify listener and abort routine
            int textLen = getText().length();
            if(textLen == 0 || rawCharIndex >= textLen){
                if(!ended){
                    ended = true;
                    skipping = false;
                    if(listener != null) listener.end();
                }
                return;
            }

            // Detect layout line breaks
            boolean isLayoutLineBreak = false;
            if(layoutLineBreaks.contains(glyphCharIndex)){
                layoutLineBreaks.removeValue(glyphCharIndex);
                isLayoutLineBreak = true;
            }

            // Increase glyph char index for all characters, except new lines.
            if(rawCharIndex >= 0 && primitiveChar != '\n' && !isLayoutLineBreak) glyphCharIndex++;

            // Process tokens according to the current index
            while(tokenEntries.size > 0 && tokenEntries.peek().index == rawCharIndex){
                TokenEntry entry = tokenEntries.pop();
                String token = entry.token;
                TokenCategory category = entry.category;

                // Process tokens
                switch(category){
                    case SPEED:{
                        textSpeed = entry.floatValue;
                        continue;
                    }
                    case WAIT:{
                        glyphCharIndex--;
                        glyphCharCompensation++;
                        charCooldown += entry.floatValue;
                        continue;
                    }
                    case SKIP:{
                        Log.info("SKIP at " + rawCharIndex + " from " + rawCharIndex + " to " + (rawCharIndex + entry.stringValue.length()));
                        if(entry.stringValue != null){
                            rawCharIndex += entry.stringValue.length();
                        }
                        continue;
                    }
                    case EVENT:{
                        if(this.listener != null && !ignoringEvents){
                            listener.event(entry.stringValue);
                        }
                        continue;
                    }
                    case EFFECT_START:
                    case EFFECT_END: {
                        // Get effect class
                        boolean isStart = category == TokenCategory.EFFECT_START;

                        // End all effects of the same type
                        for(int i = 0; i < activeEffects.size; i++){
                            Effect effect = activeEffects.get(i);
                            if(effect.indexEnd < 0){
                                if(effect.endToken.equals(token)){
                                    effect.indexEnd = glyphCharIndex - 1;
                                }
                            }
                        }

                        // Create new effect if necessary
                        if(isStart){
                            entry.effect.indexStart = glyphCharIndex;
                            activeEffects.add(entry.effect);
                        }

                        continue;
                    }
                }
            }

            // Notify listener about char progression
            if(listener != null){
                if(rawCharIndex > 0){
                    int nextIndex = Mathf.clamp(rawCharIndex, 0, getText().length() - 1);
                    char nextChar = getText().charAt(nextIndex);
                    listener.onChar(nextChar);
                }
            }

            // Increment char counter
            charCounter++;

            // Break loop if this was our first glyph to prevent glyph issues.
            if(glyphCharIndex == -1){
                charCooldown = textSpeed;
                break;
            }

            // Break loop if enough chars were processed
            charCounter++;
            int charLimit = TypingConfig.CHAR_LIMIT_PER_FRAME;
            if(!skipping && charLimit > 0 && charCounter > charLimit){
                charCooldown = textSpeed;
                break;
            }
        }
    }

    @Override
    public boolean remove(){
        Pools.freeAll(glyphCache);
        glyphCache.clear();
        return super.remove();
    }

    @Override
    public BitmapFontCache getBitmapFontCache(){
        return super.getBitmapFontCache();
    }

    @Override
    public void setEllipsis(String ellipsis){
        // Mimics superclass but keeps an accessible reference
        super.setEllipsis(ellipsis);
        this.ellipsis = ellipsis;
    }

    @Override
    public void setEllipsis(boolean ellipsis){
        // Mimics superclass but keeps an accessible reference
        super.setEllipsis(ellipsis);
        if(ellipsis)
            this.ellipsis = "...";
        else
            this.ellipsis = null;
    }

    @Override
    public void setWrap(boolean wrap){
        // Mimics superclass but keeps an accessible reference
        super.setWrap(wrap);
        this.wrap = wrap;
    }

    @Override
    public void setFontScale(float fontScale){
        super.setFontScale(fontScale);
        this.fontScaleChanged = true;
    }

    @Override
    public void setFontScale(float fontScaleX, float fontScaleY){
        super.setFontScale(fontScaleX, fontScaleY);
        this.fontScaleChanged = true;
    }

    @Override
    public void setFontScaleX(float fontScaleX){
        super.setFontScaleX(fontScaleX);
        this.fontScaleChanged = true;
    }

    @Override
    public void setFontScaleY(float fontScaleY){
        super.setFontScaleY(fontScaleY);
        this.fontScaleChanged = true;
    }

    @Override
    public void layout(){
        // --- SUPERCLASS IMPLEMENTATION (but with accessible getters instead) ---
        BitmapFontCache cache = getBitmapFontCache();
        StringBuilder text = getText();
        GlyphLayout layout = super.getGlyphLayout();
        int lineAlign = getLineAlign();
        int labelAlign = getLabelAlign();
        LabelStyle style = getStyle();

        BitmapFont font = cache.getFont();
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();
        if(fontScaleChanged) font.getData().setScale(getFontScaleX(), getFontScaleY());

        boolean wrap = this.wrap && ellipsis == null;
        if(wrap){
            float prefHeight = getPrefHeight();
            if(prefHeight != lastPrefHeight){
                lastPrefHeight = prefHeight;
                invalidateHierarchy();
            }
        }

        float width = getWidth(), height = getHeight();
        Drawable background = style.background;
        float x = 0, y = 0;
        if(background != null){
            x = background.getLeftWidth();
            y = background.getBottomHeight();
            width -= background.getLeftWidth() + background.getRightWidth();
            height -= background.getBottomHeight() + background.getTopHeight();
        }

        float textWidth, textHeight;
        // if (wrap || text.indexOf("\n") != -1)
        {
            // If the text can span multiple lines, determine the text's actual size so it can be aligned within the label.
            layout.setText(font, text, 0, text.length(), Color.WHITE, width, lineAlign, wrap, ellipsis);
            textWidth = layout.width;
            textHeight = layout.height;

            if((labelAlign & Align.left) == 0){
                if((labelAlign & Align.right) != 0)
                    x += width - textWidth;
                else
                    x += (width - textWidth) / 2;
            }
            // } else {
            // textWidth = width;
            // textHeight = font.getData().capHeight;
        }

        if((labelAlign & Align.top) != 0){
            y += cache.getFont().isFlipped() ? 0 : height - textHeight;
            y += style.font.getDescent();
        }else if((labelAlign & Align.bottom) != 0){
            y += cache.getFont().isFlipped() ? height - textHeight : 0;
            y -= style.font.getDescent();
        }else{
            y += (height - textHeight) / 2;
        }
        if(!cache.getFont().isFlipped()) y += textHeight;

        // Don't set the layout or cache now, since we progressively update both over time.
        // layout.setText(font, text, 0, text.length, Color.WHITE, textWidth, lineAlign, wrap, ellipsis);
        // cache.setText(layout, x, y);
        if(fontScaleChanged) font.getData().setScale(oldScaleX, oldScaleY);

        // --- END OF SUPERCLASS IMPLEMENTATION ---

        // Store coordinates passed to BitmapFontCache
        lastLayoutX = x;
        lastLayoutY = y;

        // Perform cache layout operation, where the magic happens
        Pools.freeAll(glyphCache);
        glyphCache.clear();
        layoutCache();
    }

    /**
     * Reallocate glyph clones according to the updated {@link GlyphLayout}. This should only be called when the text or
     * the layout changes.
     */
    private void layoutCache(){
        BitmapFontCache cache = getBitmapFontCache();
        GlyphLayout layout = super.getGlyphLayout();
        Array<GlyphRun> runs = layout.runs;

        // Reset layout line breaks
        layoutLineBreaks.clear();

        // Store GlyphRun sizes and count how many glyphs we have
        int glyphCount = 0;
        glyphRunCapacities.setSize(runs.size);
        for(int i = 0; i < runs.size; i++){
            Array<Glyph> glyphs = runs.get(i).glyphs;
            glyphRunCapacities.set(i, glyphs.size);
            glyphCount += glyphs.size;
        }

        // Make sure our cache array can hold all glyphs
        if(glyphCache.size < glyphCount){
            glyphCache.setSize(glyphCount);
            offsetCache.setSize(glyphCount * 2);
        }

        // Clone original glyphs with independent instances
        int index = -1;
        float lastY = 0;
        for(int i = 0; i < runs.size; i++){
            GlyphRun run = runs.get(i);
            Array<Glyph> glyphs = run.glyphs;
            for(int j = 0; j < glyphs.size; j++){

                // Detect and store layout line breaks
                if(!Mathf.isEqual(run.y, lastY)){
                    lastY = run.y;
                    layoutLineBreaks.add(index);
                }

                // Increment index
                index++;

                // Get original glyph
                Glyph original = glyphs.get(j);

                // Get clone glyph
                TypingGlyph clone = null;
                if(index < glyphCache.size){
                    clone = glyphCache.get(index);
                }
                if(clone == null){
                    clone = Pools.obtain(TypingGlyph.class, TypingGlyph::new);
                    glyphCache.set(index, clone);
                }
                clone.set(original);
                clone.width *= getFontScaleX();
                clone.height *= getFontScaleY();
                clone.xoffset *= getFontScaleX();
                clone.yoffset *= getFontScaleY();
                clone.run = run;

                // Store offset data
                offsetCache.set(index * 2, clone.xoffset);
                offsetCache.set(index * 2 + 1, clone.yoffset);

                // Replace glyph in original array
                glyphs.set(j, clone);
            }
        }

        // Remove exceeding glyphs from original array
        int glyphCountdown = glyphCharIndex;
        for(int i = 0; i < runs.size; i++){
            Array<Glyph> glyphs = runs.get(i).glyphs;
            if(glyphs.size < glyphCountdown){
                glyphCountdown -= glyphs.size;
                continue;
            }

            for(int j = 0; j < glyphs.size; j++){
                if(glyphCountdown < 0){
                    glyphs.removeRange(j, glyphs.size - 1);
                    break;
                }
                glyphCountdown--;
            }
        }

        // Pass new layout with custom glyphs to BitmapFontCache
        cache.setText(layout, lastLayoutX, lastLayoutY);
    }

    /** Adds cached glyphs to the active BitmapFontCache as the char index progresses. */
    private void addMissingGlyphs(){
        // Add additional glyphs to layout array, if any
        int glyphLeft = glyphCharIndex - cachedGlyphCharIndex;
        if(glyphLeft < 1) return;

        // Get runs
        GlyphLayout layout = super.getGlyphLayout();
        Array<GlyphRun> runs = layout.runs;

        // Iterate through GlyphRuns to find the next glyph spot
        int glyphCount = 0;
        for(int runIndex = 0; runIndex < glyphRunCapacities.size; runIndex++){
            int runCapacity = glyphRunCapacities.get(runIndex);
            if((glyphCount + runCapacity) < cachedGlyphCharIndex){
                glyphCount += runCapacity;
                continue;
            }

            // Get run and increase glyphCount up to its current size
            Array<Glyph> glyphs = runs.get(runIndex).glyphs;
            glyphCount += glyphs.size;

            // Next glyphs go here
            while(glyphLeft > 0){

                // Skip run if this one is full
                int runSize = glyphs.size;
                if(runCapacity == runSize){
                    break;
                }

                // Put new glyph to this run
                cachedGlyphCharIndex++;
                TypingGlyph glyph = glyphCache.get(cachedGlyphCharIndex);
                glyphs.add(glyph);

                // Cache glyph's vertex index
                glyph.internalIndex = glyphCount;

                // Advance glyph count
                glyphCount++;
                glyphLeft--;
            }
        }
    }

    @Override
    public void draw(){
        super.validate();
        addMissingGlyphs();

        // Update cache with new glyphs
        BitmapFontCache bitmapFontCache = getBitmapFontCache();
        getBitmapFontCache().setText(getGlyphLayout(), lastLayoutX, lastLayoutY);

        // Tint glyphs
        for(TypingGlyph glyph : glyphCache){
            if(glyph.internalIndex >= 0 && glyph.color != null){
                bitmapFontCache.setColors(glyph.color, glyph.internalIndex, glyph.internalIndex + 1);
            }
        }

        super.draw();
    }

}
