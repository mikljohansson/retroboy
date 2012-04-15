/*
 * Monochrome filter
 */

#pragma version(1)
#pragma rs java_package_name(se.embargo.onebit.filter)

rs_allocation gIn;
rs_allocation gOut;
rs_script gScript;

const static float3 gMonoMult = {0.299f, 0.587f, 0.114f};

void root(const uchar4 *v_in, uchar4 *v_out, const void *usrData, uint32_t x, uint32_t y) {
    float4 pixel = rsUnpackColor8888(*v_in);
    float3 mono = dot(pixel.rgb, gMonoMult);
    *v_out = rsPackColorTo8888(mono);
}

void filter() {
	int64_t t1 = rsUptimeMillis();

    rsForEach(gScript, gIn, gOut, 0);

	int64_t t2 = rsUptimeMillis();
	rsDebug("Monochrome filter in (ms)", t2 - t1);
}
