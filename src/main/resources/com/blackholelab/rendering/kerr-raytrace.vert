#version 330 core

out vec2 vUv;

void main() {
    vec2 position = gl_VertexID == 0 ? vec2(-1.0, -1.0)
                  : gl_VertexID == 1 ? vec2(3.0, -1.0)
                                     : vec2(-1.0, 3.0);
    vUv = position * 0.5 + 0.5;
    gl_Position = vec4(position, 0.0, 1.0);
}
