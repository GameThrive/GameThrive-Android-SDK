/**
 * Copyright 2014 GameThrive
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gamethrive;

import java.io.UnsupportedEncodingException;

import org.apache.http.entity.StringEntity;
import org.json.JSONObject;

import android.content.Context;

import com.loopj.android.http.*;

class GameThriveRestClient {
  private static final String BASE_URL = "https://gamethrive.com/api/v1/";
  private static final int TIMEOUT = 20000;
  
  private static AsyncHttpClient client = new AsyncHttpClient();
  private static SyncHttpClient clientSync = new SyncHttpClient();
  
  static {
	  // setTimeout method = socket timeout
	  // setMaxRetriesAndTimeout = sleep between retries
	  client.setTimeout(TIMEOUT);
	  client.setMaxRetriesAndTimeout(1, TIMEOUT);
	  
	  clientSync.setTimeout(TIMEOUT);
	  clientSync.setMaxRetriesAndTimeout(1, TIMEOUT);
  }
  
  static void put(Context context, String url, JSONObject jsonBody, AsyncHttpResponseHandler responseHandler) throws UnsupportedEncodingException {
	  StringEntity entity = new StringEntity(jsonBody.toString());
      client.put(context, BASE_URL + url, entity, "application/json", responseHandler);
  }

  static void post(Context context, String url, JSONObject jsonBody, AsyncHttpResponseHandler responseHandler) throws UnsupportedEncodingException {
	  StringEntity entity = new StringEntity(jsonBody.toString());
      client.post(context, BASE_URL + url, entity, "application/json", responseHandler);
  }
  
  
  static void putSync(Context context, String url, JSONObject jsonBody, AsyncHttpResponseHandler responseHandler) throws UnsupportedEncodingException {
	  StringEntity entity = new StringEntity(jsonBody.toString());
	  clientSync.put(context, BASE_URL + url, entity, "application/json", responseHandler);
  }

  static void postSync(Context context, String url, JSONObject jsonBody, AsyncHttpResponseHandler responseHandler) throws UnsupportedEncodingException {
	  StringEntity entity = new StringEntity(jsonBody.toString());
	  clientSync.post(context, BASE_URL + url, entity, "application/json", responseHandler);
  }
  
  
  // Call getOnNewThread when you can't block the thread your calling this from (like calling from a UI hread) and
  // need to make sure the ResponseHandler gets called in cases where the thread which called this could die before getting a response.
  static void getOnNewThread(final Context context, final String url, final AsyncHttpResponseHandler responseHandler) {
	  new Thread(new Runnable() {
	      public void run() {
	    	  clientSync.get(context, BASE_URL + url, responseHandler);
	   		}
	  }).start();
  }
  

}