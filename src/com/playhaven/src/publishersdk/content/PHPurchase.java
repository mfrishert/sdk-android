package com.playhaven.src.publishersdk.content;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** Simple container for managing purchases. It can self-report the final result of the transaction
 * via {@link reportResolution}.
 * @author samstewart
 *
 */
public class PHPurchase implements Parcelable {
	
    public String product;
    
    public String contentview_intent;
    
    public String name;
    
    public int quantity;
    
    public String receipt;
    
    public String callback;

    public Resolution resolution;
    
    public static final String NO_CONTENTVIEW_INTENT = "com.playhaven.null";
    
    public enum Resolution {
    	
    	Buy 	("buy"),
    	Cancel  ("cancel"),
    	Error 	("error");
    	
    	private String type;
    	
    	private Resolution(String type) {
    		this.type = type;
    	}
    	
    	public String getType() {
    		return type;
    	}
    	
	}
	
    /**
     * Creates a new PHPurchase.
     * @param contentview_intent An intent <em>filter</em> string this PHPurchase will <em>use</em> when broadcasting
     * our final purchase result. This should be unique to the content view.
     */
	public PHPurchase(String contentview_intent) {
		// our callback intent filter for talking with the PHContentView
		this.contentview_intent = contentview_intent;
	}
	
	public PHPurchase() {
		this.contentview_intent = NO_CONTENTVIEW_INTENT;
	}

	
    public void reportResolution(Resolution resolution, Activity context) {
    	this.resolution = resolution;
    	
		// notify the original content view that our purchase is complete
    	// we use the contentview_intent string we were supplied by the content view
		Intent didPurchase = new Intent(contentview_intent);
		
		Bundle metadata = new Bundle();
		metadata.putParcelable(PHContentView.Detail.Purchase.getKey(), this);
		
		didPurchase.putExtra(PHContentView.BROADCAST_METADATA, metadata);
		
		context.sendBroadcast(didPurchase);
    }

    ////////////////////////////////////////////////////
	////////////////// Parcelable Methods //////////////
	public static final Parcelable.Creator<PHPurchase> CREATOR = new Creator<PHPurchase>() {
		
		@Override
		public PHPurchase[] newArray(int size) {
			return new PHPurchase[size];
		}
		
		@Override
		public PHPurchase createFromParcel(Parcel source) {
			return new PHPurchase(source);
		}
	};
	
	public PHPurchase(Parcel in) {
		this.product 			= in.readString();
		this.name 				= in.readString();
		this.quantity 			= in.readInt();
		this.receipt 			= in.readString();
		this.callback 			= in.readString();
		this.contentview_intent = in.readString();
	}
	
	public int describeContents() {
		return 0;
	}
	
	public void writeToParcel(Parcel out, int flags) {
		out.writeString(product);
		out.writeString(name);
		out.writeInt(quantity);
		out.writeString(receipt);
		out.writeString(callback);
		out.writeString(contentview_intent);
	}
}

