package io.anuke.arc.graphics.glutils;

import io.anuke.arc.util.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class IndexArray implements IndexData{
    final ShortBuffer buffer;
    final ByteBuffer byteBuffer;

    // used to work around bug: https://android-review.googlesource.com/#/c/73175/
    private final boolean empty;

    /**
     * Creates a new IndexArray to be used with vertex arrays.
     * @param maxIndices the maximum number of indices this buffer can hold
     */
    public IndexArray(int maxIndices){

        empty = maxIndices == 0;
        if(empty){
            maxIndices = 1; // avoid allocating a zero-sized buffer because of bug in Android's ART < Android 5.0
        }

        byteBuffer = BufferUtils.newUnsafeByteBuffer(maxIndices * 2);
        buffer = byteBuffer.asShortBuffer();
        buffer.flip();
        byteBuffer.flip();
    }

    /** @return the number of indices currently stored in this buffer */
    public int getNumIndices(){
        return empty ? 0 : buffer.limit();
    }

    /** @return the maximum number of indices this IndexArray can store. */
    public int getNumMaxIndices(){
        return empty ? 0 : buffer.capacity();
    }

    /**
     * <p>
     * Sets the indices of this IndexArray, discarding the old indices. The count must equal the number of indices to be copied to
     * this IndexArray.
     * </p>
     *
     * <p>
     * This can be called in between calls to {@link #bind()} and {@link #unbind()}. The index data will be updated instantly.
     * </p>
     * @param indices the vertex data
     * @param offset the offset to start copying the data from
     * @param count the number of shorts to copy
     */
    public void setIndices(short[] indices, int offset, int count){
        buffer.clear();
        buffer.put(indices, offset, count);
        buffer.flip();
        byteBuffer.position(0);
        byteBuffer.limit(count << 1);
    }

    public void setIndices(ShortBuffer indices){
        int pos = indices.position();
        buffer.clear();
        buffer.limit(indices.remaining());
        buffer.put(indices);
        buffer.flip();
        indices.position(pos);
        byteBuffer.position(0);
        byteBuffer.limit(buffer.limit() << 1);
    }

    @Override
    public void updateIndices(int targetOffset, short[] indices, int offset, int count){
        final int pos = byteBuffer.position();
        byteBuffer.position(targetOffset * 2);
        BufferUtils.copy(indices, offset, byteBuffer, count);
        byteBuffer.position(pos);
    }

    /**
     * <p>
     * Returns the underlying ShortBuffer. If you modify the buffer contents they wil be uploaded on the call to {@link #bind()}.
     * If you need immediate uploading use {@link #setIndices(short[], int, int)}.
     * </p>
     * @return the underlying short buffer.
     */
    public ShortBuffer getBuffer(){
        return buffer;
    }

    /** Binds this IndexArray for rendering with glDrawElements. */
    public void bind(){
    }

    /** Unbinds this IndexArray. */
    public void unbind(){
    }

    /** Invalidates the IndexArray so a new OpenGL buffer handle is created. Use this in case of a context loss. */
    public void invalidate(){
    }

    /** Disposes this IndexArray and all its associated OpenGL resources. */
    public void dispose(){
        BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
    }
}
