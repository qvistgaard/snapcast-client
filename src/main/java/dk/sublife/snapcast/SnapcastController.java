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
import dk.sublife.snapcast.types.Server;
import dk.sublife.snapcast.types.ServerStatus;
import dk.sublife.snapcast.types.Stream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SnapcastController {
	private final JsonRpcEventClient client;
	private final Map<String,Client> clientMap = new HashMap<>();
	private final Map<String,Stream> streamMap = new HashMap<>();
	private final List<SnapcastUpdateListener> updateListeners = new ArrayList<>();
	private Server server;

	public SnapcastController(JsonRpcEventClient client) {
		this.client = client;
	}

	public void connect() throws IOException, InterruptedException {
		client.addNotificationHandler(new SnapcastMethodClientOnConnect(clientMap, this));
		client.addNotificationHandler(new SnapcastMethodClientOnDisconnect(clientMap, this));
		client.addNotificationHandler(new SnapcastMethodClientOnUpdate(clientMap, this));
		client.connect();
		final ServerStatus serverStatus = client.sendRequestAndReadResponse(
				"Server.GetStatus", null, ServerStatus.class);
		server = serverStatus.getServer();
		serverStatus.getClients().forEach(c -> clientMap.put(c.getHost().getMac(), c));
		serverStatus.getStreams().forEach(s -> streamMap.put(s.getId(), s));
	}

	@SuppressWarnings("unused")
	public Collection<Stream> getStreams(){
		return Collections.unmodifiableCollection(streamMap.values());
	}

	public SnapcastClientController getClient(final String mac){
		return new SnapcastClientController(client, mac, clientMap, updateListeners);
	}

	@SuppressWarnings("unused")
	public void addUpdateListener(final SnapcastUpdateListener updateListener){
		updateListeners.add(updateListener);
	}
}
