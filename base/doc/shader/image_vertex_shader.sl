uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;
uniform float uCRatio;
attribute vec4 aPosition;
attribute vec4 aTextureCoord;
varying vec2 vTextureCoord;

void main() {
	gl_Position = vec4(uCRatio,1,1,1) * uMVPMatrix * aPosition;
	vTextureCoord = (uSTMatrix * aTextureCoord).xy;
}
