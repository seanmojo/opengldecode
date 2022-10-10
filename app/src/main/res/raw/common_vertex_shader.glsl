#version 320 es

in vec2 a_Position;
in vec2 a_TexCoordinate;

out vec2 v_tex_coord;

void main()
{
    v_tex_coord = a_TexCoordinate;
    gl_Position = vec4(a_Position.x, a_Position.y, 0.0, 1.0);
}