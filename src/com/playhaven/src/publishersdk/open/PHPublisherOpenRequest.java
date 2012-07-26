package com.playhaven.src.publishersdk.open;


import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;

import com.playhaven.src.common.PHAPIRequest;
import com.playhaven.src.common.PHConfig;
import com.playhaven.src.common.PHCrashReport;

public class PHPublisherOpenRequest extends PHAPIRequest implements PHPrefetchTask.Listener {
	private ConcurrentLinkedQueue<PHPrefetchTask> prefetchTasks = new ConcurrentLinkedQueue<PHPrefetchTask>(); 
	
	public static interface PrefetchListener {
		public void prefetchFinished(PHPublisherOpenRequest request);
	}
	
	private PrefetchListener prefetch_listener;
	
	public void setPrefetchListener(PrefetchListener listener) {
		this.prefetch_listener = listener;
	}
	
	public PHPublisherOpenRequest(Context context, PHAPIRequest.Delegate delegate) {
		this(context);
		this.setDelegate(delegate);
	}
	
	public PHPublisherOpenRequest(Context context) {
		super(context);

	}
	
	@Override
	public String baseURL() {
		return super.createAPIURL("/v3/publisher/open/");
	}
	
	@Override
	public void handleRequestSuccess(JSONObject res) {
		if ( ! res.isNull("session")) {
			PHAPIRequest.setSession(res.optString("session"));
		}
		
		if ( PHConfig.precache && res.has("precache")) {
			prefetchTasks.clear();
			
			JSONArray precached = res.optJSONArray("precache");
			
			for (int i = 0; i < precached.length(); i++) {
				String url = precached.optString(i);
				
				if (url != null) {
					PHPrefetchTask task = new PHPrefetchTask();
					task.setOnPrefetchDoneListener(this);
					task.setURL(url);
					prefetchTasks.add(task);
				}
				
			}
						
			startNextPrefetch();
		}
		
		
		// call out to delegates
		super.handleRequestSuccess(res);
	}

	private void startNextPrefetch() {
		if (prefetchTasks.size() > 0) prefetchTasks.poll().execute(-1); // pass in ignored param (-1)
	}
	
	@Override
	public void prefetchDone(int result) {
		try {
			
			// previous prefetch is finished, start the next one
			if (prefetchTasks.size() > 0)
				startNextPrefetch();
			else // no more prefetches? Call back to delegate
				if (prefetch_listener != null) prefetch_listener.prefetchFinished(this);
			
		} catch (Exception e) { // swallow all exceptions
			PHCrashReport.reportCrash(e, "PHPublisherOpenRequest - prefetchDone", PHCrashReport.Urgency.low);
		}

	}

}
