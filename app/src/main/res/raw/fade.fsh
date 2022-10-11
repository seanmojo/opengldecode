#version 320 es
#extension GL_OES_EGL_image_external_essl3 : require
#define texture2D texture
#define gl_FragColor fragColor
#define c_offset vec2(0.00,0.00)

precision highp float;

in vec2 v_tex_coord;

out vec4 gl_FragColor;

uniform vec2 i_size;
uniform samplerExternalOES i_texture_1;
uniform samplerExternalOES i_texture_2;
uniform vec2 i_texture_size_1;
uniform vec2 i_texture_size_2;
uniform float i_transition_progress;

vec2 uvFromTextureSize(vec2 vTexCoord, vec2 size, vec2 textureSize) {
    vec2 uv = vTexCoord * 2. - 1.;
    uv.x *= min(1., (size.x * textureSize.y) / (size.y * textureSize.x));
    uv.y *= min(1., (size.y * textureSize.x) / (size.x * textureSize.y));
    uv = uv * .5 + .5;
    return uv;
}

void main() {
    vec2 uv1 = uvFromTextureSize(v_tex_coord, i_size, i_texture_size_1);
    vec2 uv2 = uvFromTextureSize(v_tex_coord, i_size, i_texture_size_2);

    uv1 += c_offset;

    gl_FragColor = mix(texture2D(i_texture_1, uv1), texture2D(i_texture_2, uv2), i_transition_progress);
}