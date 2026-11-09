#version 450
#extension GL_GOOGLE_include_directive : require
#ifdef BINDLESS
#extension GL_EXT_nonuniform_qualifier : require
#endif

// Vulkan versions of the GLSL programs used by luka's mods, selected by GLCompat from the program's source text.
// PROGRAM 1: entity outline. 2: linear-light accumulation. 3: accumulation resolve.
// Uniforms arrive in environment.alphaReference.yzw.

#include "fixed_function.glsl"

#ifdef BINDLESS
layout(constant_id = 0) const int TEXTURE_CAPACITY = 1024;
layout(set = 1, binding = 0) uniform sampler2D textures[TEXTURE_CAPACITY];
#define SAMPLER0 textures[nonuniformEXT(vTextures.x)]
#else
layout(set = 1, binding = 0) uniform sampler2D texture0;
#define SAMPLER0 texture0
#endif

layout(location = 0) in vec4 vColor;
layout(location = 1) in vec4 vTexCoord0;
layout(location = 2) in vec4 vTexCoord1;
layout(location = 3) in vec3 vEye;
layout(location = 4) flat in ivec4 vTextures;

layout(location = 0) out vec4 outColor;

#if PROGRAM == 3
// luka: FXAA on the final copy to the screen. The main image is sampled with nearest filtering, so the bilinear taps are done here.
vec3 tap(vec2 uv, vec2 texel) {
    vec2 position = uv / texel - 0.5;
    vec2 cell = floor(position);
    vec2 f = position - cell;
    vec2 base = (cell + 0.5) * texel;
    vec3 a = texture(SAMPLER0, base).rgb;
    vec3 b = texture(SAMPLER0, base + vec2(texel.x, 0.0)).rgb;
    vec3 c = texture(SAMPLER0, base + vec2(0.0, texel.y)).rgb;
    vec3 d = texture(SAMPLER0, base + texel).rgb;
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec3 fxaa(vec2 uv, vec2 texel) {
    const vec3 weights = vec3(0.299, 0.587, 0.114);
    vec3 m = texture(SAMPLER0, uv).rgb;
    float lumaM = dot(m, weights);
    float lumaNW = dot(texture(SAMPLER0, uv + vec2(-texel.x, -texel.y)).rgb, weights);
    float lumaNE = dot(texture(SAMPLER0, uv + vec2(texel.x, -texel.y)).rgb, weights);
    float lumaSW = dot(texture(SAMPLER0, uv + vec2(-texel.x, texel.y)).rgb, weights);
    float lumaSE = dot(texture(SAMPLER0, uv + vec2(texel.x, texel.y)).rgb, weights);
    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));
    if (lumaMax - lumaMin < max(0.03, lumaMax * 0.125)) return m;
    vec2 dir = vec2(-((lumaNW + lumaNE) - (lumaSW + lumaSE)), (lumaNW + lumaSW) - (lumaNE + lumaSE));
    float reduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.03125, 0.0078125);
    float scale = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduce);
    dir = clamp(dir * scale, vec2(-8.0), vec2(8.0)) * texel;
    vec3 near = 0.5 * (tap(uv + dir * (1.0 / 3.0 - 0.5), texel) + tap(uv + dir * (2.0 / 3.0 - 0.5), texel));
    vec3 far = near * 0.5 + 0.25 * (tap(uv - dir * 0.5, texel) + tap(uv + dir * 0.5, texel));
    float lumaFar = dot(far, weights);
    return lumaFar < lumaMin || lumaFar > lumaMax ? near : far;
}
#endif

void main() {
    environmentIndex = vTextures.w;
    vec2 uv = vTexCoord0.xy / vTexCoord0.w;
#if PROGRAM == 1
    vec2 texelSize = 1.0 / environment.alphaReference.yz;
    vec2 safeUV = clamp(uv, texelSize, 1.0 - texelSize);
    vec4 tl = texture(SAMPLER0, safeUV + vec2(-texelSize.x, -texelSize.y));
    vec4 t = texture(SAMPLER0, safeUV + vec2(0.0, -texelSize.y));
    vec4 tr = texture(SAMPLER0, safeUV + vec2(texelSize.x, -texelSize.y));
    vec4 l = texture(SAMPLER0, safeUV + vec2(-texelSize.x, 0.0));
    vec4 c = texture(SAMPLER0, safeUV);
    vec4 r = texture(SAMPLER0, safeUV + vec2(texelSize.x, 0.0));
    vec4 bl = texture(SAMPLER0, safeUV + vec2(-texelSize.x, texelSize.y));
    vec4 b = texture(SAMPLER0, safeUV + vec2(0.0, texelSize.y));
    vec4 br = texture(SAMPLER0, safeUV + vec2(texelSize.x, texelSize.y));
    vec3 weights = vec3(0.299, 0.587, 0.114);
    float lumTL = dot(tl.rgb, weights);
    float lumT = dot(t.rgb, weights);
    float lumTR = dot(tr.rgb, weights);
    float lumL = dot(l.rgb, weights);
    float lumC = dot(c.rgb, weights);
    float lumR = dot(r.rgb, weights);
    float lumBL = dot(bl.rgb, weights);
    float lumB = dot(b.rgb, weights);
    float lumBR = dot(br.rgb, weights);
    float gx = -lumTL + lumTR - 2.0 * lumL + 2.0 * lumR - lumBL + lumBR;
    float gy = -lumTL - 2.0 * lumT - lumTR + lumBL + 2.0 * lumB + lumBR;
    float edge = sqrt(gx * gx + gy * gy);
    float alpha = pow(smoothstep(0.15, 0.4, edge), 0.85);
    vec4 baseColor = vec4(c.rgb, alpha);
    float lumaMin = min(lumC, min(min(min(lumTL, lumT), min(lumTR, lumL)), min(min(lumR, lumBL), min(lumB, lumBR))));
    float lumaMax = max(lumC, max(max(max(lumTL, lumT), max(lumTR, lumL)), max(max(lumR, lumBL), max(lumB, lumBR))));
    if (lumaMax - lumaMin < 0.1) {
        outColor = baseColor;
        return;
    }
    vec2 dir;
    dir.x = -((lumTL + lumL + lumBL) - (lumTR + lumR + lumBR));
    dir.y = ((lumTL + lumT + lumTR) - (lumBL + lumB + lumBR));
    float dirReduce = max((lumTL + lumT + lumTR + lumL + lumC + lumR + lumBL + lumB + lumBR) / 9.0 * 0.25, 0.001);
    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = clamp(dir * rcpDirMin * texelSize * 0.5, vec2(-8.0), vec2(8.0));
    vec4 result1 = texture(SAMPLER0, safeUV + dir * (1.0 / 3.0 - 0.5));
    vec4 result2 = texture(SAMPLER0, safeUV + dir * (2.0 / 3.0 - 0.5));
    vec4 fxaaColor = (result1 + result2) * 0.5;
    outColor = vec4(mix(fxaaColor.rgb, baseColor.rgb, baseColor.a * 0.6), baseColor.a);
#elif PROGRAM == 2
    float weight = environment.alphaReference.y;
    vec3 c = texture(SAMPLER0, uv).rgb;
    outColor = vec4(pow(c, vec3(2.2)) * weight, weight);
#else
    if (environment.alphaReference.y == 7.0) {
        outColor = vec4(fxaa(uv, 1.0 / environment.alphaReference.zw), 1.0);
        return;
    }
    vec3 c = texture(SAMPLER0, uv).rgb;
    outColor = vec4(pow(c, vec3(1.0 / 2.2)), 1.0);
#endif
}
