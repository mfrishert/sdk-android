package com.playhaven.sampleapp.examples;

import org.json.JSONObject;

import android.os.Bundle;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.playhaven.androidsdk.R;
import com.playhaven.sampleapp.billing.PurchaseHelper;
import com.playhaven.src.common.PHAPIRequest;
import com.playhaven.src.publishersdk.content.PHContent;
import com.playhaven.src.publishersdk.content.PHPublisherContentRequest;
import com.playhaven.src.publishersdk.content.PHPurchase;
import com.playhaven.src.publishersdk.content.PHReward;
import com.playhaven.src.publishersdk.metadata.PHNotificationView;
import com.playhaven.src.utils.PHStringUtil;

/** Simple view used for testing content view.
 * TODO: should we refactor the billing nonsense into an innerclass or a 
 * subclass?
 */
public class PublisherContentView extends ExampleView implements PHPublisherContentRequest.ContentDelegate,
																 PHPublisherContentRequest.FailureDelegate,
																 PHPublisherContentRequest.RewardDelegate,
																 PHPublisherContentRequest.PurchaseDelegate {	
	private PHPublisherContentRequest request;
	
	private EditText placementTxt;
	
	private PurchaseHelper mPurchaseHelper;

	
	@Override
	public void onCreate(Bundle savedInstance) {
		super.onCreate(savedInstance);
		
		mPurchaseHelper = new PurchaseHelper(this);
		
		setTitle("Content Request");
	}
	
	@Override
	public void shouldMakePurchase(PHPublisherContentRequest request, PHPurchase purchase) {
		mPurchaseHelper.makePurchase(purchase);
	}
	
	@Override
	public void unlockedReward(PHPublisherContentRequest request, PHReward reward) {
		// Pass
	}
	

	@Override
	protected void addTopbarItems(LinearLayout topbar) {
		
		placementTxt = new EditText(this);
		placementTxt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, .9f)); // smaller weight means bigger?
		placementTxt.setHint(R.string.default_placement);
		placementTxt.setContentDescription("placementTxt");
		
		topbar.addView(placementTxt);
		
		super.addTopbarItems(topbar); // will add start button on the right
	}
	
	@Override
	public void startRequest() {
		super.startRequest();
		
		// testing the badge
		PHNotificationView notifyView = new PHNotificationView(this, placementTxt.getText().toString());
		notifyView.setBackgroundColor(0xFF020AFF);
		notifyView.refresh();
		
		super.addMessage("Notification View: ", notifyView);
		

		// pass ourselves as the delegate AND the context
		request = new PHPublisherContentRequest(this, placementTxt.getText().toString());
		request.setOnPurchaseListener(this);
		
		request.preload();
		request.send();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		mPurchaseHelper.unbindService();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		mPurchaseHelper.unbindPurchaseObserver();
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		mPurchaseHelper.bindPurchaseObserver();
	}
	@Override
	public void onResume() {
		super.onResume();
		if (PHPublisherContentRequest.didDismissContentWithin(2000)) { // can actually be less than 2 seconds, all we want is enough time for onResume to be called
			PHStringUtil.log("Resumed after displaying content");
			return; 
		}
		
		PHStringUtil.log("Resumed PHPublisherView regularly");
	}
	
	///////////////////////////////////////////////////////////
	/////////// PHPublisherContent Request Delegate ///////////
	public void requestSucceeded(PHAPIRequest request, JSONObject responseData) {
		//TODO: do nothing here since we're a content delegate and don't care about these.
		
	}

	public void requestFailed(PHAPIRequest request, Exception e) {
		//TODO: do nothing here since we're a content delegate and don't care about these.		
	}

	@Override
	public void willGetContent(PHPublisherContentRequest request) {
		PHStringUtil.log("Will get content...");
		super.addMessage("Starting content request...");
	}

	@Override
	public void willDisplayContent(PHPublisherContentRequest request, PHContent content) {
		String message = String.format("Recieved content: %s. \n-------\npreparing for display", content);
		super.addMessage(message);
	}

	@Override
	public void didDisplayContent(PHPublisherContentRequest request, PHContent content) {
		String message = String.format("Displayed Content: %s", content);
		super.addMessage(message);		
	}

	@Override
	public void didDismissContent(PHPublisherContentRequest request, PHPublisherContentRequest.PHDismissType type) {
		String message = String.format("User dismissed request: %s of type: %s", request, type.toString());
		super.addMessage(message);
		
	}

	@Override
	public void didFail(PHPublisherContentRequest request, String error) {
		String message = String.format(" Failed with error: %s", error);
		super.addMessage(message);
	}

	@Override
	public void contentDidFail(PHPublisherContentRequest request, Exception e) {
		String message = String.format(" Content failed with error; %s", e);
		super.addMessage(message);
		
	}
	
	

	

}
