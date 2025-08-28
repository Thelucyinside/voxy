package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengles.GLES20.*;
import static org.lwjgl.opengles.GLES30.GL_RED_INTEGER;
import static org.lwjgl.opengles.GLES31.*;

public class GlBuffer extends TrackedObject {
    public final int id;
    private final long size;

    private static int COUNT;
    private static long TOTAL_SIZE;

    public GlBuffer(long size) {
        this(size, GL_STATIC_DRAW);
    }

    public GlBuffer(long size, int usage) {
        this.id = glGenBuffers();
        this.size = size;
        glBindBuffer(GL_ARRAY_BUFFER, this.id);
        glBufferData(GL_ARRAY_BUFFER, size, usage);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        this.zero();

        COUNT++;
        TOTAL_SIZE += size;
    }

    @Override
    public void free() {
        this.free0();
        glDeleteBuffers(this.id);

        COUNT--;
        TOTAL_SIZE -= this.size;
    }

    public long size() {
        return this.size;
    }

    public GlBuffer zero() {
        glBindBuffer(GL_ARRAY_BUFFER, this.id);
        ByteBuffer data = MemoryUtil.memCalloc((int)this.size);
        glBufferSubData(GL_ARRAY_BUFFER, 0, data);
        MemoryUtil.memFree(data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        return this;
    }

    public GlBuffer zeroRange(long offset, long size) {
        glBindBuffer(GL_ARRAY_BUFFER, this.id);
        ByteBuffer data = MemoryUtil.memCalloc((int)size);
        glBufferSubData(GL_ARRAY_BUFFER, offset, data);
        MemoryUtil.memFree(data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        return this;
    }

    public GlBuffer fill(int data) {
        glBindBuffer(GL_ARRAY_BUFFER, this.id);
        ByteBuffer buffer = MemoryUtil.memAlloc((int)this.size);
        for (int i = 0; i < this.size; i += 4) {
            buffer.putInt(i, data);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        MemoryUtil.memFree(buffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        return this;
    }

    public static int getCount() {
        return COUNT;
    }

    public static long getTotalSize() {
        return TOTAL_SIZE;
    }

    public GlBuffer name(String name) {
        return GlDebug.name(name, this);
    }

    private static final long SCRATCH = MemoryUtil.nmemAlloc(4);
}
