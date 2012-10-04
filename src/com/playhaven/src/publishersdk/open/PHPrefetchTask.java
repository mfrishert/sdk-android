package com.playhaven.src.publishersdk.open;

import java.io.BufferedOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

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
		
		int responseCode = HttpStatus.SC_BAD_REQUEST;
		
		// Note: while HttpURLConnection might be simpler, we use the Apache
		// libraries for easier testing with Robolectric
		try {
			synchronized (this) {
                // Note: we ignore the input dummy variables since we use URL
                if (url == null) {
                    return HttpStatus.SC_BAD_REQUEST;
                }
                
                DefaultHttpClient client = new DefaultHttpClient();
                HttpGet request = new HttpGet(url.toString());
                request.addHeader("Accept-Encoding", "gzip");
                
                HttpResponse response = client.execute(request);
                
                responseCode = response.getStatusLine().getStatusCode();
                if (responseCode != HttpStatus.SC_OK) {
                    return responseCode;
                }
                
                HttpEntity entity = response.getEntity();
                
                // dump to local cache
                DiskLruCache.Editor editor = getCache().edit(url.toString());
                // open a new handle to a cached file
                BufferedOutputStream cachedFile = new BufferedOutputStream(editor.newOutputStream(PHAPIRequest.PRECACHE_FILE_KEY_INDEX));
                
                Header contentEncoding = entity.getContentEncoding();
                String encoding = (contentEncoding == null) ? null : contentEncoding.getValue();
                
                if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
                    GZIPInputStream in = new GZIPInputStream(entity.getContent());

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        cachedFile.write(buffer, 0, bytesRead);
                    }
                    
                    in.close();
                } else {
                    // Content is not compressed.
                    entity.writeTo(cachedFile);
                }
                    
                cachedFile.flush();
                cachedFile.close();
                    
                editor.commit();
         
                client.getConnectionManager().shutdown();
    
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
