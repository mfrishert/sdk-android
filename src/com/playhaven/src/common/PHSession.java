package com.playhaven.src.common;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.playhaven.src.utils.PHStringUtil;


/**
 * A session is defined as the in-game time spent by a user between two {@link PHPublisherOpenRequest}. This class is a singleton.
 * 
 * @author andreiciortea
 *
 */
public class PHSession {
    
    public static final String SSUM_PREF   = "com_playhaven_time_in_game_ssum";
    public static final String SCOUNT_PREF = "com_playhaven_time_in_game_scount";
    
    private long mTotalTime;
    private long mSessionTime;
    private long mCurTime;
    private long mSessionCount;
    
    private boolean mSessionStarted;
    private boolean mSessionPaused;

    private static PHSession mSession = null;
    
    private PHSession(Context context) {
        inflate(context);
        mSessionStarted = false;
        mSessionPaused = true;
    }
    
    public static PHSession getInstance(Context context) {
        if (mSession == null) {
            mSession = new PHSession(context);
        }
        
        return mSession;
    }
    
    /** Used for unit testing only. Re-creates the shared instance. It returns the new session.*/
    public static PHSession regenerateInstance(Context context) {
    	getInstance(context).clear(context);
    	getInstance(context).reset();
    	
    	mSession = null;
    	// create a new session
    	return getInstance(context);
    }
    
    /**
     * This method is called when a new Playhaven open request is sent. It is called just *before the
     * actual request data is sent. You should not call this method explicitly.
     */
    public void start() {
        PHStringUtil.log("Starting a new session.");
        
        if (mSessionStarted) {
            // We already have a running session, save intermediary results
            mTotalTime += getSessionTime();
            mSessionCount++;
        }
        
        mSessionTime = 0;
        mCurTime = SystemClock.uptimeMillis();
        
        mSessionStarted = true;
    }
    
    /**
     * This method is called after a new Playhaven session was successfully opened. You should not call this method explicitly.
     */
    public void startAndReset() {
        start();
        mTotalTime = 0;
        mSessionCount = 0;
    }
    
    /**
     * This method is meant for debugging/testing purposes only.
     */
    public void reset() {
        mTotalTime = 0;
        mSessionCount = 0;
        mSessionTime = 0;
        mCurTime = SystemClock.uptimeMillis();
        
        mSessionStarted = false;
        mSessionPaused = true;
    }
    
    /**
     * Returns the duration of the current session in seconds.
     * 
     * @return Time in seconds of current session duration, or -1 if there is no session started.
     */
    public long getSessionTime() {
        return (mSessionTime + getLastElapsedTime());
    }
    
    /**
     * Returns the total time spent in all sessions since the last successful open request. This value is reset after sending a successful open requests.
     * 
     * @return Time in seconds of the duration of all sessions since the last successful open request.
     */
    public long getTotalTime() {
        return mTotalTime + getSessionTime();
    }
    
    /**
     * Returns the number of sessions since last successful open request. This value is reset after sending a successful open requests. 
     * 
     * @return Total sessions since the last successful open request.
     */
    public long getSessionCount() {
        return mSessionCount;
    }
    
    /**
     * Register an activity for monitoring. You should always call this method from the activity's onResume() callback.
     */
    public static void register(Activity activity) {
        if (activity == null) {
            return;
        }
        
        mSession = getInstance(activity);
        
        if (mSession.mSessionPaused) {
            mSession.resumeSession();
        }
    }
    
    /**
     * Unregister an activity from monitoring. You should always call this method from the activity's onPause() callback.
     */
    public static void unregister(Activity activity) {
        if (activity == null) {
            return;
        }
        
        mSession = getInstance(activity);
        
        if (!mSession.mSessionPaused) {
            mSession.pauseSession();
            
            // if the user switches activities, we should save
            if (mSession.mSessionStarted && activity.isFinishing()) {
                mSession.save(activity);
            }
        }
    }
    
    private long getLastElapsedTime() {
    	return (!mSessionStarted || mSessionPaused) 
                    ? 0
                    : ((SystemClock.uptimeMillis() - mCurTime) / 1000);
    }
    
    private void pauseSession() {
        mSessionTime = getSessionTime();
        mSessionPaused = true;
    }
    
    private void resumeSession() {
        mCurTime = SystemClock.uptimeMillis();
        mSessionPaused = false;
    }
    
    private void inflate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        mTotalTime = prefs.getLong(SSUM_PREF, 0);
        mSessionCount = prefs.getLong(SCOUNT_PREF, 0);
    }
    
    public void clear(Context context) {
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    	
    	SharedPreferences.Editor editor = prefs.edit();
        
        editor.remove(SSUM_PREF);
        editor.remove(SCOUNT_PREF);
        
        editor.commit();
    }
    
    private void save(Context context) {
        if (!mSessionStarted) {
            return;
        }
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putLong(SSUM_PREF, mTotalTime + getSessionTime());
        editor.putLong(SCOUNT_PREF, (mSessionCount + 1));
        
        editor.commit();
    }
}
