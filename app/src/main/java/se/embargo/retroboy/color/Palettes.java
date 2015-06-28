package se.embargo.retroboy.color;

/**
 * Palettes are expressed as ABGR integers (Alpha, Blue, Green, Red)
 */
public class Palettes {
	public static final int[] BINARY = new int[] {
		0xff000000,
		0xffffffff};
	
	public static final int[] GAMEBOY_CAMERA = new int[] {
		0xff000000, 
		0xff858585, 
		0xffaaaaaa, 
		0xffffffff};

	public static final int[] GAMEBOY_SCREEN = new int[] {
		0xff0f380f, 
		0xff306230, 
		0xff0fac8b, 
		0xff0fbc9b};
	
	public static final int[] GAMEBOY_SCREEN_DESAT = new int[] {
		0xff1f381f, 
		0xff406240, 
		0xff1fac9b, 
		0xff1fbcab};

	public static final int[] POP_ART = new int[] {
		0xff303030,		// black (outline)
		0xfff5ffff,		// off-white (eyes, teeth, clothes)
		0xff4433c3,		// deep-red (lips) 
		//0xffbb6060,	// lilac-blue (clothes)
		0xffa03000,		// deep-blue (clothes)
		0xffd5e5f5,		// skin-tone
		//0xff4dfcff,	// bright-yellow (hair)
		//0xffedf9f9,	// off-white (eyes, teeth, clothes)
		//0xff1a008a,	// deep-red (lips)
		0xff6dedff,		// orange-yellow (hair, flames)
	};
	
	public static final int[] CHRONO_CROSS = new int[] {
		0xff000008,
		0xff0b1a20,
		0xff172843,
		0xff102949,
		0xff094323,
		0xff1e4f5d,
		0xff206b9c,
		0xff0f22a9,
		0xff7c342b,
		0xff09742b,
		0xff40cad0,
		0xff77a0e8,
		0xffab946a,
		0xffb3c4d5,
		0xff6ee7fc,
		0xffe2fafc};
		
	public static final int[] COMMODORE_64 = new int[] {
		0xff000000,
		0xffffffff,
		0xff354374,
		0xffbaac7c,
		0xff90487b,
		0xff4f9764,
		0xff853240,
		0xff7acdbf,
		0xff2f5b7b,
		0xff00454f,
		0xff6572a3,
		0xff505050,
		0xff787878,
		0xff8ed7a4,
		0xffbd6a78,
		0xff9f9f9f};

	public static final int[] COMMODORE_64_GAMMA_ADJUSTED = new int[] {
		0xFF000000,
		0xFFFFFFFF,
		0xFF2B3768,
		0xFFB2A470,
		0xFF863D6F,
		0xFF438D58,
		0xFF792835,
		0xFF6FC7B8,
		0xFF254F6F,
		0xFF003943,
		0xFF59679A,
		0xFF444444,
		0xFF6C6C6C,
		0xFF84D29A,
		0xFFB55E6C,
		0xFF959595};
	
	public static final int[] AMSTRAD_CPC464 = new int[] {
		0xFF000000,
		0xFF800000,
		0xFFFF0000,
		0xFF000080,
		0xFF800080,
		0xFFFF0080,
		0xFF0000FF,
		0xFF8000FF,
		0xFFFF00FF,
		0xFF008000,
		0xFF808000,
		0xFFFF8000,
		0xFF008080,
		0xFF808080,
		0xFFFF8080,
		0xFF0080FF,
		0xFF8080FF,
		0xFFFF80FF,
		0xFF00FF00,
		0xFF80FF00,
		0xFFFFFF00,
		0xFF00FF80,
		0xFF80FF80,
		0xFFFFFF80,
		0xFF00FFFF,
		0xFF80FFFF,
		0xFFFFFFFF};
}
