#version 450
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_nonuniform_qualifier : require

// luka: the terrain path without the general fixed-function fragment shader. Light and fog are resolved per vertex, the
// way OpenGL's own fixed-function fog always worked, so the fragment shader only samples the atlas.

invariant gl_Position;

#include "fixed_function.glsl"

layout(constant_id = 0) const int TEXTURE_CAPACITY = 1024;
layout(set = 1, binding = 0) uniform sampler2D textures[TEXTURE_CAPACITY];

layout(location = 0) in uvec4 inVertex;

layout(location = 0) out vec4 vColor; // rgb is vertex colour times light, a is the fog factor
layout(location = 1) out vec2 vTexCoord;
layout(location = 2) flat out vec3 vFogColor;
layout(location = 3) flat out ivec2 vTexture; // atlas index, alpha function
layout(location = 4) flat out float vAlphaReference;

const float POSITION_SCALE = 1.0 / 1024.0;
const float POSITION_OFFSET = 8.0;
const float UV_SCALE = 1.0 / 16384.0;
const float UV_INSET = 0.75;

void main() {
    uvec3 position = uvec3(inVertex.x, inVertex.x >> 15, inVertex.y) & 0x7FFFu;
    uvec2 uv = uvec2(inVertex.y >> 15, inVertex.z) & 0x7FFFu;
    vec2 bias = mix(vec2(-UV_INSET), vec2(UV_INSET), equal(uv >> 14u, uvec2(1u)));
    vec2 texCoord = (vec2(uv & 0x3FFFu) + bias) * UV_SCALE;
    vec2 light = vec2(uvec2(inVertex.z >> 15, inVertex.z >> 21) & 63u) * 4.0;
    uint slot = (inVertex.x >> 30) | ((inVertex.y >> 30) << 2) | (((inVertex.z >> 27) & 31u) << 4) | ((inVertex.w >> 24) << 9);
    uvec4 section = sections[uint(gl_InstanceIndex) + slot];
    Draw draw = draws[section.w];
    environmentIndex = draw.textures.w;
    vec3 local = vec3(position) * POSITION_SCALE - POSITION_OFFSET;
    vec4 eye = draw.modelView * vec4(local + uintBitsToFloat(section.xyz), 1.0);
    gl_Position = environment.projection * eye;

    int flags = environment.flags.x;
    int textureFlags = draw.textures.z;
    vec3 color = unpackUnorm4x8(inVertex.w).rgb;
    if ((textureFlags & TEXTURE1) != 0) {
        vec4 lightCoord = environment.textureMatrix1 * vec4(light, 0.0, 1.0);
        vec2 lightUv = lightCoord.xy / lightCoord.w;
        if ((textureFlags & TEXTURE1_CLAMP_S) != 0) lightUv.x = clamp(lightUv.x, 0.0, 1.0);
        if ((textureFlags & TEXTURE1_CLAMP_T) != 0) lightUv.y = clamp(lightUv.y, 0.0, 1.0);
        color *= textureLod(textures[nonuniformEXT(draw.textures.y)], lightUv, 0.0).rgb;
    }

    float fog = 1.0;
    if ((flags & FOG) != 0) {
        vec4 parameters = environment.fogParams;
        float distance = (flags & FOG_RADIAL) != 0 ? length(eye.xyz) : abs(eye.z);
        switch (environment.flags.z) {
            case FOG_LINEAR: fog = (parameters.y - distance) * parameters.w; break;
            case FOG_EXP: fog = exp(-parameters.z * distance); break;
            default: fog = exp(-(parameters.z * distance) * (parameters.z * distance)); break;
        }
        fog = clamp(fog, 0.0, 1.0);
    }

    vColor = vec4(color, fog);
    vec4 atlasCoord = environment.textureMatrix0 * vec4(texCoord, 0.0, 1.0);
    vTexCoord = atlasCoord.xy / atlasCoord.w;
    vFogColor = environment.fogColor.rgb;
    vTexture = ivec2(draw.textures.x, (flags & ALPHA_TEST) != 0 ? environment.flags.y : 7);
    vAlphaReference = environment.alphaReference.x;
}
