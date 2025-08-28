package me.cortex.voxy.client.core.gl.shader;

import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gl.GlDebug;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.ThreadUtils;
import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.lwjgl.opengles.GLES20.glDeleteProgram;
import static org.lwjgl.opengles.GLES20.glUseProgram;

public class Shader extends TrackedObject {
    private final int id;
    Shader(int program) {
        id = program;
    }

    public int id() {
        return this.id;
    }

    public void bind() {
        glUseProgram(this.id);
    }

    public void free() {
        super.free0();
        glDeleteProgram(this.id);
    }


    public Shader name(String name) {
        return GlDebug.name(name, this);
    }


    public static Builder<Shader> make(IShaderProcessor... processor) {
        return makeInternal((a,b)->new Shader(b), processor);
    }

    public static Builder<AutoBindingShader> makeAuto(IShaderProcessor... processor) {
        return makeInternal(AutoBindingShader::new, processor);
    }



    static <T extends Shader> Builder<T> makeInternal(Builder.IShaderObjectConstructor<T> constructor, IShaderProcessor[] processors) {
        List<IShaderProcessor> aa = new ArrayList<>(List.of(processors));
        Collections.reverse(aa);
        IShaderProcessor applicator = (type,source)->source;
        for (IShaderProcessor processor : processors) {
            IShaderProcessor finalApplicator = applicator;
            applicator = (type, source) -> finalApplicator.process(type, processor.process(type, source));
        }
        return new Builder<>(constructor, applicator);
    }

    public static class Builder <T extends Shader> {
        protected interface IShaderObjectConstructor <J extends Shader> {
            J make(Builder<J> builder, int program);
        }
        final Map<String, String> defines = new HashMap<>();
        private final Map<ShaderType, String> sources = new HashMap<>();
        private final IShaderProcessor processor;
        private final IShaderObjectConstructor<T> constructor;
        private Builder(IShaderObjectConstructor<T> constructor, IShaderProcessor processor) {
            this.constructor = constructor;
            this.processor = processor;
        }

        public Builder<T> clone() {
            var clone = new Builder<>(this.constructor, this.processor);
            clone.defines.putAll(this.defines);
            clone.sources.putAll(this.sources);
            return clone;
        }

        public Builder<T> define(String name) {
            this.defines.put(name, "");
            return this;
        }

        //Useful for inline setting (such as debug)
        public Builder<T> defineIf(String name, boolean condition) {
            if (condition) {
                this.defines.put(name, "");
            }
            return this;
        }

        public Builder<T> defineIf(String name, boolean condition, int value) {
            if (condition) {
                this.defines.put(name, Integer.toString(value));
            }
            return this;
        }

        public Builder<T> define(String name, int value) {
            this.defines.put(name, Integer.toString(value));
            return this;
        }

        public Builder<T> define(String name, String value) {
            this.defines.put(name, value);
            return this;
        }

        public Builder<T> add(ShaderType type, String id) {
            this.addSource(type, ShaderLoader.parse(id));
            return this;
        }

        public Builder<T> addSource(ShaderType type, String source) {
            this.sources.put(type, this.processor.process(type, source));
            return this;
        }


        private int compileToProgram() {
            int program = GLES20.glCreateProgram();
            int[] shaders = new int[this.sources.size()];
            {
                String defs = this.defines.entrySet().stream().map(a->"#define " + a.getKey() + " " + a.getValue() + "\n").collect(Collectors.joining());
                int i = 0;
                for (var entry : this.sources.entrySet()) {
                    String src = entry.getValue();

                    //Inject defines
                    src = src.substring(0, src.indexOf('\n')+1) +
                            defs
                            + src.substring(src.indexOf('\n')+1);

                    shaders[i++] = createShader(entry.getKey(), src);
                }
            }

            for (int i : shaders) {
                GLES20.glAttachShader(program, i);
            }
            GLES20.glLinkProgram(program);
            for (int i : shaders) {
                GLES20.glDetachShader(program, i);
                GLES20.glDeleteShader(i);
            }
            printProgramLinkLog(program);
            verifyProgramLinked(program);
            return program;
        }

        public T compile() {
            this.defineIf("IS_INTEL", Capabilities.INSTANCE.isIntel);
            this.defineIf("IS_WINDOWS", ThreadUtils.isWindows);
            return this.constructor.make(this, this.compileToProgram());
        }

        private static void printProgramLinkLog(int program) {
            String log = GLES20.glGetProgramInfoLog(program);

            if (!log.isEmpty()) {
                Logger.error(log);
            }
        }

        private static void verifyProgramLinked(int program) {
            int[] result = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result);

            if (result[0] != GLES20.GL_TRUE) {
                throw new RuntimeException("Shader program linking failed, see log for details");
            }
        }

        private static int createShader(ShaderType type, String src) {
            int shader = GLES20.glCreateShader(type.gl);
            {//https://github.com/CaffeineMC/sodium/blob/fc42a7b19836c98a35df46e63303608de0587ab6/src/main/java/me/jellysquid/mods/sodium/client/gl/shader/ShaderWorkarounds.java
                long ptr = MemoryUtil.memAddress(MemoryUtil.memUTF8(src, true));
                try (var stack = MemoryStack.stackPush()) {
                    GLES20.nglShaderSource(shader, 1, stack.pointers(ptr).address0(), 0);
                }
                MemoryUtil.nmemFree(ptr);
            }
            GLES20.glCompileShader(shader);
            String log = GLES20.glGetShaderInfoLog(shader);

            if (!log.isEmpty()) {
                Logger.warn(log);
            }

            int[] result = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result);

            if (result[0] != GLES20.GL_TRUE) {
                GLES20.glDeleteShader(shader);
                try {
                    Files.writeString(Path.of("SHADER_DUMP.txt"), src);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                throw new RuntimeException("Shader compilation failed of type " + type.name() + ", see log for details, dumped shader");
            }

            return shader;
        }
    }
}
