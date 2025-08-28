package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;

import static org.lwjgl.opengles.GLES20.*;
import static org.lwjgl.opengles.GLES30.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengles.GLES30.GL_R32F;
import static org.lwjgl.opengles.GLES30.glTexStorage2D;
import static org.lwjgl.opengles.GLES30.GL_RGBA8;
import static org.lwjgl.opengles.GLES30.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengles.GLES30.GL_DEPTH_COMPONENT32F;


public class GlTexture extends TrackedObject {
    public final int id;
    public final int target;
    private int format;
    private int width;
    private int height;
    private int levels;
    private boolean hasAllocated;

    private static int COUNT;
    private static long ESTIMATED_TOTAL_SIZE;

    public GlTexture() {
        this(GL_TEXTURE_2D);
    }

    public GlTexture(int type) {
        this.id = glGenTextures();
        this.target = type;
        COUNT++;
    }

    private GlTexture(int type, boolean useGenTypes) {
        if (useGenTypes) {
            this.id = glGenTextures();
        } else {
            this.id = glGenTextures();
        }
        this.target = type;
        COUNT++;
    }

    public GlTexture store(int format, int levels, int width, int height) {
        if (this.hasAllocated) {
            throw new IllegalStateException("Texture already allocated");
        }
        this.hasAllocated = true;

        this.format = format;
        if (this.target == GL_TEXTURE_2D) {
            glBindTexture(this.target, this.id);
            glTexStorage2D(this.target, levels, format, width, height);
            glBindTexture(this.target, 0);
            this.width = width;
            this.height = height;
            this.levels = levels;
        } else {
            throw new IllegalStateException("Unknown texture type");
        }
        ESTIMATED_TOTAL_SIZE += this.getEstimatedSize();
        return this;
    }

    public GlTexture createView() {
        throw new UnsupportedOperationException("Texture views not supported in GLES");
    }

    @Override
    public void free() {
        if (this.hasAllocated) {
            ESTIMATED_TOTAL_SIZE -= this.getEstimatedSize();
        }
        COUNT--;
        this.hasAllocated = false;
        super.free0();
        glDeleteTextures(this.id);
    }

    public GlTexture name(String name) {
        this.assertAllocated();
        return GlDebug.name(name, this);
    }

    public int getWidth() {
        this.assertAllocated();
        return this.width;
    }

    public int getHeight() {
        this.assertAllocated();
        return this.height;
    }

    public int getLevels() {
        this.assertAllocated();
        return this.levels;
    }

    private long getEstimatedSize() {
        this.assertAllocated();
        long elemSize = switch (this.format) {
            case GL_RGBA8, GL_DEPTH24_STENCIL8, GL_R32F, GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT32F -> 4;
            default -> throw new IllegalStateException("Unknown element size for format " + this.format);
        };

        long size = 0;
        for (int lvl = 0; lvl < this.levels; lvl++) {
            size += Math.max((((long)this.width)>>lvl), 1) * Math.max((((long)this.height)>>lvl), 1) * elemSize;
        }
        return size;
    }

    public void assertAllocated() {
        if (!this.hasAllocated) {
            throw new IllegalStateException("Texture not yet allocated");
        }
    }


    public static int getCount() {
        return COUNT;
    }

    public static long getEstimatedTotalSize() {
        return ESTIMATED_TOTAL_SIZE;
    }
}
