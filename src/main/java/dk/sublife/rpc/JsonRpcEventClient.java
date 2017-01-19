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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class JsonRpcEventClient {
	private Logger logger = LoggerFactory.getLogger(JsonRpcEventClient.class);

	private final String hostname;
	private final Integer port;

	private final ObjectMapper objectMapper;
	private final Random random = new Random();
	private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();
	private final JsonRpcResponseRegistry responseRegistry = new JsonRpcResponseRegistry();
	private final Map<String,JsonRpcNotificationHandler> notificationHandlerMap = new HashMap<>();

	private ConnectionThread connectionThread;
	private Socket socket;

	public JsonRpcEventClient(final String hostname, final Integer port) {
		this.hostname = hostname;
		this.port = port;
		this.objectMapper = new ObjectMapper();
	}

	public void connect() throws IOException {
		this.connectionThread = new ConnectionThread();
		this.connectionExecutor.submit(connectionThread);

		Runtime.getRuntime().addShutdownHook(new ShutdownThread());
	}

	public void addNotificationHandler(JsonRpcNotificationHandler notificationHandler){
		notificationHandlerMap.put(notificationHandler.getMethod(), notificationHandler);
	}

	public void invoke(final String method, final Object params) throws IOException {
		writeRequest(createRequest(method, params));
	}

	public <T> T sendRequestAndReadResponse(final String method, final Object params, Class<T> type) throws InterruptedException, IOException {
		final JsonNode request = createRequest(method, params);
		final int id = getRequestId(request);
		responseRegistry.setupResponseListener(id);

		writeRequest(request);

		final JsonNode jsonNode = responseRegistry.waitForResponse(id);
		return readResponse(jsonNode, type);
	}

	private void writeRequest(final JsonNode node) throws IOException {
		final String bytes = objectMapper.writeValueAsString(node);
		logger.info("Send request to server: {}", bytes);
		socket.getOutputStream().write((bytes+"\r\n").getBytes());
	}

	private <T> T readResponse(JsonNode jsonNode, Class<T> type){
		try {
			JsonParser jsonParser = objectMapper.treeAsTokens(jsonNode);
			JavaType javaType = objectMapper.getTypeFactory().constructType(type);
			return objectMapper.readValue(jsonParser, javaType);
		} catch (IOException e){
			logger.error("Failed to read response: {}", e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	private JsonNode createRequest(final String method, final Object params){
		final ObjectNode request = objectMapper.createObjectNode();
		final ObjectNode paramsJson = objectMapper.valueToTree(params);
		request.put("id", generateRandomId());
		request.put("jsonrpc", "2.0");
		request.put("method", method);
		if(paramsJson != null) {
			request.set("params", paramsJson);
		}
		return request;
	}

	private Integer getRequestId(JsonNode jsonNode){
		return jsonNode.get("id").asInt();
	}

	private Integer generateRandomId() {
		return random.nextInt(Integer.MAX_VALUE);
	}



















	private class ConnectionThread extends Thread {
		Logger logger = LoggerFactory.getLogger(ConnectionThread.class);

		private Boolean running = true;

		@Override
		public void interrupt() {
			super.interrupt();
			logger.info("Interrupt received... Closing socket.");
			running = false;
			try {
				connectionExecutor.awaitTermination(5, TimeUnit.SECONDS);
				socket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void run() {
			socket = new Socket();
			try {
				while (!isInterrupted()) {
					if (socket.isConnected() && !socket.isClosed()) {
						logger.info("Connected to control server {}:{}", hostname, port);
						BufferedReader in = new BufferedReader(
								new InputStreamReader(socket.getInputStream()));
						String inputLine;
						while ((inputLine = in.readLine()) != null && !socket.isClosed()) {
							final JsonNode jsonNode = objectMapper.readValue(inputLine.getBytes(), JsonNode.class);
							logger.info("Received message: {}", jsonNode);
							// Determine incoming message type
							if(jsonNode.has("id")){
								responseRegistry.notifyThreadListener(getRequestId(jsonNode), jsonNode.get("result"));
							} else if (jsonNode.has("method")){
								final String methodName = jsonNode.get("method").asText();
								final JsonRpcNotificationHandler method = notificationHandlerMap.get(methodName);
								if(method != null){
									final JsonNode param = jsonNode.get("params");
									final Class type = method.getType();
									method.handleNotification(readResponse(param, type));
								} else {
									logger.error("No notification handler found for: {}", methodName);
								}
							}
						}
						socket.close();
					} else {
						try {
							logger.info("Connecting to control server {}:{}...", hostname, port);
							socket = new Socket();
							socket.connect(new InetSocketAddress(hostname, port), 30);
						} catch (IOException e) {
							logger.info("Connection lost to control server {}:{} trying to reconnect...", hostname, port);
							Thread.sleep(5000);
						}
					}
				}
				logger.info("Connection closed");
			} catch (InterruptedException e){
				logger.info("Interrupted");
			} catch (IOException e) {
				logger.info("Connection closed: {}", e.getMessage());
				e.printStackTrace();
			}
		}
	}



	private class ShutdownThread extends Thread {
		@Override
		public void run() {
			super.run();
			connectionThread.interrupt();
		}
	}

}
