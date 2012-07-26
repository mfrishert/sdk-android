package com.playhaven.src.common;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.jakewharton.DiskLruCache;
import com.playhaven.src.utils.PHStringUtil;

/** 
 * @class PHAPIRequest
 * Nicer wrapper for {@link PHAsyncRequest} and base class for making API calls.
 * We make extensive use of the "templating" pattern in this class since it is designed primarily to be overridden.
 * Subclasses should override the getters to customize the behavior of this class instead of calling a million setters.
 */
public class PHAPIRequest implements PHAsyncRequest.Delegate {

	public Delegate delegate;
	
	private PHAsyncRequest conn;
	
	private HashMap<String, String> signedParams;
	
	private Hashtable<String, String> additionalParams;
	
	private String urlPath;
	
	private int requestTag;
	
	private static String session = "";
	
	private static WeakReference<SharedPreferences> preferences;
	
	private final String SESSION_PREFERENCES = "com_playhaven_sdk_session";
	
	// the "subkey" index within each precache entry (we only need 1)
	public static final Integer PRECACHE_FILE_KEY_INDEX = 0;
	
	public final  String API_CACHE_SUBDIR    = "apicache";
	
	public final  Integer APP_CACHE_VERSION  = 100;
	
	// protected so subclasses can access it. You shouldn't.
	protected String fullUrl;

	public static interface Delegate {
		public void requestSucceeded(PHAPIRequest request, JSONObject responseData);
		public void requestFailed(PHAPIRequest request, Exception e);
	}
	
	/** Creates a new API using the specified context to glean device information.
	 * If a null context is passed in, the values in {@link PHConfig} may be invalid
	 */
	public PHAPIRequest(Context context) {
		
		if (context != null) {
			synchronized (PHAPIRequest.class) {
				PHConfig.cacheDeviceInfo(context); // get all relevant device info
				
				try {
				if (DiskLruCache.getSharedDiskCache() == null) // open up the cache if it doesn't exist
					DiskLruCache.open(new File(context.getCacheDir() + File.separator + API_CACHE_SUBDIR), 
								      APP_CACHE_VERSION, 
								      1, 
								      PHConfig.precache_size);
				
				} catch (Exception e) { // swallow all exceptions
					e.printStackTrace();
				}
				
				preferences = new WeakReference<SharedPreferences>(context.getSharedPreferences(
																		SESSION_PREFERENCES, 
																		Context.MODE_WORLD_WRITEABLE) // shared with all apps so beware!
																  	);
			}
			 
		}
		
		
		if (PHConfig.token == null       || PHConfig.secret == null     ||
			PHConfig.token.length() == 0 || PHConfig.secret.length() == 0)
			throw new IllegalArgumentException("You must set your token and secret from the Playhaven dashboard");
		
	}

	public PHAPIRequest(Context context, Delegate delegate) {
		this(context);
		this.delegate = delegate;
	}
	
	///////////////////////////////////////////////
	public void setDelegate(Delegate delegate) {
		this.delegate = delegate;

	}
	
	///////////////////////////////////////////////
	public static void setSession(String session) {
		if (session == null) return;
		
		synchronized (PHAPIRequest.class) {
			PHAPIRequest.session = session;
			
			if (preferences.get() != null) {
				SharedPreferences.Editor editor = preferences.get().edit();
				editor.putString("session", session);
					
				editor.commit();
			}
				
		}
	}
	
	public static String getSession() {
		synchronized (PHAPIRequest.class) {
			if (PHAPIRequest.session == null) PHAPIRequest.session = "";
			
			if (PHAPIRequest.preferences.get() != null) PHAPIRequest.session = PHAPIRequest.preferences.get().getString("session", "");
		}
		
		return PHAPIRequest.session;
	}
	
	///////////////////////////////////////////////
	
	public void setRequestTag(int requestTag) {
		this.requestTag = requestTag;
	}
	
	public int getRequestTag() {
		return this.requestTag;
	}
	
	///////////////////////////////////////////////////////
	//////////
	///// Generating Signed Parameters //////////
	
	/** Creates a base "authed" URL + any addditional parameters (usually from subclass). 
	 * @throws NoSuchAlgorithmException 
	 * @throws UnsupportedEncodingException 
	 * @return HashMap Mapping from query parameters to values
	 * */
	public HashMap<String, String> getSignedParams() throws UnsupportedEncodingException, 
															  NoSuchAlgorithmException {
		if(signedParams == null) {
			String device, 
			nonce, 
			sigHash, 
			sig, 
			appId, 
			appVersion, 
			hardware, 
			os, 
			idiom, 
			sdk_version, 
			width, 
			height, 
			sdk_platform, 
			orientation,
			screen_density,
			connection;
			
			device 			= (PHConfig.device_id != null ? PHConfig.device_id : "null");
			idiom			= String.valueOf(PHConfig.device_size);
			orientation 	= "0"; // TODO: use actual orientation?
			
			// make sure we generate the device id before doing the sighash!
			// you like that formatting do you?
			
			nonce 			= PHStringUtil	.base64Digest  (PHStringUtil.generateUUID	  ());
			
			if (PHConfig.token == null || PHConfig.secret == null) throw new UnsupportedOperationException("You must set both the token and secret from the Playhaven Dashboard!");
			
			sig		  		= String		.format		   ("%s:%s:%s:%s", 
															PHConfig.token, 
															(device != null ? device : ""), 		// in the future we'll add session
															(nonce  != null ? nonce  : ""), 
															PHConfig.secret);
			
			sigHash         = PHStringUtil	.hexDigest	   (sig);
			appId           = PHConfig		.app_package;
			appVersion      = PHConfig		.app_version;
			hardware        = PHConfig 		.device_model;
			os              = String		.format		   ("%s %s", 
															PHConfig.os_name			, 
													 		PHConfig.os_version			);
			
			sdk_version     = PHConfig		.sdk_version;
			sdk_platform    = 				"android";
			width           = String		.valueOf	   (PHConfig.screen_size.width  ());
			height          = String		.valueOf	   (PHConfig.screen_size.height ());
			screen_density  = String		.valueOf	   (PHConfig.screen_density_type  );
			
			connection      = ((PHConfig.connection == PHConfig.ConnectionType.NO_PERMISSION) 
			                                ? null
			                                : String.valueOf(PHConfig.connection.ordinal()));
			
			// decide if we add to existing params.
			Hashtable<String, String> additionalParams = getAdditionalParams(); // only call *once* since might have side effects
			
			// makek a copy...
			HashMap<String, String> add_params 		   = (additionalParams != null 
																	? new HashMap<String, String>(additionalParams)
																	: new HashMap<String, String>());
			
			
			signedParams = new HashMap<String, String>();
			
			signedParams.put("device", 			device);
			
			signedParams.put("token", 			PHConfig.token);
			signedParams.put("signature", 		sigHash);
			
			signedParams.put("nonce", 			nonce);
			signedParams.put("app", 			appId);
			
			signedParams.put("app_version", 	appVersion);
			signedParams.put("hardware", 		hardware);
			
			signedParams.put("os", 				os);
			signedParams.put("idiom", 			idiom);
			
			signedParams.put("width", 			width);
			signedParams.put("height", 			height);
			
			signedParams.put("sdk_version", 	sdk_version);
			signedParams.put("sdk_platform", 	sdk_platform);
			
			signedParams.put("orientation", 	orientation);
			signedParams.put("dpi", 			screen_density);
			
			signedParams.put("languages", 		Locale.getDefault().getLanguage());
			
			if (connection != null) 
				signedParams.put("connection",	connection);
			
			add_params.putAll(signedParams);
			signedParams 	= add_params;
		}
		
		return signedParams;
	}
	
	/** Sets the additional parameters (mostly for testing)*/
	public void setAdditionalParameters(Hashtable<String, String> params) {
		this.additionalParams = params;
	}
	
	/** Gets the additional parameters. Declared as an accessor so it can be overrode*/
	public Hashtable<String, String> getAdditionalParams() {
		return additionalParams;
	}
	
	/** Produces a query string with the signed parameters attached.
	 * @throws NoSuchAlgorithmException 
	 * @throws UnsupportedEncodingException */
	public String signedParamsStr() throws UnsupportedEncodingException, 
										   NoSuchAlgorithmException {
		
		return PHStringUtil.createQuery(getSignedParams());
	}
	
	public void send() {
		conn = new PHAsyncRequest(this);
			
		conn.request_type = getRequestType();
			
		send(conn);	
	}
	
	/** Actually kicks off the request*/
	public void send(PHAsyncRequest client) {
		try { // all encompassing crash report before we spawn new thread
		this.conn = client;
		
		// explicitly set the post params if a post request
		if (conn.request_type == PHAsyncRequest.RequestType.Post)
			conn.addPostParams(getPostParams());
					
					
			PHStringUtil.log("Sending PHAPIRequest of type: " + getRequestType().toString());
			PHStringUtil.log("PHAPIRequest URL: " + getURL());
					
			conn.execute(Uri.parse(getURL()));
					
		} catch (Exception e) {
			PHCrashReport.reportCrash(e, "PHAPIRequest - send()", PHCrashReport.Urgency.critical);
		}
	}
	
	//////////////////////////////////////////////////
	/////////// Request Detail Differences /////////////
	/** Gets the request type. This should be overriden by subclasses to control the type of request which gets sent.*/
	public PHAsyncRequest.RequestType getRequestType() {
		return PHAsyncRequest.RequestType.Get;
	}
	
	/** Gets the post parameters if the request type is POST. Subclasses should override to provide parameters.*/
	public Hashtable<String, String> getPostParams() {
		return null; // just empty
	}
	/** Should only be overridden and not called directly from external class.*/
	protected void finish() {
		conn.cancel(true);
		
	}
	
	public void cancel() {
		PHStringUtil.log(this.toString() + " canceled!");
		finish();
	}
	
	/** Gets url along with parameters attached (plus auth parameters).
	 * Subclasses should override for additional customization.
	 * @throws NoSuchAlgorithmException 
	 * @throws UnsupportedEncodingException 
	 */
	public String getURL() throws UnsupportedEncodingException, 
								  NoSuchAlgorithmException {
		if(fullUrl == null)
			fullUrl = String.format("%s?%s", this.baseURL(), signedParamsStr());

		return fullUrl;
	}
	
	/** Appends the endpoint path to the base api url*/
	public String createAPIURL(String slug) {
		return PHConfig.api + slug;
	}
	
	/** The base url (or slug) with no parameters attached. Subclasses should override */
	public String baseURL() {
		return urlPath;
	}
	
	/** Sets the base url.*/
	public void setBaseURL(String url) {
		this.urlPath = url;
	}
	
	///////////////////////////////////////////////////////
	//////////// Response Handling ///////////////////////
	
	/** Override point for subclasses (template pattern) to handle requests*/
	public void handleRequestSuccess(JSONObject res) {
		if (delegate == null) return;
		
		if(res != null)
			delegate.requestSucceeded(this, res);
		else 
			delegate.requestFailed(this, new JSONException("Couldn't parse json"));
		
	}
	
	/** Processes response. Broken into its own method to make the code more readable.*/
	public void processRequestResponse(JSONObject response) {
		JSONObject res = response.optJSONObject("response");
		
		// first make sure we actually have a response
		if (JSONObject.NULL.equals(res) ||
			res.equals("") 			    ||
			res.equals("undefined")		  ) {
			
			if (delegate != null)  delegate.requestFailed(this, new JSONException("No 'response' body in JSON payload"));
			
			return;
		}
		
		// the response JSON object may be empty
		// but that often indicates success. We simply pass through.
		
		try { PHStringUtil.log("Found dictionary for 'response' key in server response: "+res.toString(2)); } catch (JSONException e) { e.printStackTrace(); }
			
		handleRequestSuccess(res);
			
	}

	///////////////////////////////////////////////////////////////
	/////////////// PHAsyncRequest Delegate Methods ///////////////
	
	@Override
	public void requestFinished(ByteBuffer response) {
		// parse into json
		try {
			if (response == null 		|| 
				response.array() == null  ) 
				return;
			
			String res_str = new String(response.array(), "UTF8");
			PHStringUtil.log("Unparsed JSON: "+res_str);
			JSONObject json = new JSONObject(res_str);
			
			processRequestResponse(json);
			
		} catch (UnsupportedEncodingException e) {
			PHCrashReport.reportCrash(e, "PHAPIRequest - requestFinished", PHCrashReport.Urgency.low);
			
		} catch (JSONException e) {
			// PHCrashReport.reportCrash(e, "PHAPIRequest - requestFinished", PHCrashReport.Urgency.low);
			// force us to call the delegate and inform them of the parse error.
			if (delegate != null) delegate.requestFailed(this, new JSONException("Could not parse JSON: "+e.getMessage()));
			
		} catch (Exception e) { // swallow all exceptions
			PHCrashReport.reportCrash(e, "PHAPIRequest - requestFinished", PHCrashReport.Urgency.critical);
		}
		
	}

	@Override
	public void requestFailed(Exception e) {
		// PHCrashReport.reportCrash(e, "PHAPIRequest - requestFailed", PHCrashReport.Urgency.low);
		
		if (delegate != null) delegate.requestFailed(this, e);
		
	}

	@Override
	public void requestResponseCode(int responseCode) {
		PHStringUtil.log("Received response code: "+responseCode);
		
		if(delegate != null && responseCode != 200)
			delegate.requestFailed(this, new IOException("API Request failed with code: " + responseCode));
		
	}

	@Override
	public void requestProgressUpdate(int progress) {
		// pass	
	}


}
