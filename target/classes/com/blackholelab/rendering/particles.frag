#version 330 core

out vec4 outColor;

void main() {
    vec2 centered = gl_PointCoord * 2.0 - 1.0;
    float radiusSquared = dot(centered, centered);
    float alpha = (1.0 - smoothstep(0.35, 1.0, radiusSquared)) * 0.9;
    if (alpha < 0.01) {
        discard;
    }
    outColor = vec4(1.0, 0.58, 0.22, alpha);
}
