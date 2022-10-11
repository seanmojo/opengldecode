#version 320 es
#extension GL_OES_EGL_image_external_essl3 : require

#define texture2D texture
#define gl_FragColor fragColor

precision highp float;

uniform float i_transition_progress;
uniform vec2 i_size;
uniform samplerExternalOES i_texture_1;
uniform samplerExternalOES i_texture_2;
uniform vec2 i_texture_size_1;
uniform vec2 i_texture_size_2;
uniform float i_media_content_mode_1; // FIT mode = 0, FILL mode = 1
uniform float i_media_content_mode_2;
uniform samplerExternalOES u_texture;

in vec2 v_tex_coord;
out vec4 gl_FragColor;

#define NUM_OCTAVES 6

#define c_fbmNoiseScale 1.3
#define c_fbmNoiseScale2 1.5
#define c_amp 5.75
#define c_shred 2.87
#define c_shred2 3.48
#define c_innerShred 0.38
#define c_step 12.0
#define c_darken 0.25
#define c_scale 2.4
#define c_rotate 1.57

// ----------- uvs -----------
vec2 uvFromTextureSize(vec2 uv, vec2 textureSize, vec2 viewportSize) {
    uv = uv * 2. - 1.;
    uv.x *= min(1., (viewportSize.x * textureSize.y) / (viewportSize.y * textureSize.x));
    uv.y *= min(1., (viewportSize.y * textureSize.x) / (viewportSize.x * textureSize.y));
    uv = uv * .5 + .5;
    return uv;
}

float random(float n){return fract(sin(n) * 43758.5453123);}

vec2 transformUV(vec2 uv, float aspect, vec2 center, vec2 translate, vec2 scale, float rotate) {
    vec3 st = vec3(uv, 1.0);

    mat3 applyOriginMatrix = mat3(
    1.0, 0.0, -center.x,
    0.0, 1.0, -center.y,
    0.0, 0.0, 1.0
    );
    mat3 restoreOriginMatrix = mat3(
    1.0, 0.0, center.x,
    0.0, 1.0, center.y,
    0.0, 0.0, 1.0
    );

    mat3 translateMatrix = mat3(
    1.0, 0.0, -translate.x,
    0.0, 1.0, -translate.y,
    0.0, 0.0, 1.0
    );

    mat3 rotateMatrix = mat3(
    cos(rotate), sin(rotate), 0.0,
    -sin(rotate), cos(rotate), 0.0,
    0.0, 0.0, 1.0);

    mat3 scaleMatrix = mat3(
    1.0 / scale.x, 0.0, 0.0,
    0.0, 1.0 / scale.y, 0.0,
    0.0, 0.0, 1.0
    );

    st = st * translateMatrix;

    // Rotation needs origin applied + aspect ratio corrected
    st = st * applyOriginMatrix;
    st.x *= aspect;
    st = st * rotateMatrix;
    st.x /= aspect;
    st = st * restoreOriginMatrix;

    // Scale needs origin applied
    st = st * applyOriginMatrix;
    st = st * scaleMatrix;
    st = st * restoreOriginMatrix;

    return st.xy;
}

// ----------- easings -----------
float map(float oldVal, float oldMin, float oldMax, float newMin, float newMax) {
    float oldDiff = oldMax - oldMin;
    float newDiff = newMax - newMin;
    return (((oldVal - oldMin) * newDiff) / oldDiff) + newMin;
}

float cmap(float oldValue, float oldMin, float oldMax, float newMin, float newMax) {
    return clamp(map(oldValue, oldMin, oldMax, newMin, newMax), min(newMax, newMin), max(newMin, newMax));
}

vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec2 mod289(vec2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec3 permute(vec3 x) { return mod289(((x*34.0)+1.0)*x); }

float snoise(vec2 v) {
    const vec4 C = vec4(0.211324865405187,  // (3.0-sqrt(3.0))/6.0
    0.366025403784439,  // 0.5*(sqrt(3.0)-1.0)
    -0.577350269189626,  // -1.0 + 2.0 * C.x
    0.024390243902439); // 1.0 / 41.0
    vec2 i  = floor(v + dot(v, C.yy) );
    vec2 x0 = v -   i + dot(i, C.xx);
    vec2 i1;
    i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod289(i); // Avoid truncation effects in permutation
    vec3 p = permute( permute( i.y + vec3(0.0, i1.y, 1.0 ))
    + i.x + vec3(0.0, i1.x, 1.0 ));

    vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
    m = m*m ;
    m = m*m ;
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );
    vec3 g;
    g.x  = a0.x  * x0.x  + h.x  * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

float fbm(vec2 x) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100);
    // Rotate to reduce axial bias

    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.50));
    for (int i = 0; i < NUM_OCTAVES; ++i) {
        v += a * snoise(x);
        x = rot * x * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

void main() {
    float ratio = i_size.x / i_size.y;
    vec2 uv1 = uvFromTextureSize(v_tex_coord, i_texture_size_1, i_size);
    vec2 uv2 = uvFromTextureSize(v_tex_coord, i_texture_size_2, i_size);

    vec3 texel1 = texture2D(i_texture_1, uv1).rgb;
    vec3 texel2 = texture2D(i_texture_2, uv2).rgb;
    vec3 texel3 = texture2D(i_texture_2, transformUV(uv2, i_size.x/i_size.y, vec2(.5, .5), vec2(0.), vec2(c_scale), c_rotate)).rgb;
    texel3 *= vec3(1. - c_darken);

    vec2 uv = v_tex_coord * vec2(ratio, 1.);

    float progress = cmap(i_transition_progress, 0., 1., 1.1, -.2);
    progress = ceil(progress * c_step) / c_step;

    // main rip
    float fbmNoise = fbm(uv * c_fbmNoiseScale);
    float thres = 1. - fbmNoise / c_amp;
    float ripThres = (thres - progress);

    // inner rip
    float innerFbmNoise = fbm(uv * c_fbmNoiseScale2 + vec2(.1, 4.));
    float innerThres = 1. - innerFbmNoise / c_amp;
    float innerRipThres = (innerThres - cmap(progress, 0., 1., -.15, 1.));

    float current = uv.y;

    vec3 ripColor = vec3(.95, .95, 1.);
    vec3 color = texel1;

    if (current < ripThres) { // main rip
        float mixVal = step(ripThres - current, 0.01 * c_shred);
        vec3 paperPattern = ripColor - vec3(random(v_tex_coord.x + v_tex_coord.y)) * .15;
        vec3 outColor = mix(texel2, vec3(paperPattern), mixVal);
        color = outColor;

        if (current + c_innerShred > innerRipThres && i_transition_progress < .9) { // inbetween rip
            vec3 innerColor = texel3;
            float innerRipEdge = step(.1 * c_shred2, (innerRipThres - current));
            color = mix(mix(innerColor, vec3(paperPattern), innerRipEdge), outColor, mixVal);
        }
    }

    gl_FragColor = vec4(color, 1.);
}
