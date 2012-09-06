package com.playhaven.sampleapp;

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;

import com.playhaven.androidsdk.R;
import com.playhaven.sampleapp.examples.ExampleView;
import com.playhaven.sampleapp.examples.PublisherContentView;
import com.playhaven.sampleapp.examples.PublisherIAPView;
import com.playhaven.sampleapp.examples.PublisherOpenView;
import com.playhaven.src.common.PHConfig;
import com.playhaven.src.common.PHSession;

public class SampleApp extends ListActivity {
	public static enum Pref {
		Token("tokenPref"),
		Secret("secretPref"),
		Server("apiServerPref");
		
		private String key;
		
		private Pref(String key) {
			this.key = key;
		}
		
		public String getKey() {
			return this.key;
		}
	}
	
	/** Simple utility method for setting the server, private, and public key*/
	public void setCredentials(String server, String token, String secret) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SampleApp.Pref.Token.getKey(),  token);
		editor.putString(SampleApp.Pref.Secret.getKey(), secret);
		editor.putString(SampleApp.Pref.Server.getKey(), server);
		
		editor.commit();
	}
	
	/** Simple class for holding a request title and url type. Static class to avoid grabbing
	 * reference to activity and causing memory leak*/
	public static class DemoRequest implements DetailAdapter.DetailObject {
		private String title;
		
		private String requestURL;
		
		private String contentDescription;
		
		public DemoRequest(String title, String requestURL, String contentDescription) {
			this.title = title;
			this.requestURL = requestURL;
			this.contentDescription = contentDescription;
		}
		
		////////////////////////////
		// Detail Adapter Methods
		public String getTitle() {
			return title;
		}
		
		public String getContentDescription() {
			return contentDescription;
		}
		
		public String getDetail() {
			return requestURL;
		}
		
		public View getView() {
			return null;
		}
	}
	
	private ArrayList<DemoRequest> requests;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle("Playhaven SDK: " + PHConfig.sdk_version);
        
        createDemoRequests();
        
        addPreferencesButton();
        
        setListAdapter(new DetailAdapter<DemoRequest>(this, R.layout.row, requests));
        
        getListView().setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// we subtract 1 to account for the header
				position -= 1;
				
				itemTapped(position);
			}
		});
        
    }
    
    private void addPreferencesButton() {
    	// add the button to the listview as a header
    	Button showPrefsBtn = new Button(this);
    	showPrefsBtn.setText("Settings");
    	
    	showPrefsBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent showPrefsIntent = new Intent(SampleApp.this, SamplePreferences.class);
				startActivity(showPrefsIntent);
			}
		});
    	
    	getListView().addHeaderView(showPrefsBtn);
    	
    }
    
	private void itemTapped(int position) {
		DemoRequest request = requests.get(position);
		
		if(request.title.equals("Open"))
			startExampleActivity(PublisherOpenView.class);
		
		else if(request.title.equals("Content"))
			startExampleActivity(PublisherContentView.class);
		
		else if (request.title.equals("IAP"))
			startExampleActivity(PublisherIAPView.class);
		
	}
	
	private void startExampleActivity(Class<? extends ExampleView> cls) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		// TODO: default to something other than null?
		PHConfig.token  = prefs.getString(Pref.Token.getKey(), 		null);
		PHConfig.secret = prefs.getString(Pref.Secret.getKey(), 	null);
		PHConfig.api    = prefs.getString(Pref.Server.getKey(), 	null);
		PHConfig.precache = true;
        
		Intent intent = new Intent(this, cls);
		startActivity(intent);
	}
	
    private void createDemoRequests() {
    	// create the demo requests
        requests = new ArrayList<DemoRequest>();
        requests.add(new DemoRequest("Open", "/publisher/open/", 		"openRequest"));
        requests.add(new DemoRequest("Content", "/publisher/content/", 	"contentRequest"));
        requests.add(new DemoRequest("IAP", "/publisher/iap/", 			"iapRequest"));

    }
    
    @Override
    protected void onResume() {
        super.onResume();
        PHSession.register(this);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        PHSession.unregister(this);
    }
    
}
