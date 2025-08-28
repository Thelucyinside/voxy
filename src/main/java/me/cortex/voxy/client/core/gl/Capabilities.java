package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gl.shader.ShaderType;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.opengles.GLES31;
import org.lwjgl.opengles.GLES32;


import static org.lwjgl.opengles.GLES20.*;
import static org.lwjgl.opengles.GLES31.GL_MAX_SHADER_STORAGE_BLOCK_SIZE;

public class Capabilities {

    public static final Capabilities INSTANCE = new Capabilities();

    public final boolean repFragTest;
    public final boolean meshShaders;
    public final boolean INT64_t;
    public final long ssboMaxSize;
    public final boolean isMesa;
    public final boolean canQueryGpuMemory;
    public final long totalDedicatedMemory;//Bytes, dedicated memory
    public final long totalDynamicMemory;//Bytes, total allocation memory - dedicated memory
    public final boolean compute;
    public final boolean indirectParameters;
    public final boolean isIntel;
    public final boolean subgroup;

    public Capabilities() {
        var cap = GLES.getCapabilities();
        this.compute = cap.glDispatchComputeIndirect != 0;
        this.indirectParameters = false; // Not in GLES 3.2
        this.repFragTest = cap.GL_NV_representative_fragment_test;
        this.meshShaders = cap.GL_NV_mesh_shader;
        this.canQueryGpuMemory = false; // Not in GLES
        //this.INT64_t = cap.GL_ARB_gpu_shader_int64 || cap.GL_AMD_gpu_shader_int64;
        //The only reliable way to test for int64 support is to try compile a shader
        this.INT64_t = testShaderCompilesOk(ShaderType.COMPUTE, """
                #version 320 es
                #extension GL_OES_gpu_shader_int64 : require
                layout(local_size_x=32) in;
                void main() {
                    uint64_t a = 1234;
                }
                """);
        if (cap.GL_KHR_shader_subgroup) {
            this.subgroup = testShaderCompilesOk(ShaderType.COMPUTE, """
                #version 320 es
                #extension GL_KHR_shader_subgroup_basic : require
                #extension GL_KHR_shader_subgroup_arithmetic : require
                layout(local_size_x=32) in;
                void main() {
                    uint a = subgroupExclusiveAdd(gl_LocalInvocationIndex);
                }
                """);
        } else {
            this.subgroup = false;
        }

        long[] ssboSize = new long[1];
        GLES32.glGetInteger64v(GLES31.GL_MAX_SHADER_STORAGE_BLOCK_SIZE, ssboSize);
        this.ssboMaxSize = ssboSize[0];

        this.isMesa = glGetString(GL_VERSION).toLowerCase().contains("mesa");
        this.isIntel = glGetString(GL_VENDOR).toLowerCase().contains("intel");

        this.totalDedicatedMemory = -1;
        this.totalDynamicMemory = -1;
    }


    public static void init() {
    }

    private static boolean testShaderCompilesOk(ShaderType type, String src) {
        int shader = GLES20.glCreateShader(type.gl);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        int[] result = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result);
        GLES20.glDeleteShader(shader);

        return result[0] == GLES20.GL_TRUE;
    }

    public long getFreeDedicatedGpuMemory() {
        throw new IllegalStateException("Cannot query gpu memory in GLES");
    }

    //TODO: add gpu eviction tracking
}
