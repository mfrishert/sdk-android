package com.playhaven.src.publishersdk.open;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.os.AsyncTask;
import android.os.Build;

import com.jakewharton.DiskLruCache;
import com.playhaven.src.common.PHAPIRequest;
import com.playhaven.src.common.PHCrashReport;
import com.playhaven.src.utils.PHStringUtil;

public class PHPrefetchTask extends AsyncTask<Integer, Integer, Integer> {
	private static final Integer BUFFER_SIZE = 1024;
	
	public static interface Listener {
		public void prefetchDone(int result);
	}
	
	public URL url;
	
	public Listener listener;
	
	private DiskLruCache cache;
	
	///////////////////////////////////////////
	//////////////// Accessors ///////////////
	public void setOnPrefetchDoneListener(Listener listener) {
		this.listener = listener;
	}
	
	public Listener getOnPrefetchDoneListener() {
		return listener;
	}
	
	public URL getURL() {
		return url;
	}
	
	public void setURL(String url) {
		
		try {
			this.url = new URL(url);
		} catch (MalformedURLException e) {
			this.url = null;
			PHStringUtil.log("Malformed URL in PHPrefetchTask: " + url);
		}
	}
	
	public void setCache(DiskLruCache cache) {
		this.cache = cache;
	}
	
	public DiskLruCache getCache() {
		if (cache == null)
			cache = DiskLruCache.getSharedDiskCache();
		// TODO: what if it is null again???
		return cache;
	}
	
	@Override
	protected Integer doInBackground(Integer... dummys) {
		disableConnectionReuseIfNecessary();
		
		int responseCode = HttpURLConnection.HTTP_BAD_REQUEST;
		
		// Note: while HttpURLConnection might be simpler, we use the Apache
		// libraries for easier testing with Robolectric
		try {
			synchronized (this) {
                // Note: we ignore the input dummy variables since we use URL
                if (url == null) return HttpURLConnection.HTTP_BAD_REQUEST;
                
                HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
                responseCode = urlConn.getResponseCode();
                
                BufferedInputStream in = new BufferedInputStream(urlConn.getInputStream());
                
                // dump to local cache
                DiskLruCache.Editor editor = getCache().edit(url.toString());
                
                // open a new handle to a cached file
                BufferedOutputStream cachedFile = new BufferedOutputStream(editor.newOutputStream(PHAPIRequest.PRECACHE_FILE_KEY_INDEX));
                
                // transfer to cache file
                byte[] buffer = new byte[BUFFER_SIZE];
                while (in.read(buffer) != -1) {
                    cachedFile.write(buffer);
                }
                
                in.close();
                cachedFile.flush();
                cachedFile.close();
                
                editor.commit();
                
                urlConn.disconnect();
                
                getCache().flush();
			}
		} catch (Exception e) { // swallow all exceptions
			PHCrashReport.reportCrash(e, "PHPrefetchTask - doInBackground", PHCrashReport.Urgency.low);
		}
		
		return responseCode;
	}
	
	/**
	 * Fixes strange bug in pre-Froyo distributions
	 * @see http://android-developers.blogspot.com/2011/09/androids-http-clients.html
	 */
	private void disableConnectionReuseIfNecessary() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO)
	        System.setProperty("http.keepAlive", "false");
	}
	
	@Override
	protected void onProgressUpdate(Integer... progress) {
		PHStringUtil.log("Progress update in prefetch operation: " + progress);
	}
	
	@Override
	protected void onPostExecute(Integer result) {
		PHStringUtil.log("Pre-fetch finished with response code: " + result);
		
		// don't catch exceptions from listener
		if (listener != null) listener.prefetchDone(result);
	}
}
