#version 450
#extension GL_EXT_nonuniform_qualifier : require

// luka: see terrain_fast.vert.

layout(constant_id = 0) const int TEXTURE_CAPACITY = 1024;
layout(set = 1, binding = 0) uniform sampler2D textures[TEXTURE_CAPACITY];

layout(location = 0) in vec4 vColor;
layout(location = 1) in vec2 vTexCoord;
layout(location = 2) flat in vec3 vFogColor;
layout(location = 3) flat in ivec2 vTexture;
layout(location = 4) flat in float vAlphaReference;

layout(location = 0) out vec4 outColor;

#ifdef NO_DISCARD
layout(early_fragment_tests) in;
#endif

void main() {
    vec4 texel = texture(textures[nonuniformEXT(vTexture.x)], vTexCoord);
#ifndef NO_DISCARD
    if (vTexture.y == 4) {
        if (texel.a <= vAlphaReference) discard;
    } else if (vTexture.y != 7) {
        bool pass;
        switch (vTexture.y) {
            case 0: pass = false; break;
            case 1: pass = texel.a < vAlphaReference; break;
            case 2: pass = texel.a == vAlphaReference; break;
            case 3: pass = texel.a <= vAlphaReference; break;
            case 5: pass = texel.a != vAlphaReference; break;
            case 6: pass = texel.a >= vAlphaReference; break;
            default: pass = true; break;
        }
        if (!pass) discard;
    }
#endif
    outColor = vec4(mix(vFogColor, texel.rgb * vColor.rgb, vColor.a), texel.a);
}
