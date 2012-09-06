package com.playhaven.src.publishersdk.content;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;

import com.playhaven.src.common.PHAPIRequest;
import com.playhaven.src.common.PHCrashReport;
import com.playhaven.src.common.PHSession;
import com.playhaven.src.publishersdk.content.PHContentView.ButtonState;
import com.playhaven.src.utils.PHStringUtil;

/** 
 * Represents a request for an actual advertisement or "content". We handle the close button and actual logistics such as rewards, etc.
 * Each instance makes a request to the server for the content template "data" and then "pushes" (displays) a PHContentView. The PHContentView
 * is an Activity which in turn can display other PHContentView if the content template makes a subrequest, etc.
 * 
 * @author Sam Stewart
 *  
 */
public class PHPublisherContentRequest extends PHAPIRequest{
	
	private WeakReference<Context> applicationContext; // should be the main Application context (we must have it...)
	
	private WeakReference<Context> activityContext; // should be an activity context
		
	private boolean showsOverlayImmediately = false;
	
	public String placement;
	
	private PHContent content;
	
	public String contentTag; 
	
	public enum PHRequestState {
		Initialized,
		Preloading,
		Preloaded,
		DisplayingContent,
		Done
	};
	
	public enum PHDismissType {
		ContentUnitTriggered, // content template dismissal
		CloseButtonTriggered, // called from close button
		ApplicationTriggered, //application currentState 
		NoContentTriggered    // Usually on error
	};
	
	private PHRequestState currentState;
	
	private PHRequestState targetState;
	
	/** Big ol' extra delegate methods that a delegate can implement for more detail. {@link PHPublisherContentRequest} will work just
	 * fine with a regular PHAPIRequest delegate but if you want additional detail, pass a {@link ContentDelegate}.
	 * It is an abstract class to allow the developer to only override methods they wish. 
	 * 
	 * We break the callbacks into smaller classes to emulate optional interfaces as in iOS (an reference different delegates).
	 */	
	public static interface FailureDelegate {
		//these two methods handle the request failing in general, and then the content request failing..
		public void didFail(PHPublisherContentRequest request, String error);
		public void contentDidFail(PHPublisherContentRequest request, Exception e);
	}
	
	public static interface CustomizeDelegate {
		public Bitmap closeButton (PHPublisherContentRequest request, ButtonState state);
		public int borderColor	  (PHPublisherContentRequest request, PHContent content);
	}
	
	public static interface RewardDelegate {
		public void unlockedReward(PHPublisherContentRequest request, PHReward reward);
	}
	
	public static interface PurchaseDelegate {
		public void shouldMakePurchase(PHPublisherContentRequest request, PHPurchase purchase);
	}

	public static interface ContentDelegate extends PHAPIRequest.Delegate {
		public void willGetContent		(PHPublisherContentRequest request					  );
		public void willDisplayContent	(PHPublisherContentRequest request, PHContent content );
		public void didDisplayContent	(PHPublisherContentRequest request, PHContent content );
		public void didDismissContent	(PHPublisherContentRequest request, PHDismissType type);
	}
	
	private ContentDelegate content_listener 	= null;
	
	public void setOnContentListener(ContentDelegate content_listener) {
		this.content_listener = content_listener;
	}
	
	private RewardDelegate reward_listener 		= null;
	
	public void setOnContentListener(RewardDelegate reward_listener) {
		this.reward_listener = reward_listener;
	}
	
	private PurchaseDelegate purchase_listener 	= null;
	
	public void setOnPurchaseListener(PurchaseDelegate purchase_listener) {
		this.purchase_listener = purchase_listener;
	}
	
	private CustomizeDelegate customize_listener = null;
	
	public void setOnCustomizeListener(CustomizeDelegate customize_listener) {
		this.customize_listener = customize_listener;
	}
	
	private FailureDelegate failure_listener 	= null;
	
	public void setOnFailureListener(FailureDelegate failure_listener) {
		this.failure_listener = failure_listener;
	}
	
	private static ConcurrentLinkedQueue<Long> dismissedStamps = new ConcurrentLinkedQueue<Long>(); // time stamps for dismissal
	
	/** Checks to see if we have dismissed a content view within
	 * the given number of milliseconds. 
	 * 
	 * <strong><em>Important</em></strong>: This method removes all previous dismiss stamps.
	 * Hence, repeat calls of this method are not useful without an intervening call to dismissedContent().
	 * @return whether or not a content view was dismissed within the number of milliseconds specified
	 */
	public static boolean didDismissContentWithin(long range) {
		
		long curTime = System.currentTimeMillis();
		long stampTime = 0; // start with the "oldest" conceivable value
		
		// start at the oldest and throw out any which are too old.
		// The oldest stamps are guaranteed to be at the head of the queue. This property is key.
		// We know that if the current stamp is within range, then a newer stamp must also be in range.
		// We will end up with the most recent timestamp because of this ordering.
		while (curTime - stampTime > range && dismissedStamps.size() > 0) {
			stampTime = dismissedStamps.poll();
		}
		
		// all of the stamps may be too old, let's check
		return (curTime - stampTime <= range ? true : false);
	}
	
	/** Utility method for PHContentView to log a dismiss*/
	public static void dismissedContent() {
		dismissedStamps.add(System.currentTimeMillis());
	}
	
	/** Set the different content request delegates using the sneaky 'instanceof' operator. It's a bit hacky but works for our purposes.*/
	public void setDelegates(Object delegate) {
		
		if (delegate instanceof RewardDelegate)
			reward_listener = (RewardDelegate)delegate;
		else {
			reward_listener = null;
			PHStringUtil.log("*** RewardDelegate is not implemented. If you are using rewards this needs to be implemented.");
		}

		if (delegate instanceof PurchaseDelegate)
			purchase_listener = (PurchaseDelegate)delegate;
		else {
			purchase_listener = null;
			PHStringUtil.log("*** PurchaseDelegate is not implemented. If you are using VGP this needs to be implemented.");
		}

		if (delegate instanceof CustomizeDelegate)
			customize_listener = (CustomizeDelegate)delegate; 
		else {
			customize_listener = null;
			PHStringUtil.log("*** CustomizeDelegate is not implemented, using Play Haven close button bitmap. Implement to use own close button bitmap.");
		}
		
		if (delegate instanceof FailureDelegate)
			failure_listener = (FailureDelegate)delegate;
		else {
			failure_listener = null;
			PHStringUtil.log("*** FailureDelegate is not implemented. Implement if want to be notified of failed content downloads.");
		}
		
		if (delegate instanceof ContentDelegate)
			content_listener = (ContentDelegate)delegate;
		else {
			content_listener = null;
			PHStringUtil.log("*** ContentDelegate is not implemented. Implement if want to be notified of content request states.");
		}

	}
	
	public PHPublisherContentRequest(Activity activity, String placement) {
		super(activity);
		
		this.placement = placement;
		
		// we use the Application Context to ensure decent stability (exists throughout application's life)
		this.applicationContext = new WeakReference<Context>(activity.getApplicationContext());
		this.activityContext = new WeakReference<Context>(activity);
		
		registerReceiver();
		
		setCurrentState(PHRequestState.Initialized);
	}
	
	public PHPublisherContentRequest(Activity activity, ContentDelegate delegate, String placement) {
		this(activity, placement);
		
		this.delegate = delegate;
		setDelegates(delegate);
	}
	
	@Override
	public String baseURL() {
		return super.createAPIURL("/v3/publisher/content/");
	}
	
	public void setOverlayImmediately(boolean doOverlay) {
		this.showsOverlayImmediately = doOverlay;
	}
	
	public void setCurrentState(PHRequestState state) {
	    if (state      == null) return;
		if (this.currentState == null) this.currentState = state; //guard against null edge case..
		
		// only set currentState forward in time! (if set above, will just ignore)
		if (state.ordinal() > this.currentState.ordinal()) {
			this.currentState = state;
		}
	}
	
	public PHRequestState getCurrentState() {
		return currentState;
	}
	
	public PHRequestState getTargetState() {
		return targetState;
	}
	
	public PHContent getContent() {
		return content;
	}
	
	public void setTargetState(PHRequestState targetState) {
		this.targetState = targetState;
	}
	
	/////////////////////////////////////////////////
	/////////////////////////////////////////////////
	
	public void preload() {
		targetState = PHRequestState.Preloaded;
		continueLoading();
	}
	
	private void loadContent() {
		setCurrentState(PHRequestState.Preloading);
		super.send(); // now actually send the request
		
		// order is important here! We need to kick off the background task first
		if(content_listener != null)
			content_listener.willGetContent(this);

	}
	
	private void showContent() {
		if (targetState == PHRequestState.DisplayingContent || targetState == PHRequestState.Done) {
			
			if (content_listener != null)
				content_listener.willDisplayContent(this, content);
			
			// the currentState might have been 'Done'
			setCurrentState(PHRequestState.DisplayingContent);
			
			BitmapDrawable inactive = null;
			BitmapDrawable active = null;
			HashMap<String, Bitmap> customClose = new HashMap<String, Bitmap>();
			
			if (customize_listener != null) {
				inactive = new BitmapDrawable(customize_listener.closeButton(this, ButtonState.Up));
				active = new BitmapDrawable(customize_listener.closeButton(this, ButtonState.Up));

				customClose.put(ButtonState.Up.name(), inactive.getBitmap());
				customClose.put(ButtonState.Down.name(), active.getBitmap());
			}
			
			String tag = "PHContentView: " + this.hashCode(); // generate a unique tag for the PHContentView 
			
			// TODO: not sure the content view should do its own pushing?
			contentTag = PHContentView.pushContent(content, activityContext.get(), customClose, tag);

			if(content_listener != null) 
				content_listener.didDisplayContent(this, content);
		}
	}
	
	private void continueLoading() {
		switch (currentState) {
			case Initialized:
				loadContent();
				break;
			case Preloaded:
				showContent();
				break;
			default:
				break;
		}
	}
	
	public boolean getOverlayImmediately() {
		return this.showsOverlayImmediately;
	}
	
	@Override
	public void send() {
		try {
		targetState = PHRequestState.DisplayingContent;
		
		if(content_listener != null)
			content_listener.willGetContent(this);
		
		continueLoading();
		
		} catch(Exception e) { // swallow all exceptions
			PHCrashReport.reportCrash(e, "PHPublisherContentRequest - send", PHCrashReport.Urgency.critical);
		}
	}
	
	@Override
	public void finish() {
		setCurrentState(PHRequestState.Done);
		
		super.finish();
	}
	
	
	/////////////////////////////////////////////////
	///////// PHAPIRequest Override Methods /////////
	@Override
	public Hashtable<String, String> getAdditionalParams() {
		Hashtable<String, String> table = new Hashtable<String, String>();
		
		table.put("placement_id", (placement != null ? placement : ""));
		table.put("preload", (targetState == PHRequestState.Preloaded ? "1" : "0"));

		PHSession session = PHSession.getInstance(activityContext.get());
		table.put("stime", String.valueOf(session.getSessionTime()));
	
		return table;
	}
	
	@Override
	public void handleRequestSuccess(JSONObject response) {
	    if (JSONObject.NULL.equals(response) || response.length() == 0) return;
	    
		content = new PHContent(response);
		
		if (content.url == null) {
		    setCurrentState(PHRequestState.Done);
		} else {
		    setCurrentState(PHRequestState.Preloaded);
		}
		
		continueLoading();
	}
	
	/////////////////////////////////////////////////////////////////////
	/////////////////// Broadcast Routing Methods ///////////////////////
	
	private void registerReceiver() {
		
		// TODO: we should *unregister* the receiver eventually
		// TODO: if we don't unregister, this might lead to a null pointer exceptino
		if (applicationContext.get() != null) {
			// 
			applicationContext.get().registerReceiver(new BroadcastReceiver() {
				
				@Override
				public void onReceive(Context context, Intent intent) {
					Bundle md 	= intent.getBundleExtra(PHContentView.BROADCAST_METADATA);
					if (md == null) return;
					
					String eventStr  = md.getString(PHContentView.Detail.Event.getKey());
					if (eventStr == null) return;
					
					PHContentView.Event event = PHContentView.Event.valueOf(eventStr);
					// the tag used for identifying the phcontentview
					String tag    = md.getString(PHContentView.Detail.Tag.getKey());
					
					if (tag == null || !tag.equals(contentTag)) return; // only process if it is relevant to us
					
					// TODO: listen to didLoad or didShow?
					if (event == PHContentView.Event.DidShow) {
						PHContent content = (PHContent) md.getParcelable(PHContentView.Detail.Content.getKey());
						
						if (content_listener != null)
							content_listener.didDisplayContent(PHPublisherContentRequest.this, content);
						
					} else if (event == PHContentView.Event.DidDismiss) {
						PHDismissType type = PHDismissType.valueOf(
														md.getString(
																	PHContentView.Detail.CloseType.getKey()
																			));
					
						if(content_listener != null) 
							content_listener.didDismissContent(PHPublisherContentRequest.this, type);
						
					} else if (event == PHContentView.Event.DidFail) {
						String error = md.getString(PHContentView.Detail.Error.getKey());
					
						if(failure_listener != null) 
							failure_listener.didFail(PHPublisherContentRequest.this, error);
						
					} else if (event == PHContentView.Event.DidUnlockReward) {
						PHReward reward = md.getParcelable(PHContentView.Detail.Reward.getKey());
					
						if (reward_listener != null) 
							reward_listener.unlockedReward(PHPublisherContentRequest.this, reward);
						
					} else if (event == PHContentView.Event.DidMakePurchase) {
						PHPurchase purchase = md.getParcelable(PHContentView.Detail.Purchase.getKey());
					
						if (purchase_listener != null) 
							purchase_listener.shouldMakePurchase(PHPublisherContentRequest.this, purchase);
					}
				
				}
			}, new IntentFilter(PHContentView.BROADCAST_INTENT));
			}
	}

}
