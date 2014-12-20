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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.amazon.device.messaging.ADMMessageHandlerBase;
import com.amazon.device.messaging.ADMMessageReceiver;

public class ADMMessageHandler extends ADMMessageHandlerBase {

	public static class Receiver extends ADMMessageReceiver {
        public Receiver() {
            super(ADMMessageHandler.class);
        }
    }
	
	public ADMMessageHandler() {
		super("ADMMessageHandler");
	}

	@Override
	protected void onMessage(Intent intent) {
		final Bundle extras = intent.getExtras();
		GenerateNotification.fromBundle(this, extras);
	}

	@Override
	protected void onRegistered(String newRegistrationId) {
		Log.i(GameThrive.TAG, "ADM registartion ID: " + newRegistrationId);
		PushRegistratorADM.registeredCallback.complete(newRegistrationId);
	}

	@Override
	protected void onRegistrationError(String error) {
		Log.e(GameThrive.TAG, "ADM:onRegistrationError: " + error);
		PushRegistratorADM.registeredCallback.complete(null);
	}

	@Override
	protected void onUnregistered(String info) {
		Log.i(GameThrive.TAG, "ADM:onUnregistered: " + info);
	}
}