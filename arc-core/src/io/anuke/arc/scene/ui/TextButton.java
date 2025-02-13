package io.anuke.arc.scene.ui;

import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.BitmapFont;
import io.anuke.arc.scene.style.Drawable;
import io.anuke.arc.scene.ui.Label.LabelStyle;
import io.anuke.arc.scene.ui.layout.Cell;
import io.anuke.arc.util.Align;

import static io.anuke.arc.Core.scene;

/**
 * A button with a child {@link Label} to display text.
 * @author Nathan Sweet
 */
public class TextButton extends Button{
    protected final Label label;
    private TextButtonStyle style;

    public TextButton(String text){
        this(text, scene.getStyle(TextButtonStyle.class));
    }

    public TextButton(String text, TextButtonStyle style){
        super();
        setStyle(style);
        this.style = style;
        label = new Label(text, new LabelStyle(style.font, style.fontColor));
        label.setAlignment(Align.center);
        add(label).expand().fill().wrap().minWidth(getMinWidth());
        setSize(getPrefWidth(), getPrefHeight());
    }

    public TextButtonStyle getStyle(){
        return style;
    }

    public void setStyle(ButtonStyle style){
        if(style == null) throw new NullPointerException("style cannot be null");
        if(!(style instanceof TextButtonStyle)) throw new IllegalArgumentException("style must be a TextButtonStyle.");
        super.setStyle(style);
        this.style = (TextButtonStyle)style;
        if(label != null){
            TextButtonStyle textButtonStyle = (TextButtonStyle)style;
            LabelStyle labelStyle = label.getStyle();
            labelStyle.font = textButtonStyle.font;
            labelStyle.fontColor = textButtonStyle.fontColor;
            label.setStyle(labelStyle);
        }
    }

    @Override
    public void draw(){
        Color fontColor;
        if(isDisabled() && style.disabledFontColor != null)
            fontColor = style.disabledFontColor;
        else if(isPressed() && style.downFontColor != null)
            fontColor = style.downFontColor;
        else if(isChecked && style.checkedFontColor != null)
            fontColor = (isOver() && style.checkedOverFontColor != null) ? style.checkedOverFontColor : style.checkedFontColor;
        else if(isOver() && style.overFontColor != null)
            fontColor = style.overFontColor;
        else
            fontColor = style.fontColor;
        if(fontColor != null) label.getStyle().fontColor = fontColor;
        super.draw();
    }

    public Label getLabel(){
        return label;
    }

    public Cell<Label> getLabelCell(){
        return getCell(label);
    }

    public CharSequence getText(){
        return label.getText();
    }

    public void setText(String text){
        label.setText(text);
    }

    /**
     * The style for a text button, see {@link TextButton}.
     * @author Nathan Sweet
     */
    public static class TextButtonStyle extends ButtonStyle{
        public BitmapFont font;
        /** Optional. */
        public Color fontColor, downFontColor, overFontColor, checkedFontColor, checkedOverFontColor, disabledFontColor;

        public TextButtonStyle(){
        }

        public TextButtonStyle(Drawable up, Drawable down, Drawable checked, BitmapFont font){
            super(up, down, checked);
            this.font = font;
        }

        public TextButtonStyle(TextButtonStyle style){
            super(style);
            this.font = style.font;
            if(style.fontColor != null) this.fontColor = new Color(style.fontColor);
            if(style.downFontColor != null) this.downFontColor = new Color(style.downFontColor);
            if(style.overFontColor != null) this.overFontColor = new Color(style.overFontColor);
            if(style.checkedFontColor != null) this.checkedFontColor = new Color(style.checkedFontColor);
            if(style.checkedOverFontColor != null) this.checkedFontColor = new Color(style.checkedOverFontColor);
            if(style.disabledFontColor != null) this.disabledFontColor = new Color(style.disabledFontColor);
        }
    }
}
