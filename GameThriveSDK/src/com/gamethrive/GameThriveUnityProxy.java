/**
 * Modified MIT License
 * 
 * Copyright 2015 GameThrive
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by GameThrive.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.gamethrive;

import android.app.Activity;

import org.json.JSONObject;

import com.gamethrive.GameThrive.GetTagsHandler;
import com.gamethrive.GameThrive.IdsAvailableHandler;

public class GameThriveUnityProxy implements NotificationOpenedHandler {

	private GameThrive gameThrive;
	private String unitylistenerName;
	private static java.lang.reflect.Method unitySendMessage;
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public GameThriveUnityProxy(String listenerName, String googleProjectNumber, String gameThriveAppId) {
		unitylistenerName = listenerName;
		
		try {
			// We use reflection here so the default proguard config does not get an error for native apps.
			Class unityPlayerClass;
			unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
			unitySendMessage = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);
			
			gameThrive = new GameThrive((Activity)unityPlayerClass.getField("currentActivity").get(null), googleProjectNumber, gameThriveAppId, this);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
	@Override
	public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
		JSONObject outerObject = new JSONObject();
		try {
			outerObject.put("isActive", isActive);
			outerObject.put("alert", message);
			outerObject.put("custom", additionalData);
			unitySendMessage.invoke(null, unitylistenerName, "onPushNotificationReceived", outerObject.toString());
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
	
	public void onPause() {
		gameThrive.onPaused();
	}
	
	public void onResume() {
		gameThrive.onResumed();
	}
	
	public void sendTag(String key, String value) {
		gameThrive.sendTag(key, value);
	}
	
	public void sendPurchase(double amount) {
		gameThrive.sendPurchase(amount);
	}
	
	public void getTags() {
		gameThrive.getTags(new GetTagsHandler() {
			@Override
			public void tagsAvailable(JSONObject tags) {
				try {
					unitySendMessage.invoke(null, unitylistenerName, "onTagsReceived", tags.toString());
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		});
	}
	
	public void deleteTag(String key) {
		gameThrive.deleteTag(key);
	}
	
	public void idsAvailable() {
		gameThrive.idsAvailable(new IdsAvailableHandler() {
			@Override
			public void idsAvailable(String playerId, String registrationId) {
				JSONObject jsonIds = new JSONObject();
				try {
					jsonIds.put("playerId", playerId);
					if (registrationId != null)
						jsonIds.put("pushToken", registrationId);
					else
						jsonIds.put("pushToken", "");
					
					unitySendMessage.invoke(null, unitylistenerName, "onIdsAvailable", jsonIds.toString());
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		});
	}
}
