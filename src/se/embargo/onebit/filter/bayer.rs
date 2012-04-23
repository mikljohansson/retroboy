/*
 * Dithering filter using the Bayer method
 */

#pragma version(1)
#pragma rs java_package_name(se.embargo.onebit.filter)

typedef struct atkinson_data {
	uint32_t width, height;
	rs_allocation *input;
} atkinson_data_t;

rs_allocation gIn;
rs_allocation gOut;
rs_script gScript;

const static float gDitherThreshold[8][8] = {
	{0.0, 0.5, 0.125, 0.625, 0.03125, 0.53125, 0.15625, 0.65625}, 
	{0.75, 0.25, 0.875, 0.375, 0.78125, 0.28125, 0.90625, 0.40625}, 
	{0.1875, 0.6875, 0.0625, 0.5625, 0.21875, 0.71875, 0.09375, 0.59375}, 
	{0.9375, 0.4375, 0.8125, 0.3125, 0.96875, 0.46875, 0.84375, 0.34375}, 
	{0.046875, 0.546875, 0.171875, 0.671875, 0.015625, 0.515625, 0.140625, 0.640625}, 
	{0.796875, 0.296875, 0.921875, 0.421875, 0.765625, 0.265625, 0.890625, 0.390625}, 
	{0.234375, 0.734375, 0.109375, 0.609375, 0.203125, 0.703125, 0.078125, 0.578125}, 
	{0.984375, 0.484375, 0.859375, 0.359375, 0.953125, 0.453125, 0.828125, 0.328125}};

const static float3 gMonoMult = {0.299f, 0.587f, 0.114f};

void root(const uchar4 *v_in, uchar4 *v_out, const void *usrData, uint32_t x, uint32_t y) {
    float4 pixel = rsUnpackColor8888(*v_in);
    float mono = dot(pixel.rgb, gMonoMult);
    float result = step(gDitherThreshold[x % 8][y % 8], mono);
    *v_out = rsPackColorTo8888((float3)result);
}

void filter() {
	int64_t t1 = rsUptimeMillis();

	rsForEach(gScript, gIn, gOut, 0);

	int64_t t2 = rsUptimeMillis();
	rsDebug("Bayer filter in (ms)", t2 - t1);
}
