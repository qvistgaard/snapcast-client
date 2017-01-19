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
package dk.sublife.snapcast;

import dk.sublife.rpc.JsonRpcEventClient;
import dk.sublife.snapcast.types.Client;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SnapcastClientController {

	private final JsonRpcEventClient connection;
	private final String mac;
	private final Map<String, Client> clientMap;
	private final List<SnapcastUpdateListener> updateListeners;

	public SnapcastClientController(final JsonRpcEventClient connection,
	                                final String mac,
	                                final Map<String, Client> clientMap,
	                                final List<SnapcastUpdateListener> updateListeners) {
		this.connection = connection;
		this.mac = mac;
		this.clientMap = clientMap;
		this.updateListeners = updateListeners;
	}

	private Client client(){
		return clientMap.get(mac);
	}

	private Map<String, Object> createParamsObject(){
		Map<String, Object> params = new HashMap<>();
		params.put("client", mac);
		return params;
	}

	void notifyUpdateListeners(){
		updateListeners.forEach(u -> u.updateClient(this));
	}

	public SnapcastClientController volume(final Integer volume) throws IOException, InterruptedException {
		final Map<String, Object> params = createParamsObject();
		params.put("volume", volume);
		connection.sendRequestAndReadResponse("Client.SetVolume", params, Integer.class);
		notifyUpdateListeners();
		return this;
	}

	public Integer volume(){
		return client().getConfig().getVolume().getPercent();
	}

	public SnapcastClientController mute(final Boolean muted) throws IOException, InterruptedException {
		final Map<String, Object> params = createParamsObject();
		params.put("mute", muted);
		final Boolean aBoolean = connection.sendRequestAndReadResponse("Client.SetMute", params, Boolean.class);
		client().getConfig().getVolume().setMuted(aBoolean);
		notifyUpdateListeners();
		return this;
	}

	public Boolean isMuted(){
		return client().getConfig().getVolume().getMuted();
	}

	public SnapcastClientController stream(final String streamId) throws IOException, InterruptedException {
		final Map<String, Object> params = createParamsObject();
		params.put("id", streamId);
		connection.sendRequestAndReadResponse("Client.SetStream", params, Boolean.class);
		notifyUpdateListeners();
		return this;
	}

	public String stream(){
		return client().getConfig().getStream();
	}

	public SnapcastClientController name(final String name) throws IOException, InterruptedException {
		final Map<String, Object> params = createParamsObject();
		params.put("name", name);
		connection.sendRequestAndReadResponse("Client.SetName", params, String.class);
		notifyUpdateListeners();
		return this;
	}

	public String name(){
		return client().getConfig().getName();
	}


	public SnapcastClientController latency(final Integer latency) throws IOException, InterruptedException {
		final Map<String, Object> params = createParamsObject();
		params.put("latency", latency);
		connection.sendRequestAndReadResponse("Client.SetLatency", params, Boolean.class);
		notifyUpdateListeners();
		return this;
	}

	public Integer latency(){
		return client().getConfig().getLatency();
	}

}