/*
 * Copyright (C) 2011 lightcouch.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lightcouch;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

import org.apache.commons.codec.Charsets;
import org.apache.http.client.methods.HttpGet;
import org.lightcouch.ChangesResult.Row;

import com.google.gson.Gson;

/**
 * <p>Contains the Change Notifications API, supports <i>normal</i> and <i>continuous</i> feed Changes. 
 * <h3>Usage Example:</h3>
 * <pre>
 * // feed type normal 
 * String since = dbClient.context().info().getUpdateSeq(); // latest update seq
 * ChangesResult changeResult = dbClient.changes()
 *	.since(since) 
 *	.limit(10)
 *	.filter("example/filter")
 *	.getChanges();
 *
 * for (ChangesResult.Row row : changeResult.getResults()) {
 *   String docId = row.getId()
 *   JsonObject doc = row.getDoc();
 * }
 *
 * // feed type continuous
 * Changes changes = dbClient.changes()
 *	.includeDocs(true) 
 *	.heartBeat(30000)
 *	.continuousChanges(); 
 * 
 * while (changes.hasNext()) { 
 *	ChangesResult.Row feed = changes.next();
 *  String docId = feed.getId();
 *  JsonObject doc = feed.getDoc();
 *	// changes.stop(); // stop continuous feed
 * }
 * </pre>
 * @see ChangesResult
 * @since 0.0.2
 * @author Ahmed Yehia
 */
public class Changes {
	
	private BufferedReader reader;
	private HttpGet httpGet;
	private Row nextRow;
	private boolean stop;
	
	private CouchDbClientBase dbc;
	private Gson gson;
	private URIBuilder uriBuilder;
	
	Changes(CouchDbClientBase dbc) {
		this.dbc = dbc;
		this.gson = dbc.getGson();
		this.uriBuilder = dbc.getDBURIBuilder().pathSegment("_changes");
	}

	/**
	 * Requests Change notifications of feed type continuous.
	 * <p>Feed notifications are accessed in an <i>iterator</i> style.
	 * @return {@link Changes}
	 */
	public Changes continuousChanges() {
		final URI uri = uriBuilder.query("feed", "continuous").build();
		httpGet = new HttpGet(uri);
		final InputStream in = dbc.get(httpGet);
		final InputStreamReader is = new InputStreamReader(in, Charsets.UTF_8);
		setReader(new BufferedReader(is));
		return this;
	}

	/**
	 * Checks whether a feed is available in the continuous stream, blocking 
	 * until a feed is received. 
	 * @return true If a feed is available
	 */
	public boolean hasNext() { 
		return readNextRow();
	}

	/**
	 * @return The next feed in the stream.
	 */
	public Row next() {
		return getNextRow();
	}

	/**
	 * Stops a running continuous feed.
	 */
	public void stop() {
		stop = true;
	}

	/**
	 * Requests Change notifications of feed type normal.
	 * @return {@link ChangesResult}
	 */
	public ChangesResult getChanges() {
		final URI uri = uriBuilder.query("feed", "normal").build();
		return dbc.get(uri, ChangesResult.class);
	}

	// Query Params
	
	public Changes since(String since) {
		uriBuilder.query("since", since);
		return this;
	}
	
	public Changes limit(int limit) {
		uriBuilder.query("limit", limit);
		return this;
	}
	
	public Changes heartBeat(long heartBeat) {
		uriBuilder.query("heartbeat", heartBeat);
		return this;
	}
	
	public Changes timeout(long timeout) {
		uriBuilder.query("timeout", timeout);
		return this;
	}

	public Changes filter(String filter) {
		uriBuilder.query("filter", filter);
		return this;
	}
	
	public Changes includeDocs(boolean includeDocs) {
		uriBuilder.query(CouchConstants.PARAM_INCLUDE_DOCS, includeDocs);
		return this;
	}
	
	public Changes style(String style) {
		uriBuilder.query("style", style);
		return this;
	}
	
	// Helper

	/**
	 * Reads and sets the next feed in the stream.
	 */
	private boolean readNextRow() {
		boolean hasNext = false;
		try {
			if(!stop) {
				String row = ""; 
				do {
					row = getReader().readLine(); 
				} while(row.length() == 0);
				
				if(!row.startsWith("{\"last_seq\":")) { 
					setNextRow(gson.fromJson(row, Row.class));
					hasNext = true;
				} 
			} 
		} catch (Exception e) {
			terminate();
			throw new CouchDbException("Error reading continuous stream.", e);
		} 
		if(!hasNext) 
			terminate();
		return hasNext;
	}

	private BufferedReader getReader() {
		return reader;
	}

	private void setReader(BufferedReader reader) {
		this.reader = reader;
	}

	private Row getNextRow() {
		return nextRow;
	}

	private void setNextRow(Row nextRow) {
		this.nextRow = nextRow;
	}
	
	private void terminate() {
		httpGet.abort();
		CouchDbUtil.close(getReader());
	}
}
