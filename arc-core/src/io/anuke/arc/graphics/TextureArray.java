package io.anuke.arc.graphics;

import io.anuke.arc.Application;
import io.anuke.arc.Core;
import io.anuke.arc.collection.Array;
import io.anuke.arc.files.FileHandle;
import io.anuke.arc.util.ArcRuntimeException;

import java.util.HashMap;
import java.util.Map;

/**
 * Open GLES wrapper for TextureArray
 * @author Tomski
 */
public class TextureArray extends GLTexture{
    final static Map<Application, Array<TextureArray>> managedTextureArrays = new HashMap<>();

    private TextureArrayData data;

    public TextureArray(String... internalPaths){
        this(getInternalHandles(internalPaths));
    }

    public TextureArray(FileHandle... files){
        this(false, files);
    }

    public TextureArray(boolean useMipMaps, FileHandle... files){
        this(useMipMaps, Pixmap.Format.RGBA8888, files);
    }

    public TextureArray(boolean useMipMaps, Pixmap.Format format, FileHandle... files){
        this(TextureArrayData.Factory.loadFromFiles(format, useMipMaps, files));
    }

    public TextureArray(TextureArrayData data){
        super(GL30.GL_TEXTURE_2D_ARRAY, Core.gl.glGenTexture());

        if(Core.gl30 == null){
            throw new ArcRuntimeException("TextureArray requires a device running with GLES 3.0 compatibilty");
        }

        load(data);

        if(data.isManaged()) addManagedTexture(Core.app, this);
    }

    private static FileHandle[] getInternalHandles(String... internalPaths){
        FileHandle[] handles = new FileHandle[internalPaths.length];
        for(int i = 0; i < internalPaths.length; i++){
            handles[i] = Core.files.internal(internalPaths[i]);
        }
        return handles;
    }

    private static void addManagedTexture(Application app, TextureArray texture){
        Array<TextureArray> managedTextureArray = managedTextureArrays.get(app);
        if(managedTextureArray == null) managedTextureArray = new Array<>();
        managedTextureArray.add(texture);
        managedTextureArrays.put(app, managedTextureArray);
    }

    /** Clears all managed TextureArrays. This is an internal method. Do not use it! */
    public static void clearAllTextureArrays(Application app){
        managedTextureArrays.remove(app);
    }

    /** Invalidate all managed TextureArrays. This is an internal method. Do not use it! */
    public static void invalidateAllTextureArrays(Application app){
        Array<TextureArray> managedTextureArray = managedTextureArrays.get(app);
        if(managedTextureArray == null) return;

        for(int i = 0; i < managedTextureArray.size; i++){
            TextureArray textureArray = managedTextureArray.get(i);
            textureArray.reload();
        }
    }

    public static String getManagedStatus(){
        StringBuilder builder = new StringBuilder();
        builder.append("Managed TextureArrays/app: { ");
        for(Application app : managedTextureArrays.keySet()){
            builder.append(managedTextureArrays.get(app).size);
            builder.append(" ");
        }
        builder.append("}");
        return builder.toString();
    }

    /** @return the number of managed TextureArrays currently loaded */
    public static int getNumManagedTextureArrays(){
        return managedTextureArrays.get(Core.app).size;
    }

    private void load(TextureArrayData data){
        if(this.data != null && data.isManaged() != this.data.isManaged())
            throw new ArcRuntimeException("New data must have the same managed status as the old data");
        this.data = data;

        bind();
        Core.gl30.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, data.getInternalFormat(), data.getWidth(), data.getHeight(), data.getDepth(), 0, data.getInternalFormat(), data.getGLType(), null);

        if(!data.isPrepared()) data.prepare();

        data.consumeTextureArrayData();

        setFilter(minFilter, magFilter);
        setWrap(uWrap, vWrap);
        Core.gl.glBindTexture(glTarget, 0);
    }

    @Override
    public int getWidth(){
        return data.getWidth();
    }

    @Override
    public int getHeight(){
        return data.getHeight();
    }

    @Override
    public int getDepth(){
        return data.getDepth();
    }

    @Override
    public boolean isManaged(){
        return data.isManaged();
    }

    @Override
    protected void reload(){
        if(!isManaged()) throw new ArcRuntimeException("Tried to reload an unmanaged TextureArray");
        glHandle = Core.gl.glGenTexture();
        load(data);
    }

}
