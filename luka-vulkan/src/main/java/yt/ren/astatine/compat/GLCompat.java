package yt.ren.astatine.compat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;
import yt.ren.astatine.Native;
import yt.ren.astatine.render.GL;
import yt.ren.astatine.render.GLCapabilities;
import yt.ren.astatine.render.GLState;

/**
 * OpenGL entry points for mods that call LWJGL directly. There is no OpenGL context under this renderer, so
 * {@link GLCompatPlugin} rewrites LWJGL's GL classes: a public static method here with the same name and descriptor
 * as a GL method replaces it, and every other GL method becomes a no-op returning zero, so no call can reach a missing
 * driver and abort the JVM. Custom GLSL shaders and raw framebuffers are therefore inert, not translated.
 *
 * <p>Everything here runs on the game thread, the same thread that writes the renderer's command stream.
 */
public final class GLCompat {
	private static final int STRIDE = 28;

	private GLCompat() {
	}

	// ---- capabilities and fixed state ----

	public static void glEnable(int capability) {
		GL.enable(capability);
	}

	public static void glDisable(int capability) {
		GL.disable(capability);
	}

	public static boolean glIsEnabled(int capability) {
		return GL.isEnabled(capability);
	}

	public static void glBlendEquation(int mode) {
		GL.blendEquation(mode == 0x8008); // GL_MAX; other equations are treated as add
	}

	public static void glBlendFunc(int source, int destination) {
		GL.blendFunc(source, destination, source, destination);
	}

	public static void glBlendFuncSeparate(int sourceColor, int destinationColor, int sourceAlpha, int destinationAlpha) {
		GL.blendFunc(sourceColor, destinationColor, sourceAlpha, destinationAlpha);
	}

	public static void glAlphaFunc(int function, float reference) {
		GL.alphaFunc(function, reference);
	}

	public static void glDepthMask(boolean mask) {
		GL.depthMask(mask);
	}

	public static void glDepthFunc(int function) {
		GL.depthFunc(function);
	}

	public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		GL.colorMask(red, green, blue, alpha);
	}

	public static void glCullFace(int face) {
		GL.cullFace(face);
	}

	public static void glPolygonOffset(float factor, float units) {
		GL.polygonOffset(factor, units);
	}

	public static void glShadeModel(int mode) {
		GL.shadeModel(mode);
	}

	public static void glViewport(int x, int y, int width, int height) {
		GL.viewport(x, y, width, height);
	}

	public static void glScissor(int x, int y, int width, int height) {
		GL.scissor(x, y, width, height);
	}

	public static void glLineWidth(float width) {
		GL.lineWidth(width);
	}

	public static void glClear(int mask) {
		GL.clear(mask);
	}

	public static void glClearColor(float red, float green, float blue, float alpha) {
		GL.clearColor(red, green, blue, alpha);
	}

	public static void glClearDepth(double depth) {
		GL.clearDepth((float) depth);
	}

	public static void glPushAttrib(int mask) {
		GL.pushAttributes(mask);
	}

	public static void glPopAttrib() {
		GL.popAttributes();
	}

	// ---- current vertex attributes ----

	public static void glColor4f(float red, float green, float blue, float alpha) {
		GL.color(red, green, blue, alpha);
	}

	public static void glColor3f(float red, float green, float blue) {
		GL.color(red, green, blue, 1f);
	}

	public static void glColor4d(double red, double green, double blue, double alpha) {
		GL.color((float) red, (float) green, (float) blue, (float) alpha);
	}

	public static void glColor3d(double red, double green, double blue) {
		GL.color((float) red, (float) green, (float) blue, 1f);
	}

	public static void glColor4ub(byte red, byte green, byte blue, byte alpha) {
		GL.color((red & 255) / 255f, (green & 255) / 255f, (blue & 255) / 255f, (alpha & 255) / 255f);
	}

	public static void glNormal3f(float x, float y, float z) {
		GL.normal(x, y, z);
	}

	public static void glTexCoord2f(float s, float t) {
		currentS = s;
		currentT = t;
		if (!inBegin) GL.texCoord(0, s, t);
	}

	public static void glTexCoord2d(double s, double t) {
		glTexCoord2f((float) s, (float) t);
	}

	public static void glMultiTexCoord2f(int unit, float s, float t) {
		GL.texCoord(unit & 7, s, t);
	}

	// ---- matrices ----

	public static void glMatrixMode(int mode) {
		GL.matrixMode(mode);
	}

	public static void glLoadIdentity() {
		GL.loadIdentity();
	}

	public static void glPushMatrix() {
		GL.pushMatrix();
	}

	public static void glPopMatrix() {
		GL.popMatrix();
	}

	public static void glTranslatef(float x, float y, float z) {
		GL.translate(x, y, z);
	}

	public static void glTranslated(double x, double y, double z) {
		GL.translate((float) x, (float) y, (float) z);
	}

	public static void glScalef(float x, float y, float z) {
		GL.scale(x, y, z);
	}

	public static void glScaled(double x, double y, double z) {
		GL.scale((float) x, (float) y, (float) z);
	}

	public static void glRotatef(float angle, float x, float y, float z) {
		GL.rotate(angle, x, y, z);
	}

	public static void glRotated(double angle, double x, double y, double z) {
		GL.rotate((float) angle, (float) x, (float) y, (float) z);
	}

	public static void glOrtho(double left, double right, double bottom, double top, double near, double far) {
		GL.ortho((float) left, (float) right, (float) bottom, (float) top, (float) near, (float) far);
	}

	public static void glMultMatrix(FloatBuffer matrix) {
		GL.multMatrix(MemoryUtil.memAddress(matrix));
	}

	public static void glMultMatrixf(FloatBuffer matrix) {
		GL.multMatrix(MemoryUtil.memAddress(matrix));
	}

	// ---- textures ----

	public static void glActiveTexture(int unit) {
		GL.activeTexture(unit - 0x84C0); // the renderer takes a plain unit index, GL passes GL_TEXTURE0 + index
	}

	public static void glBindTexture(int target, int name) {
		GL.bindTexture(TextureNames.handle(name));
	}

	public static void glTexParameteri(int target, int name, int value) {
		glTexParameterf(target, name, value);
	}

	public static void glTexParameterf(int target, int name, float value) {
		TEXTURES.computeIfAbsent(TextureNames.current(), key -> new Tex()).parameters.put(name, value);
		GL.textureParameter(name, value);
	}

	public static void glCopyTexSubImage2D(int target, int level, int destinationX, int destinationY, int x, int y, int width, int height) {
		Native.copyFromTarget(level, destinationX, destinationY, x, y, width, height);
	}

	// ---- queries ----

	public static int glGetError() {
		return 0;
	}

	public static String glGetString(int name) {
		return GLCapabilities.string(name);
	}

	public static int glGetInteger(int name) {
		switch (name) {
			case 0x8CA6: // GL_FRAMEBUFFER_BINDING / GL_DRAW_FRAMEBUFFER_BINDING
			case 0x8CAA: // GL_READ_FRAMEBUFFER_BINDING
				return currentFramebufferId();
			case 0x8069: // GL_TEXTURE_BINDING_2D
				return TextureNames.current();
			case 0x0BA0: // GL_MATRIX_MODE
				return GL.matrixModeValue();
			case 0x84E0: // GL_ACTIVE_TEXTURE
				return 0x84C0 + GL.activeUnit();
			case 0x0D33: // GL_MAX_TEXTURE_SIZE
				return 16384;
			// luka: mods read these to put state back afterwards, so answering 0 would restore a broken state
			case 0x80C9: // GL_BLEND_SRC_RGB
			case 0x0BE1: // GL_BLEND_SRC
				return GL.blendValue(0);
			case 0x80C8: // GL_BLEND_DST_RGB
			case 0x0BE0: // GL_BLEND_DST
				return GL.blendValue(1);
			case 0x80CB: // GL_BLEND_SRC_ALPHA
				return GL.blendValue(2);
			case 0x80CA: // GL_BLEND_DST_ALPHA
				return GL.blendValue(3);
			case 0x0BC1: // GL_ALPHA_TEST_FUNC
				return GL.alphaFunctionValue();
			case 0x0B74: // GL_DEPTH_FUNC
				return GL.depthFunctionValue();
			case 0x0B45: // GL_CULL_FACE_MODE
				return GL.cullFaceValue();
			default:
				return 0;
		}
	}

	public static void glGetInteger(int name, IntBuffer destination) {
		if (name == 0x0BA2) { // GL_VIEWPORT
			GLState.readViewport(MemoryUtil.memAddress(destination));
		} else {
			destination.put(destination.position(), glGetInteger(name));
		}
	}

	public static void glGetIntegerv(int name, IntBuffer destination) {
		glGetInteger(name, destination);
	}

	public static boolean glGetBoolean(int name) {
		switch (name) {
			case 0x0B72: // GL_DEPTH_WRITEMASK
				return GL.depthMaskValue();
			case 0x0C23: // GL_COLOR_WRITEMASK
				return true;
			default:
				return false;
		}
	}

	public static void glGetBoolean(int name, ByteBuffer destination) {
		byte value = (byte) (glGetBoolean(name) ? 1 : 0);
		int at = destination.position();
		int count = name == 0x0C23 ? 4 : 1;
		for (int i = 0; i < count; i++) destination.put(at + i, value);
	}

	public static float glGetFloat(int name) {
		if (name == 0x0BC2) return GL.alphaReferenceValue(); // GL_ALPHA_TEST_REF
		return name == 0x0B21 ? GL.lineWidthValue() : 0f;
	}

	public static void glGetFloat(int name, FloatBuffer destination) {
		switch (name) {
			case 0x0BA6: // GL_MODELVIEW_MATRIX
			case 0x0BA7: // GL_PROJECTION_MATRIX
			case 0x0BA8: // GL_TEXTURE_MATRIX
				GLState.readMatrix(name, MemoryUtil.memAddress(destination));
				break;
			case 0x0B00: { // GL_CURRENT_COLOR
				int at = destination.position();
				for (int i = 0; i < 4; i++) destination.put(at + i, GL.currentColor[i]);
				break;
			}
			default:
				destination.put(destination.position(), glGetFloat(name));
				break;
		}
	}

	public static void glGetFloatv(int name, FloatBuffer destination) {
		glGetFloat(name, destination);
	}

	// ---- textures and framebuffers created by mods ----

	private static final class Tex {
		int width, height;
		boolean floating;
		boolean ownedByFramebuffer;
		final Map<Integer, Float> parameters = new HashMap<>();
	}

	private static final class Fbo {
		long handle;
		int color;
		boolean depth;
	}

	private static final Map<Integer, Tex> TEXTURES = new HashMap<>();
	private static final Map<Integer, Fbo> FRAMEBUFFERS = new HashMap<>();
	private static final Map<Long, Integer> FRAMEBUFFER_IDS = new HashMap<>();
	private static int nextFramebuffer = 1;
	private static int nextRenderbuffer = 1;
	private static Fbo boundFbo;
	private static long mainFramebuffer;

	/** Gives a renderer framebuffer an OpenGL-style id so mods can save and restore it. */
	public static int registerFramebuffer(long handle) {
		Integer known = FRAMEBUFFER_IDS.get(handle);
		if (known != null) return known;
		if (mainFramebuffer == 0L) mainFramebuffer = handle; // the game creates its main render target first
		int id = nextFramebuffer++;
		Fbo fbo = new Fbo();
		fbo.handle = handle;
		FRAMEBUFFERS.put(id, fbo);
		FRAMEBUFFER_IDS.put(handle, id);
		return id;
	}

	private static int currentFramebufferId() {
		long handle = GL.framebufferValue();
		return handle == 0L ? 0 : registerFramebuffer(handle);
	}

	public static int glGenTextures() {
		int name = TextureNames.allocate();
		TextureNames.put(name, Native.textureCreate());
		return name;
	}

	public static void glDeleteTextures(int name) {
		Tex tex = TEXTURES.remove(name);
		if (tex == null || !tex.ownedByFramebuffer) {
			if (TextureNames.has(name)) Native.textureDetach(TextureNames.handle(name));
		}
		TextureNames.remove(name);
	}

	public static void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
		if (level != 0) return;
		int name = TextureNames.current();
		Tex tex = TEXTURES.computeIfAbsent(name, key -> new Tex());
		tex.width = width;
		tex.height = height;
		tex.floating = internalFormat == 0x881A; // GL_RGBA16F: storage comes with its framebuffer
		if (tex.floating) return;
		if (!TextureNames.has(name)) {
			TextureNames.put(name, Native.textureCreate());
			GL.bindTexture(TextureNames.handle(name));
		}
		long handle = TextureNames.handle(name);
		Native.textureAllocate(handle, width, height);
		if (pixels == null) return;
		ByteBuffer bgra = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
		for (int i = 0, at = pixels.position(), n = width * height; i < n; i++, at += 4) {
			byte a = pixels.get(at), b = pixels.get(at + 1), c = pixels.get(at + 2), d = pixels.get(at + 3);
			if (format == 0x1908) bgra.put(c).put(b).put(a).put(d); // GL_RGBA
			else bgra.put(a).put(b).put(c).put(d);
		}
		Native.textureUpload(handle, 0, 0, 0, width, height, MemoryUtil.memAddress0(bgra));
	}

	public static int glGenFramebuffers() {
		int id = nextFramebuffer++;
		FRAMEBUFFERS.put(id, new Fbo());
		return id;
	}

	public static void glDeleteFramebuffers(int id) {
		Fbo fbo = FRAMEBUFFERS.remove(id);
		if (fbo == null) return;
		if (fbo == boundFbo) glBindFramebuffer(0x8D40, 0);
		if (fbo.handle != 0L) {
			FRAMEBUFFER_IDS.remove(fbo.handle);
			Native.framebufferRelease(fbo.handle);
		}
		if (fbo.color != 0 && TEXTURES.containsKey(fbo.color)) TextureNames.remove(fbo.color);
	}

	/** Attachments are collected first and the renderer object is built once the framebuffer is complete. */
	private static void materialize(Fbo fbo) {
		if (fbo == null || fbo.handle != 0L || fbo.color == 0) return;
		Tex tex = TEXTURES.get(fbo.color);
		if (tex == null || tex.width <= 0) return;
		fbo.handle = tex.floating ? Native.framebufferCreateFloat(tex.width, tex.height) : Native.framebufferCreate(tex.width, tex.height, fbo.depth);
		tex.ownedByFramebuffer = true;
		long colorTexture = Native.framebufferTexture(fbo.handle);
		TextureNames.put(fbo.color, colorTexture);
		// the filter and wrap settings were made on the placeholder: without them the default mipmapped filter makes this texture incomplete
		GL.bindTexture(colorTexture);
		GL.textureParameter(0x2801, 0x2601); // GL_TEXTURE_MIN_FILTER = GL_LINEAR unless the mod chose otherwise below
		for (Map.Entry<Integer, Float> parameter : tex.parameters.entrySet()) GL.textureParameter(parameter.getKey(), parameter.getValue());
		GL.bindTexture(TextureNames.handle(TextureNames.current()));
		FRAMEBUFFER_IDS.put(fbo.handle, idOf(fbo));
	}

	private static int idOf(Fbo fbo) {
		for (Map.Entry<Integer, Fbo> entry : FRAMEBUFFERS.entrySet()) {
			if (entry.getValue() == fbo) return entry.getKey();
		}
		return 0;
	}

	public static void glBindFramebuffer(int target, int id) {
		materialize(boundFbo);
		if (id == 0) {
			boundFbo = null;
			GL.bindFramebuffer(0L);
			return;
		}
		Fbo fbo = FRAMEBUFFERS.get(id);
		if (fbo == null) return;
		boundFbo = fbo;
		materialize(fbo);
		if (fbo.handle != 0L) GL.bindFramebuffer(fbo.handle);
	}

	public static void glFramebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
		if (boundFbo != null && attachment == 0x8CE0) boundFbo.color = texture; // GL_COLOR_ATTACHMENT0
	}

	public static void glFramebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
		if (boundFbo != null && (attachment == 0x8D00 || attachment == 0x821A)) boundFbo.depth = true; // depth or depth-stencil
	}

	public static int glGenRenderbuffers() {
		return nextRenderbuffer++;
	}

	public static int glCheckFramebufferStatus(int target) {
		if (boundFbo != null) {
			materialize(boundFbo);
			if (boundFbo.handle != 0L) GL.bindFramebuffer(boundFbo.handle);
		}
		return 0x8CD5; // GL_FRAMEBUFFER_COMPLETE
	}

	// ---- pixel read-back (screen capture): pixel buffers are plain memory, reads complete immediately ----

	private static final Map<Integer, ByteBuffer> PIXEL_BUFFERS = new HashMap<>();
	private static int nextPixelBuffer = 1;
	private static int boundPackBuffer;
	private static int packAlignment = 4;

	public static int glGenBuffers() {
		return nextPixelBuffer++;
	}

	public static void glBindBuffer(int target, int buffer) {
		if (target == 0x88EB) boundPackBuffer = buffer; // GL_PIXEL_PACK_BUFFER
	}

	public static void glBufferData(int target, long size, int usage) {
		if (target == 0x88EB && boundPackBuffer != 0) {
			PIXEL_BUFFERS.put(boundPackBuffer, ByteBuffer.allocateDirect((int) size).order(ByteOrder.nativeOrder()));
		}
	}

	public static ByteBuffer glMapBuffer(int target, int access, long length, ByteBuffer old) {
		ByteBuffer buffer = target == 0x88EB ? PIXEL_BUFFERS.get(boundPackBuffer) : null;
		if (buffer != null) {
			buffer.clear();
			PendingRead read = PENDING_READS.remove(boundPackBuffer);
			if (read != null) Native.readbackFinish(read.ticket, read.format, read.alignment, MemoryUtil.memAddress0(buffer) + read.offset);
		}
		return buffer;
	}

	public static ByteBuffer glMapBuffer(int target, int access) {
		return glMapBuffer(target, access, 0L, null);
	}

	public static boolean glUnmapBuffer(int target) {
		return true;
	}

	public static void glDeleteBuffers(int buffer) {
		PIXEL_BUFFERS.remove(buffer);
		PENDING_READS.remove(buffer);
	}

	public static void glPixelStorei(int name, int value) {
		if (name == 0x0D05) packAlignment = Math.max(1, value); // GL_PACK_ALIGNMENT
	}

	public static void glReadPixels(int x, int y, int width, int height, int format, int type, long offset) {
		ByteBuffer buffer = PIXEL_BUFFERS.get(boundPackBuffer);
		if (buffer == null || offset + readBytes(width, height, format) > buffer.capacity()) return;
		long ticket = beginRead(x, y, width, height);
		if (ticket != 0L) PENDING_READS.put(boundPackBuffer, new PendingRead(ticket, format, packAlignment, offset));
	}

	public static void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
		if (readBytes(width, height, format) > pixels.remaining()) return;
		long ticket = beginRead(x, y, width, height);
		if (ticket != 0L) Native.readbackFinish(ticket, format, packAlignment, MemoryUtil.memAddress0(pixels) + pixels.position());
	}

	private static long beginRead(int x, int y, int width, int height) {
		long framebuffer = GL.framebufferValue();
		if (framebuffer == 0L) framebuffer = mainFramebuffer;
		if (framebuffer == 0L) return 0L;
		return Native.readbackBegin(Native.framebufferTexture(framebuffer), x, y, width, height);
	}

	private static long readBytes(int width, int height, int format) {
		int components = format == 0x1907 || format == 0x80E0 ? 3 : format == 0x1908 || format == 0x80E1 ? 4 : 1;
		long rowBytes = (long) width * components;
		return height * (rowBytes + (packAlignment - rowBytes % packAlignment) % packAlignment);
	}

	private record PendingRead(long ticket, int format, int alignment, long offset) {
	}

	private static final Map<Integer, PendingRead> PENDING_READS = new HashMap<>();

	// ---- GLSL programs: the known ones run as built-in Vulkan shaders, chosen from their source ----

	private static final int LOCATION_FIRST = 0, LOCATION_SAMPLER = 2;
	private static final Map<Integer, String> SHADER_SOURCES = new HashMap<>();
	private static final Map<Integer, List<Integer>> PROGRAM_SHADERS = new HashMap<>();
	private static final Map<Integer, Integer> PROGRAM_KINDS = new HashMap<>();
	private static int nextShader = 1;

	public static int glCreateShader(int type) {
		return nextShader++;
	}

	public static void glShaderSource(int shader, CharSequence source) {
		SHADER_SOURCES.put(shader, source.toString());
	}

	public static void glShaderSource(int shader, CharSequence... sources) {
		SHADER_SOURCES.put(shader, String.join("\n", sources));
	}

	public static int glGetShaderi(int shader, int name) {
		return 1;
	}

	public static int glCreateProgram() {
		int id = nextShader++;
		PROGRAM_SHADERS.put(id, new ArrayList<>());
		return id;
	}

	public static void glAttachShader(int program, int shader) {
		List<Integer> shaders = PROGRAM_SHADERS.get(program);
		if (shaders != null) shaders.add(shader);
	}

	public static void glLinkProgram(int program) {
		int kind = 0;
		List<Integer> shaders = PROGRAM_SHADERS.get(program);
		if (shaders != null) {
			for (int shader : shaders) {
				String source = SHADER_SOURCES.get(shader);
				if (source == null) continue;
				if (source.contains("lumaRange")) kind = 1;
				else if (source.contains("vec3(2.2)) * weight")) kind = 2;
				else if (source.contains("vec3(1.0 / 2.2)")) kind = 3;
			}
		}
		PROGRAM_KINDS.put(program, kind);
	}

	public static int glGetProgrami(int program, int name) {
		if (name == 0x8B82) { // GL_LINK_STATUS
			Integer kind = PROGRAM_KINDS.get(program);
			return kind != null && kind != 0 ? 1 : 0;
		}
		return 1;
	}

	public static String glGetProgramInfoLog(int program, int maxLength) {
		return "program is not one of the shaders this renderer provides";
	}

	public static void glUseProgram(int program) {
		Integer kind = PROGRAM_KINDS.get(program);
		GL.program(kind == null ? 0 : kind);
	}

	public static int glGetUniformLocation(int program, CharSequence name) {
		String text = name.toString();
		if (text.equals("weight") || text.equals("resolution")) return LOCATION_FIRST;
		return LOCATION_SAMPLER;
	}

	public static void glUniform1f(int location, float value) {
		if (location == LOCATION_FIRST) GL.programParameters(value, 0f, 0f);
	}

	public static void glUniform2f(int location, float x, float y) {
		if (location == LOCATION_FIRST) GL.programParameters(x, y, 0f);
	}

	// ---- immediate mode ----

	private static boolean inBegin;
	private static int beginMode;
	private static int vertices;
	private static float currentS;
	private static float currentT;
	private static ByteBuffer immediate = ByteBuffer.allocateDirect(1 << 16).order(ByteOrder.nativeOrder());
	private static int layout = -1;

	private static int layout() {
		if (layout < 0) {
			ByteBuffer scratch = ByteBuffer.allocateDirect(4 * 20).order(ByteOrder.nativeOrder());
			// usage, type, count, index, offset: position, uv, colour, normal (same layout as vanilla POSITION_TEX_COLOR_NORMAL)
			scratch.putInt(0).putInt(0).putInt(3).putInt(0).putInt(0);
			scratch.putInt(2).putInt(0).putInt(2).putInt(0).putInt(12);
			scratch.putInt(1).putInt(1).putInt(4).putInt(0).putInt(20);
			scratch.putInt(3).putInt(2).putInt(3).putInt(0).putInt(24);
			layout = Native.registerLayout(MemoryUtil.memAddress0(scratch), 4, STRIDE);
		}
		return layout;
	}

	public static void glBegin(int mode) {
		inBegin = true;
		beginMode = mode;
		vertices = 0;
		immediate.clear();
	}

	public static void glEnd() {
		inBegin = false;
		if (vertices == 0) return;
		long source = MemoryUtil.memAddress0(immediate);
		if (!captureDraw(layout(), beginMode, source, vertices)) {
			Native.draw(layout(), beginMode, source, vertices);
		}
		vertices = 0;
	}

	private static void vertex(float x, float y, float z) {
		if (!inBegin) return;
		if (immediate.remaining() < STRIDE) {
			ByteBuffer larger = ByteBuffer.allocateDirect(immediate.capacity() * 2).order(ByteOrder.nativeOrder());
			immediate.flip();
			larger.put(immediate);
			immediate = larger;
		}
		float[] color = GL.currentColor;
		float[] normal = GL.currentNormal;
		immediate.putFloat(x).putFloat(y).putFloat(z).putFloat(currentS).putFloat(currentT);
		immediate.put(unit(color[0])).put(unit(color[1])).put(unit(color[2])).put(unit(color[3]));
		immediate.put((byte) (normal[0] * 127f)).put((byte) (normal[1] * 127f)).put((byte) (normal[2] * 127f)).put((byte) 0);
		vertices++;
	}

	private static byte unit(float value) {
		return (byte) Math.max(0, Math.min(255, (int) (value * 255f + 0.5f)));
	}

	public static void glVertex2f(float x, float y) {
		vertex(x, y, 0f);
	}

	public static void glVertex2d(double x, double y) {
		vertex((float) x, (float) y, 0f);
	}

	public static void glVertex2i(int x, int y) {
		vertex(x, y, 0f);
	}

	public static void glVertex3f(float x, float y, float z) {
		vertex(x, y, z);
	}

	public static void glVertex3d(double x, double y, double z) {
		vertex((float) x, (float) y, (float) z);
	}

	public static void glVertex3i(int x, int y, int z) {
		vertex(x, y, z);
	}

	// ---- display lists: geometry only; state calls made while compiling apply immediately ----

	private static final Map<Integer, List<long[]>> LISTS = new HashMap<>();
	private static int nextList = 1;
	private static List<long[]> compiling;
	private static int compilingId;
	private static boolean compileAndExecute;

	public static int glGenLists(int range) {
		int first = nextList;
		nextList += Math.max(1, range);
		return first;
	}

	public static void glNewList(int list, int mode) {
		release(list);
		compiling = new ArrayList<>(2);
		compilingId = list;
		compileAndExecute = mode == 0x1301; // GL_COMPILE_AND_EXECUTE
	}

	public static void glEndList() {
		if (compiling == null) return;
		LISTS.put(compilingId, compiling);
		compiling = null;
		if (compileAndExecute) glCallList(compilingId);
	}

	public static void glCallList(int list) {
		List<long[]> draws = LISTS.get(list);
		if (draws == null) return;
		for (int i = 0, n = draws.size(); i < n; i++) {
			long[] draw = draws.get(i);
			Native.drawMesh(draw[0], (int) draw[1]);
		}
	}

	public static void glDeleteLists(int list, int range) {
		for (int i = 0; i < range; i++) release(list + i);
	}

	private static void release(int list) {
		List<long[]> draws = LISTS.remove(list);
		if (draws == null) return;
		for (long[] draw : draws) GL.releaseMesh(draw[0]);
	}

	/** While a display list is being compiled, stores the geometry in it and returns true. */
	public static boolean isCompiling() {
		return compiling != null;
	}

	public static boolean captureDraw(int layout, int mode, long source, int count) {
		if (compiling == null) return false;
		compiling.add(new long[]{Native.uploadMesh(layout, source, count), mode});
		return true;
	}
}
