uniform mat4 u_MVPMatrix;
attribute vec4 a_Position;
uniform vec4 u_Color;
varying vec4 v_Color;

void main() {
    gl_Position = u_MVPMatrix * a_Position;
    gl_PointSize = 6.0;
    v_Color = u_Color;
}
