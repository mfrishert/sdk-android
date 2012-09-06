package com.playhaven.src.publishersdk.content;

import android.os.Parcel;
import android.os.Parcelable;

/** Simple container class to hold reward meta data. It is parcelable so that we can pass between activities.*/
public class PHReward implements Parcelable {
	public String name;
	
	public int quantity;
	
	public String receipt;
	
	public PHReward() {
		//Default constructor
	}
	
	////////////////////////////////////////////////////
	////////////////// Parcelable Methods //////////////
	public static final Parcelable.Creator<PHReward> CREATOR = new Creator<PHReward>() {
		
		@Override
		public PHReward[] newArray(int size) {
			return new PHReward[size];
		}
		
		@Override
		public PHReward createFromParcel(Parcel source) {
			return new PHReward(source);
		}
	};
	
	public PHReward(Parcel in) {
		this.name = in.readString();
		
		if (this.name != null && this.name.equals(PHContent.PARCEL_NULL))
			this.name = null;
		
		this.quantity = in.readInt();
		
		this.receipt = in.readString();
		
		if (this.receipt != null && this.receipt.equals(PHContent.PARCEL_NULL))
			this.receipt = null;
	}
	
	public int describeContents() {
		return 0;
	}
	
	public void writeToParcel(Parcel out, int flags) {
		out.writeString(name == null ? PHContent.PARCEL_NULL : name);
		out.writeInt(quantity);
		out.writeString(receipt == null ? PHContent.PARCEL_NULL : receipt);
	}
}
