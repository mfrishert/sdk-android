package com.playhaven.src.common;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.List;

import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import com.playhaven.src.utils.PHStringUtil;

/** Represents a simple class that can download resources from a url. You should set the delegate to receive callbacks.
 * 
 *  This class is primarily used for open device urls (other apps on the device, market, web browser, etc).
 *  Thus, use this class when you're expecting to open a device url. Otherwise use {@link PHAsyncRequest}.
 *  
 *  TODO: perhaps examine maximum_redirects and total_redirects? 
 *  
 *  TODO: some enterprising young chap should refactor the context out and instead callback into the main PHContentView
 *  via a delegate.
 */
public class PHURLLoader implements PHAsyncRequest.Delegate {
	
	private String targetURL;
	
	private ProgressDialog progressDialog; 
	
	private final String MARKET_URL_TEMPLATE = "http://play.google.com/store/apps/details?id=%s";
	
	public boolean openFinalURL;
	
	public Delegate delegate;
	
	private PHAsyncRequest conn;
		
	public boolean isLoading = false;
	
	private final int MAXIMUM_REDIRECTS = 10;
	
	private WeakReference<Context> context;
	
	private String callback;
	
	private static LinkedHashSet<PHURLLoader> allLoaders = new LinkedHashSet<PHURLLoader>();

	public static interface Delegate {
		public void loaderFinished(PHURLLoader loader);
		public void loaderFailed(PHURLLoader loader);
	}
	
	public PHURLLoader(Context context, Delegate delegate) {
		this(context); // call other constructor
		
		this.delegate = delegate;
	}
	
	public PHURLLoader(Context context) {
		progressDialog = new ProgressDialog(context);
		this.context = new WeakReference<Context>(context);
		
		openFinalURL = true; // open the final via the system
	}
	

	///////////////////////////////////////////////////////////
	
	public void setCallback(String callback) {
		this.callback = callback;
	}
	
	public String getCallback() {
		return callback;
	}
	
	///////////////////////////////////////////////////////////
	////////////// Mostly Utilized by Unit Tests //////////////
	public ProgressDialog getDialog() {
		return progressDialog;
	}
	
	public void setProgressDialog(ProgressDialog dialog) {
		this.progressDialog = dialog;
	}
	
	public PHAsyncRequest getConnection() {
		return conn;
	}
	
	public void setConnection(PHAsyncRequest conn) {
		this.conn = conn;
	}
	
	///////////////////////////////////////////////////////////
	
	public void setTargetURL(String url) {
		this.targetURL = url;
	}
	
	public String getTargetURL() {
		return this.targetURL;
	}
	///////////////////////////////////////////////////////////
	///////////////// Management Methods //////////////////////
	
	@SuppressWarnings("unchecked")
	public static void invalidateLoaders(Delegate delegate) {
		// We make a copy to prevent a ConcurrentModificationException
		LinkedHashSet<PHURLLoader> allLoadersCopy = (LinkedHashSet<PHURLLoader>) allLoaders.clone();
		
		for (PHURLLoader loader : allLoadersCopy) {
			if (loader.delegate == delegate) {
				
				// the loader will remove itself from the 'allLoaders' array
				loader.invalidate();
			}
		}
	}
	
	public static LinkedHashSet<PHURLLoader> getAllLoaders() {
		return allLoaders;
	}
	
	public static void removeLoader(PHURLLoader loader) {
		allLoaders.remove(loader);
	}
	
	public static void addLoader(PHURLLoader loader) {
		allLoaders.add(loader);
	}
	
	///////////////////////////////////////////////////////////
	public void open() {

		if( ! JSONObject.NULL.equals(targetURL) &&
			  targetURL.length() > 0		      ) {
			
			progressDialog.show();
			
			PHStringUtil.log(String.format("Opening url in PHURLLoader: %s", targetURL));
			
			isLoading = true;
			
			conn = new PHAsyncRequest(this);
			conn.setMaxRedirects(MAXIMUM_REDIRECTS);
			conn.request_type = PHAsyncRequest.RequestType.Get;
			conn.execute(Uri.parse(targetURL));
			
			synchronized(this) {
				PHURLLoader.addLoader(this);
			}
			
			return;
		}
		
		// finish immediately if no target url
		if(delegate != null)
			delegate.loaderFinished(this);
	}
	
	private void finish() {
		if( isLoading ) {
			isLoading = false;
			
			targetURL = conn.getLastRedirectURL();
			
			PHStringUtil.log("PHURLLoader - final redirect location: " + targetURL);
			
			if(openFinalURL 		 &&
			   targetURL != null 	 && 
			   !targetURL.equals("") &&
			   context.get() != null   ) {
				
				if (targetURL.startsWith("market:")) redirectMarketURL(targetURL); // I'll take a market please!
				
				else launchActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(targetURL))); // fine, just handle default!
				
			}
				
			if(delegate != null)
				delegate.loaderFinished(this);
			
			invalidate();
		}
	}
	
	private void launchActivity(Intent intent) {
		if (PHConfig.runningTests) return; // never launch when UI testing...
		
		context.get().startActivity(intent);
	}
	public void redirectMarketURL(String url) {
		if (context.get() == null)
			return;

		PHStringUtil.log("Got a market:// URL, verifying market app is installed");

		// check if market is valid
		// (http://android-developers.blogspot.com/2009/01/can-i-use-this-intent.html)

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(url));

		// do we have the market app installed?
		PackageManager packageManager = context.get().getPackageManager();
		List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(
				intent, PackageManager.MATCH_DEFAULT_ONLY);

		if (resolveInfo.size() == 0) {
			PHStringUtil.log("Market app is not installed and market:// not supported!");
			
			// since no market:// just open in the web browser!
			Uri uri = Uri.parse(
								String.format(MARKET_URL_TEMPLATE,
								Uri.parse(url).getQueryParameter("id"))
								);

			intent = new Intent(Intent.ACTION_VIEW, uri);
		}

		launchActivity(intent);
	}

	public void invalidate() {
		delegate = null;
		
		if (conn == null 		|| 
			progressDialog == null) return; // failed to start
		
		synchronized (this) {
			conn.cancel(true);
			PHURLLoader.removeLoader(this);
		}
		
		progressDialog.dismiss();
	}
	
	private void fail() {
		if(delegate != null)
			delegate.loaderFailed(this);
		
		invalidate();
	}
	
	////////////////////////////////////////////////////
	/////// PHAsyncRequest delegate methods ////////////
	
	@Override
	public void requestFinished(ByteBuffer response, int responseCode) {
		if(responseCode < 300) {
			PHStringUtil.log("PHURLLoader finishing from initial url: " + targetURL);
			finish();
		} else {
			PHStringUtil.log("PHURLLoader failing from initial url: " + targetURL + " with error code: "+responseCode);
			fail();
		}
	}

	@Override
	public void requestFailed(Exception e) {
		PHStringUtil.log("PHURLLoader failed with error: "+e);
		fail();
	}


}
