package io.anuke.arc.backends.sdl;

import io.anuke.arc.*;
import io.anuke.arc.Graphics.Cursor.*;
import io.anuke.arc.backends.sdl.jni.SDL;
import io.anuke.arc.collection.*;
import io.anuke.arc.graphics.*;
import io.anuke.arc.graphics.glutils.*;
import io.anuke.arc.util.*;

public class SdlGraphics extends Graphics{
    private GL20 gl20;
    private GLVersion glVersion;
    private BufferFormat bufferFormat;
    private SdlApplication app;
    private ObjectMap<SystemCursor, SdlCursor> cursors;

    private long lastFrameTime = -1;
    private float deltaTime;
    private long frameId;
    private long frameCounterStart = 0;
    private int frames;
    private int fps;

    int backBufferWidth;
    int backBufferHeight;
    int logicalWidth;
    int logicalHeight;

    SdlGraphics(SdlApplication app){
        this.app = app;
        Core.gl = Core.gl20 = gl20 = new SdlGL20();

        String versionString = gl20.glGetString(GL20.GL_VERSION);
        String vendorString = gl20.glGetString(GL20.GL_VENDOR);
        String rendererString = gl20.glGetString(GL20.GL_RENDERER);

        cursors = new ObjectMap<>();
        glVersion = new GLVersion(Application.ApplicationType.Desktop, versionString, vendorString, rendererString);
        bufferFormat = new BufferFormat(app.config.r, app.config.g, app.config.b, app.config.a, app.config.depth, app.config.stencil, app.config.samples, false);

        if(!glVersion.isVersionEqualToOrHigher(2, 0)){
            throw new ArcRuntimeException("OpenGL 2.0 or higher with the FBO extension is required. OpenGL version: " + versionString);
        }

        clear(app.config.initialBackgroundColor);
        io.anuke.arc.backends.sdl.jni.SDL.SDL_GL_SwapWindow(app.window);
    }

    void update(){
        long time = System.nanoTime();
        if(lastFrameTime == -1)
            lastFrameTime = time;
        deltaTime = (time - lastFrameTime) / 1000000000.0f;
        lastFrameTime = time;

        if(time - frameCounterStart >= 1000000000){
            fps = frames;
            frames = 0;
            frameCounterStart = time;
        }
        frames++;
        frameId++;
    }

    void updateSize(int width, int height){
        logicalWidth = width;
        logicalHeight = height;
        backBufferWidth = width;
        backBufferHeight = height;

        gl20.glViewport(0, 0, width, height);
    }

    @Override
    public boolean isGL30Available(){
        return false;
    }

    @Override
    public GL20 getGL20(){
        return gl20;
    }

    @Override
    public void setGL20(GL20 gl20){
        this.gl20 = gl20;
    }

    @Override
    public GL30 getGL30(){
        return null;
    }

    @Override
    public void setGL30(GL30 gl30){

    }

    @Override
    public int getWidth(){
        if(app.config.hdpiMode == HdpiMode.Pixels){
            return backBufferWidth;
        }else{
            return logicalWidth;
        }
    }

    @Override
    public int getHeight(){
        if(app.config.hdpiMode == HdpiMode.Pixels){
            return backBufferHeight;
        }else{
            return logicalHeight;
        }
    }

    @Override
    public int getBackBufferWidth(){
        return backBufferWidth;
    }

    @Override
    public int getBackBufferHeight(){
        return backBufferHeight;
    }

    @Override
    public long getFrameId(){
        return frameId;
    }

    @Override
    public float getDeltaTime(){
        return deltaTime;
    }

    @Override
    public float getRawDeltaTime(){
        return deltaTime;
    }

    @Override
    public int getFramesPerSecond(){
        return fps;
    }

    @Override
    public GLVersion getGLVersion(){
        return glVersion;
    }

    @Override
    public float getPpiX(){
        return 0;
    }

    @Override
    public float getPpiY(){
        return 0;
    }

    @Override
    public float getPpcX(){
        return 0;
    }

    @Override
    public float getPpcY(){
        return 0;
    }

    @Override
    public float getDensity(){
        return 0;
    }

    @Override
    public boolean supportsDisplayModeChange(){
        return true;
    }

    @Override
    public Monitor getPrimaryMonitor(){
        return getMonitor();
    }

    @Override
    public Monitor getMonitor(){
        return new Monitor(0, 0, "Monitor");
    }

    @Override
    public Monitor[] getMonitors(){
        return new Monitor[0];
    }

    @Override
    public DisplayMode[] getDisplayModes(){
        return new DisplayMode[0];
    }

    @Override
    public DisplayMode[] getDisplayModes(Monitor monitor){
        return new DisplayMode[0];
    }

    @Override
    public DisplayMode getDisplayMode(){
        return new DisplayMode(getWidth(), getHeight(), 60, 32);
    }

    @Override
    public DisplayMode getDisplayMode(Monitor monitor){
        return getDisplayMode();
    }

    @Override
    public boolean setFullscreenMode(DisplayMode displayMode){
        //TODO ignores display mode
        io.anuke.arc.backends.sdl.jni.SDL.SDL_SetWindowFullscreen(app.window, io.anuke.arc.backends.sdl.jni.SDL.SDL_WINDOW_FULLSCREEN_DESKTOP);
        return true;
    }

    @Override
    public boolean setWindowedMode(int width, int height){
        io.anuke.arc.backends.sdl.jni.SDL.SDL_SetWindowFullscreen(app.window, 0);
        io.anuke.arc.backends.sdl.jni.SDL.SDL_SetWindowSize(app.window, width, height);
        return true;
    }

    @Override
    public void setTitle(String title){
        io.anuke.arc.backends.sdl.jni.SDL.SDL_SetWindowTitle(app.window, title);
    }

    @Override
    public void setUndecorated(boolean undecorated){
        boolean maximized = (io.anuke.arc.backends.sdl.jni.SDL.SDL_GetWindowFlags(app.window) & io.anuke.arc.backends.sdl.jni.SDL.SDL_WINDOW_MAXIMIZED) == io.anuke.arc.backends.sdl.jni.SDL.SDL_WINDOW_MAXIMIZED;
        if(maximized){
            io.anuke.arc.backends.sdl.jni.SDL.SDL_RestoreWindow(app.window);
        }

        io.anuke.arc.backends.sdl.jni.SDL.SDL_SetWindowBordered(app.window, !undecorated);

        if(maximized){
            io.anuke.arc.backends.sdl.jni.SDL.SDL_MaximizeWindow(app.window);
        }
    }

    @Override
    public void setResizable(boolean resizable){
        //this is utterly useless
    }

    @Override
    public void setVSync(boolean vsync){
        io.anuke.arc.backends.sdl.jni.SDL.SDL_GL_SetSwapInterval(vsync ? 1 : 0);
    }

    @Override
    public BufferFormat getBufferFormat(){
        return bufferFormat;
    }

    @Override
    public boolean supportsExtension(String extension){
        return io.anuke.arc.backends.sdl.jni.SDL.SDL_GL_ExtensionSupported(extension);
    }

    @Override
    public boolean isContinuousRendering(){
        return false;
    }

    @Override
    public void setContinuousRendering(boolean isContinuous){

    }

    @Override
    public void requestRendering(){

    }

    @Override
    public boolean isFullscreen(){
        return (io.anuke.arc.backends.sdl.jni.SDL.SDL_GetWindowFlags(app.window) & io.anuke.arc.backends.sdl.jni.SDL.SDL_WINDOW_FULLSCREEN) == io.anuke.arc.backends.sdl.jni.SDL.SDL_WINDOW_FULLSCREEN;
    }

    @Override
    public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot){
        long surface = io.anuke.arc.backends.sdl.jni.SDL.SDL_CreateRGBSurfaceFrom(pixmap.getPixels(), pixmap.getWidth(), pixmap.getHeight());
        long cursor = io.anuke.arc.backends.sdl.jni.SDL.SDL_CreateColorCursor(surface, xHotspot, yHotspot);
        return new SdlCursor(surface, cursor);
    }

    @Override
    protected void setCursor(Cursor cursor){
        io.anuke.arc.backends.sdl.jni.SDL.SDL_SetCursor(((SdlCursor)cursor).cursorHandle);
    }

    @Override
    protected void setSystemCursor(SystemCursor cursor){
        if(!cursors.containsKey(cursor)){
            long handle = io.anuke.arc.backends.sdl.jni.SDL.SDL_CreateSystemCursor(mapCursor(cursor));
            cursors.put(cursor, new SdlCursor(0, handle));
        }
        io.anuke.arc.backends.sdl.jni.SDL.SDL_SetCursor(cursors.get(cursor).cursorHandle);
    }

    @Override
    public void dispose(){
        super.dispose();

        cursors.each((ignored, value) -> value.dispose());
    }

    private int mapCursor(SystemCursor cursor){
        switch(cursor){
            case arrow: return io.anuke.arc.backends.sdl.jni.SDL.SDL_SYSTEM_CURSOR_ARROW;
            case ibeam: return io.anuke.arc.backends.sdl.jni.SDL.SDL_SYSTEM_CURSOR_IBEAM;
            case crosshair: return io.anuke.arc.backends.sdl.jni.SDL.SDL_SYSTEM_CURSOR_CROSSHAIR;
            case hand: return io.anuke.arc.backends.sdl.jni.SDL.SDL_SYSTEM_CURSOR_HAND;
            case horizontalResize: return io.anuke.arc.backends.sdl.jni.SDL.SDL_SYSTEM_CURSOR_SIZEWE;
            case verticalResize: return io.anuke.arc.backends.sdl.jni.SDL.SDL_SYSTEM_CURSOR_SIZENS;
        }
        throw new IllegalArgumentException("this is impossible.");
    }

    public static class SdlCursor implements Cursor{
        final long surfaceHandle, cursorHandle;

        public SdlCursor(long surfaceHandle, long cursorHandle){
            this.surfaceHandle = surfaceHandle;
            this.cursorHandle = cursorHandle;
        }

        @Override
        public void dispose(){
            if(cursorHandle != 0) io.anuke.arc.backends.sdl.jni.SDL.SDL_FreeCursor(cursorHandle);
            if(surfaceHandle != 0) SDL.SDL_FreeSurface(surfaceHandle);
        }
    }
}
