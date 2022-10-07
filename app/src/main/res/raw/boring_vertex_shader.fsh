#version 320 es

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;
in vec4 aPosition;
in vec4 aTextureCoord;
out vec2 v_tex_coord;
void main() {
    gl_Position = uMVPMatrix * aPosition;
    v_tex_coord = (uSTMatrix * aTextureCoord).xy;
}