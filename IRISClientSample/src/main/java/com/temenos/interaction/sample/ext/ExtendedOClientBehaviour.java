package com.temenos.interaction.sample.ext;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;

import org.odata4j.consumer.ODataClientRequest;
import org.odata4j.jersey.consumer.behaviors.JerseyClientBehavior;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.api.client.filter.Filterable;

/**
 * Extended OClientBehaviour to enable us to handle headers such as E-Tags.
 */
public class ExtendedOClientBehaviour implements JerseyClientBehavior {

	//Request headers
	private String ifNoneMatch;
	private String ifMatch;
	
	//Response headers
	private String etag;
	
	@Override
	public ODataClientRequest transform(ODataClientRequest request) {
		if (ifNoneMatch != null) {
			request.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
		}
		if (ifMatch != null) {
			request.header(HttpHeaders.IF_MATCH, ifMatch);
		}
		return request;
	}

	@Override
	public void modify(ClientConfig clientConfig) {
	}

	@Override
	public void modifyWebResourceFilters(Filterable filterable) {
	}

	@Override
	public void modifyClientFilters(Filterable client) {
		client.addFilter(new ClientFilter() {
			@Override
			public ClientResponse handle(ClientRequest clientRequest) throws ClientHandlerException {
				ClientResponse response = getNext().handle(clientRequest);
				MultivaluedMap<String, String> responseHeaders = response.getHeaders();
				if(!clientRequest.getURI().getPath().endsWith("/$metadata")) {
					etag = responseHeaders.getFirst(HttpHeaders.ETAG);
				}
				return response;
			}
		});
	}
	
	public void setIfNoneMatch(String ifNoneMatch) {
		this.ifNoneMatch = ifNoneMatch;
	}

	public void setIfMatch(String ifMatch) {
		this.ifMatch = ifMatch;
	}
	
	public String getEtag() {
		return etag;
	}
}
