#version 320 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;
uniform samplerExternalOES sTexture;
in vec2 v_tex_coord; // the camera bg texture coordinates
out vec4 FragColor;

void main() {
    vec3 color = texture(sTexture, v_tex_coord).rgb;
    FragColor = vec4(1.0-color, 1.0);
}
