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

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.RequestId;
import com.amazon.device.iap.model.UserDataResponse;

class TrackAmazonPurchase {
	
	private Context context;
	
	private boolean canTrack = false;
	
	private GTPurchasingListener gtPurchasingListener;
	
	private Class<?> listnerHandlerClass;
	private Object listnerHandlerObject;
	private Field listnerHandlerField;
	
	TrackAmazonPurchase(Context context, GameThrive gameThrive) {
		this.context = context;
	    
	    try {
	    	// 2.0.1
	    	listnerHandlerClass = Class.forName("com.amazon.device.iap.internal.d");
	    	listnerHandlerObject = listnerHandlerClass.getMethod("d").invoke(null);
	    	listnerHandlerField = listnerHandlerClass.getDeclaredField("f");
	    	listnerHandlerField.setAccessible(true);
	    	
	    	gtPurchasingListener = new GTPurchasingListener(gameThrive);
	    	gtPurchasingListener.orgPurchasingListener = (PurchasingListener)listnerHandlerField.get(listnerHandlerObject);
	    	
	    	canTrack = true;
	    	setListener();
	    } catch(Throwable t) {}
	}
	
	private void setListener() {
		PurchasingService.registerListener(context, gtPurchasingListener);
	}
	
	public void checkListener() {
		if (canTrack) {
			try {
				PurchasingListener curPurchasingListener = (PurchasingListener)listnerHandlerField.get(listnerHandlerObject);
				if (curPurchasingListener != gtPurchasingListener) {
					Log.i("GameThriveSDK", "PurchasingListener changed!!!!!");
					gtPurchasingListener.orgPurchasingListener = curPurchasingListener;
					setListener();
				}
			} catch (Throwable t) {}
		}
	}
	
	private class GTPurchasingListener implements PurchasingListener {
		private static final String TAG = "GameThriveSDK-AmazonIAP";
		
		private GameThrive gameThrive;
		
		public PurchasingListener orgPurchasingListener;
		
		private RequestId lastRequestId;
		private String currentMarket;
		
		GTPurchasingListener(GameThrive gameThrive) {
			this.gameThrive = gameThrive;
		}
		
		private String marketToCurrencyCode(String market) {
			switch(market) {
				case "US":
					return "USD";
				case "GB":
					return "GBP";
				case "DE":
				case "FR":
				case "ES":
				case "IT":
					return "EUR";
				case "JP":
					return "JPY";
				case "CA":
					return "CDN";
				case "BR":
					return "BRL";
				case "AU":
					return "AUD";
			}
			
			return "";
		}

		@Override
		public void onProductDataResponse(final ProductDataResponse response) {
			try {
				switch (response.getRequestStatus()) {
					case SUCCESSFUL:
						for (final String s : response.getUnavailableSkus()) {
							Log.v(TAG, "Unavailable SKU:" + s);
						}
		
						JSONArray purchasesToReport = new JSONArray();
						final Map<String, Product> products = response.getProductData();
						for(final String key : products.keySet()) {
							Product product = products.get(key);
							Log.v(TAG, String.format("Product: %s\n Type: %s\n SKU: %s\n Price: %s\n Description: %s\n", product.getTitle(), product.getProductType(), product.getSku(), product.getPrice(), product.getDescription()));
							
							JSONObject jsonItem = new JSONObject();
							jsonItem.put("sku",  product.getSku());
							jsonItem.put("iso", marketToCurrencyCode(currentMarket));
							
							String price = product.getPrice();
							if (!price.matches("^[0-9]"))
								price = price.substring(1);
							jsonItem.put("amount", price);
							
							purchasesToReport.put(jsonItem);
						}
						gameThrive.sendPurchases(purchasesToReport, false, null);
						break;
				}
			} catch(Throwable t) {
				t.printStackTrace();
			}
			
			if (orgPurchasingListener != null && (lastRequestId == null || !lastRequestId.toString().equals(response.getRequestId().toString())))
				orgPurchasingListener.onProductDataResponse(response);
		}

		@Override
		public void onPurchaseResponse(PurchaseResponse response) {
			try {
				final PurchaseResponse.RequestStatus status = response.getRequestStatus();
	
				try {
					Log.i(TAG, "onPurchaseResponse:" + response.toJSON());
				} catch (JSONException e) {
					e.printStackTrace();
				}
				
				if (status == PurchaseResponse.RequestStatus.SUCCESSFUL) {
					currentMarket = response.getUserData().getMarketplace();
					
					Set<String> productSkus = new HashSet<String>();
					productSkus.add(response.getReceipt().getSku());
					lastRequestId = PurchasingService.getProductData(productSkus);
				}
			} catch(Throwable t)  {
				t.printStackTrace();
			}

			if (orgPurchasingListener != null)
				orgPurchasingListener.onPurchaseResponse(response);
		}

		@Override
		public void onPurchaseUpdatesResponse(final PurchaseUpdatesResponse response) {
			if (orgPurchasingListener != null)
				orgPurchasingListener.onPurchaseUpdatesResponse(response);
		}

		@Override
		public void onUserDataResponse(final UserDataResponse response) {
			if (orgPurchasingListener != null)
				orgPurchasingListener.onUserDataResponse(response);
		}
	}
}