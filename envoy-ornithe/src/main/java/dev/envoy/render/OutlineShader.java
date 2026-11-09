package dev.envoy.render;

import dev.envoy.Envoy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class OutlineShader {
    private final int programId;
    private final int vertexShaderId;
    private final int fragmentShaderId;

    public OutlineShader(String vertexPath, String fragmentPath) throws IOException {
        int vertex = buildStage(vertexPath, GL20.GL_VERTEX_SHADER);
        int fragment;
        try { fragment = buildStage(fragmentPath, GL20.GL_FRAGMENT_SHADER); }
        catch (IOException failure) { GL20.glDeleteShader(vertex); throw failure; }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment); GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String details = GL20.glGetProgramInfoLog(program, 1024);
            GL20.glDeleteProgram(program); GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
            throw new IOException("Shader program link failed: " + details);
        }
        vertexShaderId = vertex; fragmentShaderId = fragment; programId = program;
    }

    public void bind() { GL20.glUseProgram(programId); }
    public void unbind() { GL20.glUseProgram(0); }
    public int getUniform(String name) { return GL20.glGetUniformLocation(programId, name); }

    public void cleanup() {
        unbind();
        GL20.glDetachShader(programId, vertexShaderId); GL20.glDetachShader(programId, fragmentShaderId);
        GL20.glDeleteShader(vertexShaderId); GL20.glDeleteShader(fragmentShaderId); GL20.glDeleteProgram(programId);
    }

    private static int buildStage(String assetPath, int kind) throws IOException {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, load(assetPath)); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE) return shader;
        String details = GL20.glGetShaderInfoLog(shader, 1024);
        GL20.glDeleteShader(shader);
        throw new IOException("Shader compilation failed: " + details);
    }

    private static String load(String assetPath) throws IOException {
        ResourceLocation location = new ResourceLocation(Envoy.MOD_ID, assetPath);
        InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            StringBuilder source = new StringBuilder();
            String line;
            while ((line = input.readLine()) != null) {
                if (source.length() > 0) source.append('\n');
                source.append(line);
            }
            return source.toString();
        }
    }
}
