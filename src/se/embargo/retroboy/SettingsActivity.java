package se.embargo.retroboy;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getPreferenceManager().setSharedPreferencesName(Pictures.PREFS_NAMESPACE);
		addPreferencesFromResource(R.xml.settings);
	}
}
