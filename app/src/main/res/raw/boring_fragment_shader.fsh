#version 320 es
#extension GL_OES_EGL_image_external : require
#define texture2D texture
#define gl_FragColor fragColor

precision highp float;

out vec4 gl_FragColor;
in vec2 v_tex_coord;
uniform samplerExternalOES sTexture;
void main() {
    gl_FragColor = texture2D(sTexture, v_tex_coord);
}