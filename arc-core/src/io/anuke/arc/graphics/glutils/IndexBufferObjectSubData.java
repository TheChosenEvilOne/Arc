package io.anuke.arc.graphics.glutils;

import io.anuke.arc.Core;
import io.anuke.arc.graphics.GL20;
import io.anuke.arc.util.ArcRuntimeException;
import io.anuke.arc.util.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 * <p>
 * IndexBufferObject wraps OpenGL's index buffer functionality to be used in conjunction with VBOs.
 * </p>
 *
 * <p>
 * You can also use this to store indices for vertex arrays. Do not call {@link #bind()} or {@link #unbind()} in this case but
 * rather use {@link #getBuffer()} to use the buffer directly with glDrawElements. You must also create the IndexBufferObject with
 * the second constructor and specify isDirect as true as glDrawElements in conjunction with vertex arrays needs direct buffers.
 * </p>
 *
 * <p>
 * VertexBufferObjects must be disposed via the {@link #dispose()} method when no longer needed
 * </p>
 * @author mzechner
 */
public class IndexBufferObjectSubData implements IndexData{
    final ShortBuffer buffer;
    final ByteBuffer byteBuffer;
    final boolean isDirect;
    final int usage;
    int bufferHandle;
    boolean isDirty = true;
    boolean isBound = false;

    /**
     * Creates a new IndexBufferObject.
     * @param isStatic whether the index buffer is static
     * @param maxIndices the maximum number of indices this buffer can hold
     */
    public IndexBufferObjectSubData(boolean isStatic, int maxIndices){
        byteBuffer = BufferUtils.newByteBuffer(maxIndices * 2);
        isDirect = true;

        usage = isStatic ? GL20.GL_STATIC_DRAW : GL20.GL_DYNAMIC_DRAW;
        buffer = byteBuffer.asShortBuffer();
        buffer.flip();
        byteBuffer.flip();
        bufferHandle = createBufferObject();
    }

    /**
     * Creates a new IndexBufferObject to be used with vertex arrays.
     * @param maxIndices the maximum number of indices this buffer can hold
     */
    public IndexBufferObjectSubData(int maxIndices){
        byteBuffer = BufferUtils.newByteBuffer(maxIndices * 2);
        this.isDirect = true;

        usage = GL20.GL_STATIC_DRAW;
        buffer = byteBuffer.asShortBuffer();
        buffer.flip();
        byteBuffer.flip();
        bufferHandle = createBufferObject();
    }

    private int createBufferObject(){
        int result = Core.gl20.glGenBuffer();
        Core.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, result);
        Core.gl20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, byteBuffer.capacity(), null, usage);
        Core.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0);
        return result;
    }

    /** @return the number of indices currently stored in this buffer */
    public int getNumIndices(){
        return buffer.limit();
    }

    /** @return the maximum number of indices this IndexBufferObject can store. */
    public int getNumMaxIndices(){
        return buffer.capacity();
    }

    /**
     * <p>
     * Sets the indices of this IndexBufferObject, discarding the old indices. The count must equal the number of indices to be
     * copied to this IndexBufferObject.
     * </p>
     *
     * <p>
     * This can be called in between calls to {@link #bind()} and {@link #unbind()}. The index data will be updated instantly.
     * </p>
     * @param indices the vertex data
     * @param offset the offset to start copying the data from
     * @param count the number of floats to copy
     */
    public void setIndices(short[] indices, int offset, int count){
        isDirty = true;
        buffer.clear();
        buffer.put(indices, offset, count);
        buffer.flip();
        byteBuffer.position(0);
        byteBuffer.limit(count << 1);

        if(isBound){
            Core.gl20.glBufferSubData(GL20.GL_ELEMENT_ARRAY_BUFFER, 0, byteBuffer.limit(), byteBuffer);
            isDirty = false;
        }
    }

    public void setIndices(ShortBuffer indices){
        int pos = indices.position();
        isDirty = true;
        buffer.clear();
        buffer.put(indices);
        buffer.flip();
        indices.position(pos);
        byteBuffer.position(0);
        byteBuffer.limit(buffer.limit() << 1);

        if(isBound){
            Core.gl20.glBufferSubData(GL20.GL_ELEMENT_ARRAY_BUFFER, 0, byteBuffer.limit(), byteBuffer);
            isDirty = false;
        }
    }

    @Override
    public void updateIndices(int targetOffset, short[] indices, int offset, int count){
        isDirty = true;
        final int pos = byteBuffer.position();
        byteBuffer.position(targetOffset * 2);
        BufferUtils.copy(indices, offset, byteBuffer, count);
        byteBuffer.position(pos);
        buffer.position(0);

        if(isBound){
            Core.gl20.glBufferSubData(GL20.GL_ELEMENT_ARRAY_BUFFER, 0, byteBuffer.limit(), byteBuffer);
            isDirty = false;
        }
    }


    /**
     * <p>
     * Returns the underlying ShortBuffer. If you modify the buffer contents they wil be uploaded on the call to {@link #bind()}.
     * If you need immediate uploading use {@link #setIndices(short[], int, int)}.
     * </p>
     * @return the underlying short buffer.
     */
    public ShortBuffer getBuffer(){
        isDirty = true;
        return buffer;
    }

    /** Binds this IndexBufferObject for rendering with glDrawElements. */
    public void bind(){
        if(bufferHandle == 0)
            throw new ArcRuntimeException("IndexBufferObject cannot be used after it has been disposed.");

        Core.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, bufferHandle);
        if(isDirty){
            byteBuffer.limit(buffer.limit() * 2);
            Core.gl20.glBufferSubData(GL20.GL_ELEMENT_ARRAY_BUFFER, 0, byteBuffer.limit(), byteBuffer);
            isDirty = false;
        }
        isBound = true;
    }

    /** Unbinds this IndexBufferObject. */
    public void unbind(){
        Core.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0);
        isBound = false;
    }

    /** Invalidates the IndexBufferObject so a new OpenGL buffer handle is created. Use this in case of a context loss. */
    public void invalidate(){
        bufferHandle = createBufferObject();
        isDirty = true;
    }

    /** Disposes this IndexBufferObject and all its associated OpenGL resources. */
    public void dispose(){
        GL20 gl = Core.gl20;
        gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0);
        gl.glDeleteBuffer(bufferHandle);
        bufferHandle = 0;
    }
}
