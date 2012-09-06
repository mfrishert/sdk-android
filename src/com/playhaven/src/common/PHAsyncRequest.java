package com.playhaven.src.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectHandler;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Base64;

import com.playhaven.src.utils.PHStringUtil;

/**
 * Represents an asynchronous network request and mostly used in {@link PHAPIRequest} and {@link PHURLLoader}.
 * 
 * You can set the http parameters, uri, and add post parameters. Make sure you
 * set the delegate when using this class.
 * 
 * You can also control how many times we handle redirects. After the set amount of redirects,
 * we behave exactly like we just failed. We have two separate set of redirects tracked. The ones happening
 * in the {@link #PHHttpConn} and the ones happening in the {@link #PHAsyncRequest}. There are very few occasions
 * where we actually redirect in PHAsyncRequest, but we do have the capability. Thus, methods like {@link #getLastRedirect()}
 * always give priority to the PHHttpCon client.
 * 
 * You can also utilize basic http auth using the {@link setUsername} and {@link setPassword}.
 * @author samuelstewart
 * 
 */
public class PHAsyncRequest extends AsyncTask<Uri, Integer, ByteBuffer> {
	
	public static final int INFINITE_REDIRECTS = Integer.MAX_VALUE;
	
	public Uri url;

	public enum RequestType {
		Post, Get, Put, Delete
	};

	public RequestType request_type;

	private Exception lastError;

	private int responseCode;
	
	private ArrayList<NameValuePair> postParams = new ArrayList<NameValuePair>();
	
	private boolean isDownloading;
		
	private PHHttpConn client;

	@SuppressWarnings("unused")
	private String username;
	
	@SuppressWarnings("unused")
	private String password;

	public HttpParams params;

	private long requestStart;
	
	/** Simple class that provides our http connection. We use it to divorce dependencies
	 * and for unit testing (Dependancy Injection). PHHttpConn supports basic HTTP Auth as well.
	 */
	public static class PHHttpConn {
		protected DefaultHttpClient client;
		
		private int max_redirects = INFINITE_REDIRECTS;
		
		private String username;
		
		private String password;
		
		private PHSchemeRegistry mSchemeReg = new PHSchemeRegistry();
		
		private int totalRedirects = 0;
		
		private ArrayList<String> redirectUrls = new ArrayList<String>();
		
		private HttpUriRequest cur_request;
		
		/** Our custom redirect handler. On some android versions, this doesn't work
		 * which is why we move our main logic to {@link shouldRedirect} so clients can call
		 * directly.
		 * @author samuelstewart
		 *
		 */
		private class PHRedirectHandler extends DefaultRedirectHandler {
			@Override
			public boolean isRedirectRequested(HttpResponse response, HttpContext context) {
				return shouldRedirect(response);
			}
		}
		
		/** We must use this wrapper so that we can utilize different scheme registries.
		 * when testing.
		 */
		public static class PHSchemeRegistry {
			private SchemeRegistry schemeReg = new SchemeRegistry();
			
			public Scheme get(String name) {
				return schemeReg.get(name);
			}
		}
		
		public void setMaxRedirect(int max) {
			max_redirects = max;
		}
		
		public int getMaxRedirects() {
			return max_redirects;
		}
		
		///////////////////////////
		public PHHttpConn() {
			client = new DefaultHttpClient(enableRedirecting(null));
			//set our hook into the redirect handler
			client.setRedirectHandler(new PHRedirectHandler());
		}
		
		public void setSchemeRegistry(PHSchemeRegistry reg) {
			this.mSchemeReg = reg;
		}
		
		public DefaultHttpClient getHTTPClient() {
			return client;
		}

		////////////////////////////////////////
		/////// Redirect Methods ///////////////
		public String getLastRedirect() {
			if (redirectUrls.size() == 0) return null;
			
			return redirectUrls.get(redirectUrls.size() - 1);
		}
		
		public void addRedirectUrl(String url) {
			redirectUrls.add(url);
		}
		
		public void clearRedirects() {
			redirectUrls.clear();
		}
		
		/** Checks to see if status code is redirect request or not.*/
		public boolean isRedirectResponse(int code) {
			return (code >= 300 && code <= 307);
		}
		
		/** 
		 * Should we redirect or have we hit the maximum number of redirects.
		 * We also track every single proposed URL redirect here. Not sure if this 
		 * is a good idea but really the only place to do so because we'll
		 * never visit urls with prefixes like market:// even though we need them
		 * as redirects
		 * @param response the http response from the server
		 * @return true if we should redirect, false otherwise
		 */
		public boolean shouldRedirect(HttpResponse response) {
			if(isRedirectResponse(response.getStatusLine().getStatusCode())) {
				// first check to make sure a valid scheme (avoid market:// urls)
				if (response.getHeaders("Location").length == 0) return false; // not a redirect
				
				String redirectURL = response.getHeaders("Location")[0].getValue();
				
				if (redirectURL == null) return false;
				
				Uri uri = Uri.parse(redirectURL);
				
				if (uri == null 				|| 
					uri.getScheme() == null 	|| 
					uri.getPath() == null) 
								return false;
				
				
				// at least add to the redirects
				addRedirectUrl(uri.toString());
				
				Scheme scheme = mSchemeReg.get(uri.getScheme());
				
				if (scheme == null) return false;
				
				// finally, do we even have enough redirects left?
				return (++totalRedirects <= max_redirects);
			}
				
			// not a redirect response anyway..
			return false;
		}
		
		/** Turn on redirecting using existing parameters or new ones*/
		private HttpParams enableRedirecting(HttpParams params) {
			if(params == null)
				params = new BasicHttpParams();

			params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
			params.setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true); //force circular redirects...
			HttpClientParams.setRedirecting(params, true);
			return params;
		}
		
		public void setUsername(String username) {
			this.username = username;
		}
		
		public String getUsername() {
			return username;
		}
		
		public String getPassword() {
			return password;
		}
		
		public void setPassword(String password) {
			this.password = password;
		}
		
		/** Wrapper method so that we can mock if necessary*/
		public HttpResponse start(HttpUriRequest request) throws IOException {
			cur_request = request;

			totalRedirects = 0;
			clearRedirects();
			
			// use http auth if available
			if (username != null && password != null) {
				String encodedCredentials = Base64.encodeToString((username + ":" + password).getBytes(), Base64.URL_SAFE | Base64.NO_WRAP);
				String authStr = String.format("Basic %s", encodedCredentials);
				
				request.setHeader("Authorization", authStr);
			}
			
			return client.execute(request);
		}
		
		public HttpUriRequest getCurrentRequest() {
			return cur_request;
		}
	}
	
	/** Delegate interface. All calls will be on the main UI thread */
	public static interface Delegate {
		public void requestFinished(ByteBuffer response, int responseCode);

		public void requestFailed(Exception e);
				
	}

	private Delegate delegate;

	public PHAsyncRequest(Delegate delegate) {
		this.delegate = delegate;
		client = new PHHttpConn();
		request_type = RequestType.Get;
	}
	
	public void setMaxRedirects(int max) {
		client.setMaxRedirect(max);
	}
	
	public int getMaxRedirects() {
		return client.getMaxRedirects();
	}
	
	public ArrayList<NameValuePair> getPostParams() {
		return postParams;
	}
	
	public void addPostParam(String key, String value) {
		postParams.add(new BasicNameValuePair(key, value));
	}
	
	public void addPostParams(Hashtable<String, String> params) {
		if (params == null) return;
		
		postParams.clear();
		
		for (Map.Entry<String, String> entry : params.entrySet())
			postParams.add(new BasicNameValuePair(entry.getKey(), 
												  entry.getValue()));
		
	}
	
	/** Get the final url we arrived at */
	public String getLastRedirectURL() {
		return client.getLastRedirect();
	}
	
	public PHHttpConn getPHHttpClient() {
		return client;
	}
	
	/** We only take the first uri, so don't bother passing in more than one. */
	@Override
	protected ByteBuffer doInBackground(Uri... urls) {
		return execRequest(urls);
	}
	
	/** Moved into supporting method so that we can call ourselves recursively on redirects.*/
	private ByteBuffer execRequest(Uri... urls) {
		ByteBuffer buffer = null;
		responseCode = -1;
		lastError = null;
		
		synchronized (this) {
		try { // this block swallows *all* worst case exceptions
			isDownloading = true;
			
			requestStart = System.currentTimeMillis();
			
			client.clearRedirects();
			
			if (urls.length > 0) {
				Uri url = urls[0];
				
				// always prefer the url explicitly set
				if(!url.equals(this.url) && this.url != null)
					url = this.url;
				
				HttpResponse response = null;
				try {
					if (isCancelled()) return null;
					
					// convert to java.net.uri (b/c we already have escaped the url and Http*** will encode it again.
					String net_uri = url.toString();
					
					// decide what time of connection this is
					if (request_type == RequestType.Post) {
						HttpPost request = new HttpPost(net_uri);
						// set the post fields..
						request.setEntity(new UrlEncodedFormEntity(postParams));

						response = client.start(request);

					} else if (request_type == RequestType.Get) {
						HttpGet request = new HttpGet(net_uri);
						response = client.start(request);
					} else {
						HttpGet request = new HttpGet(net_uri);
						response = client.start(request);
					}

					// try to grab http response entity (maybe json or image?)
					HttpEntity entity = response.getEntity();
					
					// grab the response code
					responseCode = response.getStatusLine().getStatusCode();
					
					// Note: if the response code is a redirect, we should clamp it to a 200
					// since we often stop redirecting (such as when we find a market:// url).
					// Hence, if we actually have a redirect url we're good
					if (responseCode == 302 && getLastRedirectURL() != null)
						responseCode = 200;
					
					if (isCancelled()) return null;
					
					if (entity != null) {
						InputStream in_stream = entity.getContent();

						buffer = readStream(in_stream);

						in_stream.close();
					}

				} catch (IOException e) {
					lastError = e;
				}
			}
		} catch (Exception e) {
			PHCrashReport.reportCrash(e, "PHAsyncRequest - doInBackground", PHCrashReport.Urgency.critical);
		}
		}
		
		return buffer;
	}
	
	@Override
	protected void onPostExecute(ByteBuffer result) {
		super.onPostExecute(result);
		
		try { // swallow *all* exceptions (safety)
			isDownloading = false;
			
			long elapsedTimeMillis = System.currentTimeMillis() - requestStart;
			String outTime = "PHAsyncRequest elapsed time (ms) = " + elapsedTimeMillis;
			PHStringUtil.log(outTime);
			
			if(lastError != null && delegate != null)
				delegate.requestFailed(lastError);
			else if (delegate != null)
				delegate.requestFinished(result, responseCode);
			
		} catch (Exception e) {
			PHCrashReport.reportCrash(e, "PHAsyncRequest - onPostExecute", PHCrashReport.Urgency.critical);
		}
		
	}
	
	public void setUsername(String username) {
		this.username = username;
		client.setUsername(username);
	}
	
	public String getUsername() {
		return username;
	}
	
	public void setPassword(String password) {
		this.password = password;
		client.setPassword(password);
	}
	
	public RequestType getRequestType() {
		return request_type;
	}
	
	public String getPassword() {
		return password;
	}
	
	public void setPHHttpClient(PHHttpConn client) {
		this.client = client;
	}
	
	public boolean isDownloading() {
		return isDownloading;
	}
	
	@Override
	protected void onCancelled() {
		isDownloading = false;
		// TODO: this method is not particularly useful
	}
	

	/** public, static utility method for converting input stream to ByteBuffer */
	private static ByteBuffer readStream(InputStream inputStream) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];

		int len = 0;
		while ((len = inputStream.read(buffer)) != -1) {
			
			output.write(buffer, 0, len);
		}

		output.flush();
		return ByteBuffer.wrap(output.toByteArray());
	}
	
	public static String streamToString(InputStream inputStream) throws IOException, UnsupportedEncodingException {
		return new String(readStream(inputStream).array(), "UTF-8");
	}
}
