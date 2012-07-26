package com.playhaven.sampleapp.billing;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import org.json.JSONObject;

import android.app.Activity;
import android.os.Handler;

import com.playhaven.sampleapp.billing.BillingService.RequestPurchase;
import com.playhaven.sampleapp.billing.BillingService.RestoreTransactions;
import com.playhaven.sampleapp.billing.Consts.PurchaseState;
import com.playhaven.sampleapp.billing.Consts.ResponseCode;
import com.playhaven.src.common.PHAPIRequest;
import com.playhaven.src.common.PHConfig;
import com.playhaven.src.publishersdk.content.PHPurchase;
import com.playhaven.src.publishersdk.purchases.PHPublisherIAPTrackingRequest;
import com.playhaven.src.utils.PHStringUtil;

public class PurchaseHelper {
	private Handler mHandler;
	
	private PHPurchaseObserver mPurchaseObserver;
	
	private PHPublisherIAPTrackingRequest trackingRequest;
	
	private BillingService mBillingService;
	
	private WeakReference<Activity> context;
	
	private HashMap<String, PHPurchase> purchases;
	
	public PurchaseHelper(Activity activity) {
		mHandler = new Handler();
		
		mPurchaseObserver = new PHPurchaseObserver(activity, mHandler);
		// don't show the market when running UI automation tests
		mPurchaseObserver.setShouldShowMarket( ! PHConfig.runningTests);
		
		ResponseHandler.register(mPurchaseObserver);
		
		mBillingService = new BillingService();
		mBillingService.setContext(activity);
		
		context = new WeakReference<Activity>(activity);
		
		purchases = new HashMap<String, PHPurchase>();
		
		
	}
	
	public void makePurchase(PHPurchase purchase) {
		purchases.put(purchase.product, purchase);
		
		mBillingService.requestPurchase(purchase.product, 
				  						Consts.ITEM_TYPE_INAPP, 
				  						null);
	}
	
	public void unbindService() {
		mBillingService.unbind();
	}
	
	public void unbindPurchaseObserver() {
		ResponseHandler.unregister(mPurchaseObserver);
	}
	
	public void bindPurchaseObserver() {
		ResponseHandler.register(mPurchaseObserver);
	}
	
	private class PHPurchaseObserver extends PurchaseObserver {
		public PHPurchaseObserver(Activity activity, Handler handler) {
			// TODO: since an inner class might this produce a memory leak?
            super(activity, handler);
        }

        @Override
        public void onBillingSupported(boolean supported, String type) {
        	PHStringUtil.log("Billing supported: " + supported);
        }

        @Override
        public void onPurchaseStateChange(PurchaseState purchaseState, String itemId,
        								  int quantity, long purchaseTime, String developerPayload) {
            
        	PHStringUtil.log("Purchase: " + itemId + " state changed: " + purchaseState + " context: " + context.get() + " purchase: " + purchases.get(itemId));
            // report the final result
        	if (context.get() != null && purchases.containsKey(itemId)) {
        		
        		
        		PHPurchase purchase = purchases.get(itemId);
        		
        		PHPurchase.Resolution res = PHPurchase.Resolution.Buy;
        		
        		switch (purchaseState) {
				case PURCHASED:
					res = PHPurchase.Resolution.Buy;
					break;
				case CANCELED:
					res = PHPurchase.Resolution.Cancel;
					break;
				default:
					break;
				}
        		
        		PHStringUtil.log("Reporting purchase resolution: " + purchase.product);
        		purchase.reportResolution(res, context.get());
        		
        		// you must call the PHPublisherIAPTracking request *after* reporting the resolution
        		trackingRequest = new PHPublisherIAPTrackingRequest(context.get(), purchase);
        		
        		trackingRequest.setDelegate(new PHAPIRequest.Delegate() {
					
					@Override
					public void requestSucceeded(PHAPIRequest request, JSONObject responseData) {
						PHStringUtil.log("Successfully reported IAP transaction to Playhaven");
						
					}
					
					@Override
					public void requestFailed(PHAPIRequest request, Exception e) {
						PHStringUtil.log("Error reporting IAP transaction to Playhaven: " + e);
						
					}
				});
        		trackingRequest.send();
        	}
        	
           
        }

        @Override
        public void onRequestPurchaseResponse(RequestPurchase request,
                ResponseCode responseCode) {
                PHStringUtil.log(request.mProductId + ": " + responseCode);
      
        }

        @Override
        public void onRestoreTransactionsResponse(RestoreTransactions request,
                ResponseCode responseCode) {
        	PHStringUtil.log("Restored Transaction with result: " + responseCode);
        	
        }
	}
}
