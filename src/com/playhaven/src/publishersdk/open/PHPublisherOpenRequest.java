package com.playhaven.src.publishersdk.open;


import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;

import com.jakewharton.DiskLruCache;
import com.playhaven.src.common.PHAPIRequest;
import com.playhaven.src.common.PHConfig;
import com.playhaven.src.common.PHCrashReport;
import com.playhaven.src.common.PHSession;

public class PHPublisherOpenRequest extends PHAPIRequest implements PHPrefetchTask.Listener {
	private ConcurrentLinkedQueue<PHPrefetchTask> prefetchTasks = new ConcurrentLinkedQueue<PHPrefetchTask>(); 
	
	/** This flag indicates whether or not we should precede with pre-caching after receiving a response or if
	 * we should instead wait for a direct command. Only used for internal unit testing.
	 */
	public boolean startPrecachingImmediately = true;
	
	/** Pre-fetch wrapper delegate. We use this to call out to our own delegates*/
	public static interface PrefetchListener {
		public void prefetchFinished(PHPublisherOpenRequest request);
	}
	
	private PrefetchListener prefetch_listener;
	
	private PHSession session;
	
	public void setPrefetchListener(PrefetchListener listener) {
		this.prefetch_listener = listener;
	}
	
	public PrefetchListener getPrefetchListener() {
		return this.prefetch_listener;
	}
	
	public PHSession getSession() {
		return session;
	}
	
	public ConcurrentLinkedQueue<PHPrefetchTask> getPrefetchTasks() {
		return prefetchTasks;
	}
	
	public PHPublisherOpenRequest(Context context, PHAPIRequest.Delegate delegate) {
		this(context);
		this.setDelegate(delegate);
	}
	
	public PHPublisherOpenRequest(Context context) {
		super(context);
		
		synchronized (PHPublisherOpenRequest.class) {
    		try {
                if (PHConfig.precache) {
                    DiskLruCache cache = DiskLruCache.getSharedDiskCache();
                    
                    if (cache == null) { // create cache if it doesn't exist
                        DiskLruCache.createSharedDiskCache(new File(context.getCacheDir() + File.separator + API_CACHE_SUBDIR), 
                                          APP_CACHE_VERSION, 
                                          1, 
                                          PHConfig.precache_size);
                    } else if (cache.isClosed()) {
                        cache.open();
                    }
                }
            
            } catch (Exception e) { // swallow all exceptions
                PHCrashReport.reportCrash(e, "PHPublisherOpenRequest - PHPublisherOpenRequest", PHCrashReport.Urgency.critical);
            }
		}
		
		session = PHSession.getInstance(context);
	}
	
	@Override
	public String baseURL() {
		return super.createAPIURL("/v3/publisher/open/");
	}
	
	@Override
	public void send() {
		// Note: ordering is important! You *must* call session.start() *before* sending the request
	    session.start();
	    super.send();
	}
	
	@Override
	public void handleRequestSuccess(JSONObject res) {
		
		if ( PHConfig.precache && res.has("precache") ) {
			prefetchTasks.clear();
			
			JSONArray precached = res.optJSONArray("precache");
			if (precached != null) {	
				
				for (int i = 0; i < precached.length(); i++) {
					String url = precached.optString(i);
					
					if (url != null) {
						PHPrefetchTask task = new PHPrefetchTask();
						task.setOnPrefetchDoneListener(this);
						task.setURL(url);
						prefetchTasks.add(task);
					}
					
				}
			}
			
			// start fetching the pre-cached elements
			if (startPrecachingImmediately)
				startNextPrefetch();
		}
		
        if (prefetchTasks.size() == 0) {
            try {
                DiskLruCache.getSharedDiskCache().close();
            } catch (IOException e) {
                PHCrashReport.reportCrash(e, "PHPublisherOpenRequest - handleRequestSuccess", PHCrashReport.Urgency.high);
            }
        }
		
		session.startAndReset();
		
		// call out to delegates
		super.handleRequestSuccess(res);
	}
	
	public void startNextPrefetch() {
		if (prefetchTasks.size() > 0) prefetchTasks.poll().execute();
	}
	
	///////////////////////////////////////////////////////////
	//////////////////// Prefetch Listener ////////////////////
	@Override
	public void prefetchDone(int result) {
		try {
			
			// previous prefetch is finished, start the next one
			if (prefetchTasks.size() > 0 && startPrecachingImmediately) {
				startNextPrefetch();
			} else { // no more pre-fetches? Call back to delegate
			    DiskLruCache.getSharedDiskCache().close();
				if (prefetch_listener != null) prefetch_listener.prefetchFinished(this);
			}
			
		} catch (Exception e) { // swallow all exceptions
			PHCrashReport.reportCrash(e, "PHPublisherOpenRequest - prefetchDone", PHCrashReport.Urgency.low);
		}

	}
	
	@Override
    public Hashtable<String, String> getAdditionalParams() {
	    Hashtable<String, String> params = new Hashtable<String, String>();
	    
	    params.put("ssum", String.valueOf(session.getTotalTime()));
	    params.put("scount", String.valueOf(session.getSessionCount()));
	    // TODO: should we send the precache = 1?
	    
	    return params;
	}

}
