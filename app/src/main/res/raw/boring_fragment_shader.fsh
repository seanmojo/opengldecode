#version 320 es
#extension GL_OES_EGL_image_external_essl3 : require

#define texture2D texture
#define gl_FragColor fragColor

precision highp float;

uniform samplerExternalOES u_texture;

in vec2 v_tex_coord;

out vec4 gl_FragColor;

void main()
{
    gl_FragColor = texture2D(u_texture, v_tex_coord);
}