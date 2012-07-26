package com.playhaven.sampleapp;

import com.playhaven.androidsdk.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SamplePreferences extends PreferenceActivity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.preferences);
		
		// TODO: actually update preferences, etc.
	}
}
