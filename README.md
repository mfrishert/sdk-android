PlayHaven Android SDK 1.12.1
====================

PlayHaven is a mobile game LTV-maximization platform which helps you take control of the business of your games.

Acquire, retain, re-engage, and monetize your players with the help of PlayHaven's powerful marketing platform. Integrate once and embrace the flexibility of the web as you build, schedule, deploy, and analyze your in-game promotions and monetization in real-time through PlayHaven's easy-to-use, web-based dashboard. 

An API token and secret is required to use this SDK. These tokens uniquely identify your app to PlayHaven and prevent others from making requests to the API on your behalf. To get a token and secret, please visit the [PlayHaven Dashboard](https://dashboard.playhaven.com).

If you have any questions, visit the [Help Center](http://help.playhaven.com) or contact us at [support@playhaven.com](mailto:support@playhaven.com).  We also recommend reviewing our [Optimization Guides](http://help.playhaven.com/customer/portal/topics/113947-optimization-guides/articles) to learn the best practices and get the most out of your PlayHaven integration.

Table of Contents
=================

* [Installation](#installation)
    * [JAR Integration](#jar-integration)
* [Usage](#usage)
    * [Recording Game Opens](#recording-game-opens)
    * [Showing Content](#displaying-content)
    * [Optimizing for Performance](#optimizing-for-performance)
    * [Notification Badges](#displaying-a-notification-badge)
    * [Unlocking Rewards](#unlocking-rewards)
    * [Handling Virtual Goods Purchases](#handling-virtual-goods-purchases)
    * [Using Connection Targeting](#using-connection-targeting)
    * [Tracking User Activity](#tracking-user-activity)
* [Callbacks](#callbacks)
    * [PHPublisherOpenRequest delegate](#phpublisheropenrequest-delegates)
    * [PHPublisherContentRequest delegates](#phpublishercontentrequest-delegates)
* [Tips n' Tricks](#tips-and-tricks)
    * [didDismissContentWithin](#diddismisscontentwithintimerange)
* [Integration Test Console](#integration-test-console-overview)


Installation
============

Integrating the PlayHaven Android SDK is dead simple and should take no more than a minute.

**Note:** The PlayHaven SDK requires a minimum of Android 2.2 (API Level 8). If you are developing for API levels including those below 8 please see the Android developer section [here] (http://developer.android.com/guide/google/play/publishing/multiple-apks.html) .

**Note:** If you are developing your game using Unity, this instructions are irrelevant and you should use the PlayHaven Unity SDK located [here](https://github.com/playhaven/sdk-unity).

### JAR Integration

1. Download the PlayHaven SDK [here](http://playhaven-sdk-builds.s3.amazonaws.com/android/jars/playhaven-1.12.1.jar) and ensure you have the latest version of the [Android Developer Tools installed](http://developer.android.com/sdk/eclipse-adt.html#updating).

2. Install the SDK into your project.
    1. If a __libs__ folder doesn't already exist, create one in your project root. Android will automatically recognize it.
        
        <img src="http://i1249.photobucket.com/albums/hh509/samatplayhaven/ScreenShot2012-06-13at111136AM.png"  style="padding: 5px; margin-bottom: 20px;" />
        
        <img src="http://i1249.photobucket.com/albums/hh509/samatplayhaven/ScreenShot2012-06-13at105708AM.png"  style="padding: 5px" />
        
    2. Drag the PlayHaven SDK JAR file you downloaded into the __libs__ folder. If prompted, select 'Copy files' as opposed to 'Link to files'.
        
        <img src="http://i1249.photobucket.com/albums/hh509/samatplayhaven/ScreenShot2012-06-13at105747AM3.png" style="padding: 5px" />
        
    3. Add the appropriate import statement to your source files:
        ```java
        import com.playhaven.*;
        ```

3. Set the API keys you received from the [dashboard](http://www.dashboard.playhaven.com). Although you can set these wherever you wish, we advise the root `Activity`.

 ```java
 PHConfig.token = "your token";
 PHConfig.secret = "your secret";
 ```

Make sure prior to compiling that you've imported `PHConfig`. This may be done by:

 ```java
 import com.playhaven.src.common.PHConfig;
 ```

4. Add the ad view to your `AndroidManifest.xml` file.

**Note:** The activity XML tag must be placed inside the application tag.

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.INTERNET"/>

<activity android:name="com.playhaven.src.publishersdk.content.PHContentView" android:theme="@android:style/Theme.Dialog" android:windowSoftInputMode="adjustResize"></activity>
```

----------

Usage
=====

**This guide assumes you have followed the installation instructions above.**

## Recording game opens

**Purpose:** helps the server track game usage. 

**Notes:** Make sure to pass in a `Context` (usually an `Activity`) to the `PHPublisherOpenRequest` constructor.

```java
PHPublisherOpenRequest request = new PHPublisherOpenRequest([your context]);

request.send();
```

## Displaying Content

**Purpose:** Displays a fullscreen content unit with the placement specified. Content units include ads, announcements and cross-promotions. Please see [here] (http://help.playhaven.com/customer/portal/articles/683511-what-features-does-playhaven-offer-) for a complete list of content units PlayHaven has available. 

**Notes:** Make sure to provide the `PHPublisherContentRequest` constructor with a valid `Context` (usually an `Activity`). You can specify placements in the PlayHaven [dashboard](http://www.dashboard.playhaven.com]).

**Notes:** If you wish to display a content unit when an activity loads it is recommended that you do so by placing it in the `onResume` method of the `Activity`. This will cause the content unit to be displayed whenever the `Activity` comes to the foreground. This allows for maximum flexibility in tuning the display of the content unit.

```java
PHPublisherContentRequest request = new PHPublisherContentRequest([your context], "your placement");

request.send();
```

### Optimizing for Performance

The PlayHaven SDK will automatically download and store a number of content templates after a successful `PHPublisherOpenRequest` (see [Recording Game Opens](#recording-game-opens)).

To further improve the responsiveness of your ads, you may choose to preload a content unit for a given placement. To do so, simply call `preload()` to start the network request without displaying the advertisement. When you are prepared to display the advertisement, call `send()` as usual. This feature is especially important for slow mobile data connections.

```java
PHPublisherContentRequest request = new PHPublisherContentRequest([your context], "your placement");
// Set your listeners, we only set the content listener here.
request.setOnContentListener([your content delegate]);
request.preload();

// ...
// ...
// ...

// Elsewhere in the code...
request.send();
```


## Displaying a notification badge

**Purpose:** Displays a small badge with a number indicating the number of new games a user can view.

**Notes:** You may place this anywhere in your app but we've found the best solution is to add it to an button which then launches a `PHPublisherContentRequest` with a "more_games" placement. Once a user clicks on the button you should call `clear()` on the notification view to reset the badge number.

```java
PHNotificationView notifyView = new PHNotificationView([your context], "your placement");

[your button/view/layout].addView(notifyView);

notifyView.refresh();
```

## Unlocking Rewards

**Purpose:** Allows your game to respond when the users unlocks rewards you have configured in the [dashboard](http://www.dashboard.playhaven.com]).

**Notes:** You must implement the `PHPublisherContentRequest.RewardDelegate` interface, and set the delegate object on a `PHPublisherContentRequest` object in order to receive this callback. This requires implementing the method `unlockedReward` in your delegate object. See the [Callbacks](#callbacks) section below for more information.

```java
public void unlockedReward(PHPublisherContentRequest request, PHReward reward) {
    ... your handling code here...
}
```

The `PHReward` object has the following useful properties:

* __name:__     The reward's name (specified in the dashboard)
* __quantity:__ The reward's amount (specified in the dashboard) 


## Handling Virtual Goods Purchases

The PlayHaven Android SDK supports "virtual good promotion" (or VGP) which allows you to advertise virtual products available *within* a game. For example, your puzzle game may offer additional levels or special "skins" for a nominal fee. 

PlayHaven offers full-screen content units which advertise virtual goods. When a user clicks an ad, the PlayHaven Android SDK sends a "callback" to your game.

__Note: You must configure your "in app purchase" items on the PlayHaven Dashboard *after* creating them in your Android Market dashboard.__

The PlayHaven SDK *only* handles two elements of a user's complete transaction:

1. Notifying your game when a user clicks "purchase" in an ad
2. Tracking the IAP purchase

**Note: The PlayHaven Android SDK does not handle interaction with the Android Billing Service as implementation details vary by game. Please refer to the [Android Billing Guide](http://developer.android.com/guide/google/play/billing/index.html) for further information.**

The first step notifies your game through the `PHPublisherContentRequest.PurchaseDelegate` interface. You must implement the interface's single method:

```java
public void shouldMakePurchase(PHPublisherContentRequest request, PHPurchase purchase) {
    .... make purchase here .... 
}
```

In this callback, you should complete the actual transaction through the Android Billing Service. You should also save a reference to the `PHPurchase` object for later use.


The PlayHaven Android SDK simply adds an intermediate step to the transaction process:
<img src="http://i990.photobucket.com/albums/af25/flashpro/playhave_billing_diagram.png" style="padding: 5px; width: 500px; margin-bottom: 20px; margin-top: 20px" />


Once the Android Billing Service has confirmed (or canceled) the user's purchase, you must call:

```java
purchase.reportResolution([purchase resolution], [your activity]);
```

on the `purchase` object passed into the *original* `shouldMakePurchase(...)` callback. Your reported resolution should correspond to the result of the Android Billing transaction (canceled, completed, failed, etc.) along with a valid `Context`.

Finally, you must report the "in app purchase" transaction to the sever with a `PHPublisherIAPTrackingRequest`:

```java
PHPublisherIAPTrackingRequest trackingRequest = new PHPublisherIAPTrackingRequest([your context], purchase)
trackingRequest.send();
```

Again, you should have a reference to the `puchase` object from `shouldMakePurchase(...)`.

You're done!

__Potential Pitfall: The `.reportResolution(...)` call must happen before sending the `PHPublisherIAPTrackingRequest`__


## Using Connection Targeting

In order to use connection targeting, include the `ACCESS_NETWORK_STATE` permission in your `AndroidManifest.xml` file.

```xml
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Tracking User Activity

The PlayHaven Android SDK makes tracking user activity simple. Register an Android activity for tracking by calling `PHSession.register([your activity])` in the `onResume()` handler and `PHSession.unregister([your activity])` in the `onPause()` handler of your activity.

**Note:** Game opens must be recorded for user activity tracking to function (see [Recording Game Opens](#recording-game-opens)).

**Note:** Include the `unregister()` and `register()` calls in all relevant activities.

```java
@Override
protected void onResume() {
    super.onResume();
    PHSession.register(this);
    
    ... your code here ...
}

@Override
protected void onPause() {
    super.onPause();
    PHSession.unregister(this);
    
    ... your code here ...
}
```

-------------
## Callbacks


Every type of request in the PlayHaven Android SDK has a special "delegate" which may be set to receive "callbacks" as the request progresses. You can find more information regarding the delegate pattern [here](http://en.wikipedia.org/wiki/Delegation_pattern).

You must implement the appropriate delegate *interface* from the list below and then add your object as a delegate. Most often the root `Activity` should handle the callbacks.

Once you've implemented the appropriate callbacks you must set the delegate on the individual request before sending:

```java
PHPublisherOpenRequest request = new PHPublisherOpenRequest([your context]);
request.delegate = [your delegate (usually an Activity)]
request.send();
```

### PHPublisherContentRequest delegates

**Note:** There are several delegate *interfaces* for a `PHPublisherContentRequest`. You should implement those which provide relevant callbacks.

1. FailureDelegate
2. CustomizeDelegate
3. RewardDelegate
4. ContentDelegate
5. PurchaseDelegate

When working with the multiple delegates in `PHPublisherContentRequest`:

```java
PHPublisherContentRequest request = new PHPublisherContentRequest([your context (usually Activity)], "your placement");
request.setOnFailureListener([your failure delegate]);
request.setOnCustomizeListener([your customize delegate]);
request.setOnContentListener([your reward delegate]);
request.setOnContentListener([your content delegate]);
request.setOnPurchaseListener([your purchase delegate]);
request.send();
```

* __failure of request:__ 
```java
public void didFail(PHPublisherContentRequest request, String error) {
    ... your handling code here ...
}
```

* __failure of actual ad:__
```java
public void contentDidFail(PHPublisherContentRequest request, Exception e) {
    ... your handling code here ...
}
```

* __customize the close button:__
```java
public Bitmap closeButton(PHPublisherContentRequest request, PHButtonState state) {
    ... return a custom bitmap for the given state ...
}
```

* __customize the border color:__
```java
public int borderColor(PHPublisherContentRequest request, PHContent content) {}
    ... constant from Color ...
}
```

* __clicked on a VGP ad:__
```java
public void shouldMakePurchase(PHPublisherContentRequest request, PHPurchase purchase) {}
    ... make the purchase via the Android Billing Service ...
}
```

* __unlocked a reward:__
```java
public void unlockedReward(PHPublisherContentRequest request, PHReward reward) {
    ... handle the reward in-game ...
}
```

* __make a purchase after user clicks ad:__
```java
public void shouldMakePurchase(PHPublisherContentRequest request, PHPurchase purchase) {
    ... make the real purchase through Android Billing ...
}
```

* __ad content is downloading:__
```java
public void willGetContent(PHPublisherContentRequest request) {
    ... your handling code here ...
}
```

* __ad content is going to display:__
```java
public void willDisplayContent(PHPublisherContentRequest request, PHContent content) {
    ... return a custom bitmap for the given state ...
}
```

* __ad content has been shown:__
```java
public void didDisplayContent(PHPublisherContentRequest request, PHContent content) {
    ... your handling code here ...
}
```

* __ad was dismissed:__
```java
public void didDismissContent(PHPublisherContentRequest request, PHDismissType type) {
    ... your handling code here ...
}
```

### PHPublisherOpenRequest delegates


* __successful request callback:__ 
```java
public void requestSucceeded(PHAPIRequest request, JSONObject responseData) {
    ... your handling code here ...
}
```

* __unsuccessful request callback:__
```java
public void requestFailed(PHAPIRequest request, Exception e) {
    ...your handling code here...
}
```

-------------
## Tips and Tricks


A few helpful tips on using the PlayHaven Android SDK.

### didDismissContentWithin(timerange)

This special method within `PHPublisherContentRequest` is helpful when you wish to determine if your game is resuming after showing an ad or from another app entirely. The *timerange* argument should be specified in milliseconds and we generally find that about 2 seconds (2000 milliseconds) works best. An example `onResume` handler using this feature:

```java
@Override
public void onResume() {
    super.onResume();
    if (PHPublisherContentRequest.didDismissContentWithin(2000)) { // can actually be less than 2 seconds, all we want is enough time for onResume to be called
        System.out.println("Resumed after displaying ad unit");
        return; 
    }
    
    System.out.println("Resumed after other app was shown");
}
```

-----------------------------------
##Integration Test Console Overview

PlayHaven provides an [Integration Test Console](http://console.playhaven.com). It's located at http://console.playhaven.com 
 
At the Testing Console, developers can enter their Android device ID. The Testing Console listens for events coming from the test device and displays a log of output, including successes, failures, and helpful information. The test device must be registered as test device from the PlayHaven Dashboard and "enabled" via the Publisher Dashboard. 
 
Currently one cannot "export" their console log but you can copy and paste it into a text file or spreadsheet. To search, please use Command+F.
 
To begin, enter your Android device ID and follow the "Testing Instructions" in the light blue box to view events and comments.
 
The following can currently be checked:
 
* Upon Open Request:
  * Device ID (Android device ID) 
  * Open requests sent
  * Token/secret present
  * SDK Version
* Placements:
  * Placement detection (Content request)
  * Pre-loading (Pre-loading request)
* IAP:
  * Check that pricing is present (IAP transaction request)
