/*
 * Copyright 2017 Steffen Folman Sørensen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.sublife.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

class JsonRpcResponseRegistry {
	Logger logger = LoggerFactory.getLogger(JsonRpcEventClient.class);

	private final Map<Integer,MessageNotification> notificationMap = new HashMap<>();

	void addThreadListener(final MessageNotification notification, final Integer requestId){
		notificationMap.put(requestId, notification);
	}

	void setupResponseListener(final Integer requestId){
		addThreadListener(new MessageNotification(requestId), requestId);
	}

	JsonNode waitForResponse(final Integer requestId) throws InterruptedException {
		final MessageNotification messageNotification = notificationMap.get(requestId);
		synchronized (messageNotification) {
			messageNotification.wait();
			return messageNotification.getResponse();
		}
	}

	Boolean notifyThreadListener(final Integer requestId, JsonNode jsonNode){
		final MessageNotification notification = notificationMap.get(requestId);
		if(notification != null){
			synchronized (notification) {
				notification.setResponse(jsonNode);
				notification.notify();
			}
			notificationMap.remove(requestId);
			return true;
		} else {
			logger.error("No request listener found for request id: {}", requestId);
			return false;
		}
	}

	private class MessageNotification {
		private final Integer id;
		private JsonNode response;

		private MessageNotification(Integer id) {
			this.id = id;
		}

		public JsonNode getResponse() {
			return response;
		}

		public void setResponse(JsonNode response) {
			this.response = response;
		}
	}
}
