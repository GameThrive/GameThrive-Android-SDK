/**
 * Modified MIT License
 * 
 * Copyright 2014 GameThrive
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

import com.amazon.device.messaging.ADM;

import android.content.Context;
import android.util.Log;

class PushRegistratorADM implements PushRegistrator {
	
	private static RegisteredHandler registeredCallback;
	private static boolean callbackSuccessful = false;
	
	@Override
	public void registerForPush(final Context context, String noKeyNeeded, final RegisteredHandler callback) {
		registeredCallback = callback;
		new Thread(new Runnable() {
    		public void run() {
				final ADM adm = new ADM(context);
				String registrationId = adm.getRegistrationId();
				if (registrationId == null)
					adm.startRegister();
				else {
					Log.i(GameThrive.TAG, "ADM Already registed with ID:" + registrationId);
					callback.complete(registrationId);
				}
				
				try { Thread.sleep(30000); } catch (InterruptedException e) {}
				if (!callbackSuccessful) {
					Log.e(GameThrive.TAG, "com.gamethrive.ADMMessageHandler timed out, please check that your have the reciever, service, and your package name matches(NOTE: Case Sensitive) per the GameThrive instructions.");
					fireCallback(null);
				}
    		}
		}).start();
	}
	
	static void fireCallback(String id) {
		callbackSuccessful = true;
		registeredCallback.complete(id);
	}
}