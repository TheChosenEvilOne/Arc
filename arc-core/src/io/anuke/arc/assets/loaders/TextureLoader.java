package io.anuke.arc.assets.loaders;

import io.anuke.arc.assets.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.files.*;
import io.anuke.arc.graphics.Pixmap.*;
import io.anuke.arc.graphics.*;
import io.anuke.arc.graphics.Texture.*;

/**
 * {@link AssetLoader} for {@link Texture} instances. The pixel data is loaded asynchronously. The texture is then created on the
 * rendering thread, synchronously. Passing a {@link TextureParameter} to
 * {@link AssetManager#load(String, Class, AssetLoaderParameters)} allows one to specify parameters as can be passed to the
 * various Texture constructors, e.g. filtering, whether to generate mipmaps and so on.
 * @author mzechner
 */
public class TextureLoader extends AsynchronousAssetLoader<Texture, TextureLoader.TextureParameter>{
    TextureLoaderInfo info = new TextureLoaderInfo();

    public TextureLoader(FileHandleResolver resolver){
        super(resolver);
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, TextureParameter parameter){
        info.filename = fileName;
        if(parameter == null || parameter.textureData == null){
            Format format = null;
            boolean genMipMaps = false;
            info.texture = null;

            if(parameter != null){
                format = parameter.format;
                genMipMaps = parameter.genMipMaps;
                info.texture = parameter.texture;
            }

            info.data = TextureData.Factory.loadFromFile(file, format, genMipMaps);
        }else{
            info.data = parameter.textureData;
            info.texture = parameter.texture;
        }
        if(!info.data.isPrepared()) info.data.prepare();
    }

    @Override
    public Texture loadSync(AssetManager manager, String fileName, FileHandle file, TextureParameter parameter){
        if(info == null) return null;
        Texture texture = info.texture;
        if(texture != null){
            texture.load(info.data);
        }else{
            texture = new Texture(info.data);
        }
        if(parameter != null){
            texture.setFilter(parameter.minFilter, parameter.magFilter);
            texture.setWrap(parameter.wrapU, parameter.wrapV);
        }
        return texture;
    }

    @Override
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, TextureParameter parameter){
        return null;
    }

    public static class TextureLoaderInfo{
        String filename;
        TextureData data;
        Texture texture;
    }

    public static class TextureParameter extends AssetLoaderParameters<Texture>{
        /** the format of the final Texture. Uses the source images format if null **/
        public Format format = null;
        /** whether to generate mipmaps **/
        public boolean genMipMaps = false;
        /** The texture to put the {@link TextureData} in, optional. **/
        public Texture texture = null;
        /** TextureData for textures created on the fly, optional. When set, all format and genMipMaps are ignored */
        public TextureData textureData = null;
        public TextureFilter minFilter = TextureFilter.Nearest;
        public TextureFilter magFilter = TextureFilter.Nearest;
        public TextureWrap wrapU = TextureWrap.ClampToEdge;
        public TextureWrap wrapV = TextureWrap.ClampToEdge;

        public TextureParameter(){
        }
    }
}
