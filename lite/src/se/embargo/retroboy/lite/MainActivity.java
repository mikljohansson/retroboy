package se.embargo.retroboy.lite;

import se.embargo.retroboy.Pictures;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.google.ads.Ad;
import com.google.ads.AdListener;
import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;
import com.google.ads.InterstitialAd;
import com.google.ads.AdRequest.ErrorCode;

public class MainActivity extends se.embargo.retroboy.MainActivity {
	private static final String BANNER_UNIT_ID = "ca-app-pub-7852293465552528/9756871897";
	private static final String SHUTTER_UNIT_ID = "ca-app-pub-7852293465552528/9997217499";
	private static final int SHUTTER_INTERVAL = 3;
	
	private AdView _banner;
	private InterstitialAd _interstitial;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Create the adView
	    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
	    params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
	    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);

	    int padding = (int)(getResources().getDisplayMetrics().density * 8);
	    _banner = new AdView(this, AdSize.BANNER, BANNER_UNIT_ID);
	    _banner.setLayoutParams(params);
	    _banner.setPadding(padding, padding, padding, padding);
	    
	    RelativeLayout layout = (RelativeLayout)findViewById(R.id.cameraPreviewLayout);
	    layout.addView(_banner, 1);
	    layout.requestLayout();
	    layout.invalidate();
	    
	    // Load the banner ad
	    _banner.loadAd(createAdRequest());
	    
	    // Init the interstitial ad
	    _interstitial = new InterstitialAd(this, SHUTTER_UNIT_ID);
		_interstitial.setAdListener(new InterstitialListener());
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		// Preload the interstitial ad
		_interstitial.loadAd(createAdRequest());
	}

	private AdRequest createAdRequest() {
		// Initiate a request to load it with an ad
	    AdRequest request = new AdRequest();
	    request.addTestDevice(AdRequest.TEST_EMULATOR);
	    request.addTestDevice("A86642863B36B2DEC80461DCE79CF381");	// White GT-I9300
	    
	    // Add some relevant keywords
	    request.addKeyword("retro");
	    request.addKeyword("camera");
	    request.addKeyword("gameboy");
	    request.addKeyword("game boy");
	    request.addKeyword("commodore");
		return request;
	}
	
	protected void onMediaCaptured() {
		int count = _prefs.getInt(Pictures.PREF_IMAGECOUNT, 0);
		if ((count % SHUTTER_INTERVAL) == 0 && _interstitial.isReady()) {
			_interstitial.show();
		}
	}
	
	@Override
	public void onDestroy() {
		if (_banner != null) {
			_banner.destroy();
		}
		
		super.onDestroy();
	}
	
	private class InterstitialListener implements AdListener {
		@Override
		public void onReceiveAd(Ad ad) {}
		
		@Override
		public void onPresentScreen(Ad ad) {}
		
		@Override
		public void onLeaveApplication(Ad ad) {}
		
		@Override
		public void onFailedToReceiveAd(Ad ad, ErrorCode errcode) {}
		
		@Override
		public void onDismissScreen(Ad ad) {
			if (ad == _interstitial) {
				_interstitial.loadAd(createAdRequest());
			}
		}
	}
}
