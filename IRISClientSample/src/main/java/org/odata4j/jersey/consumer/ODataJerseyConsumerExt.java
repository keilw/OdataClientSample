package org.odata4j.jersey.consumer;

/*
 * #%L
 * interaction-example-odata-airline
 * %%
 * Copyright (C) 2012 - 2013 Temenos Holdings N.V.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;

import javax.ws.rs.ext.RuntimeDelegate;

import org.core4j.Enumerable;
import org.core4j.Func;
import org.core4j.Func1;
import org.core4j.ReadOnlyIterator;
import org.odata4j.consumer.AbstractODataConsumer;
import org.odata4j.consumer.ConsumerGetEntityRequest;
import org.odata4j.consumer.ConsumerQueryEntitiesRequest;
import org.odata4j.consumer.ODataClient;
import org.odata4j.consumer.ODataClientRequest;
import org.odata4j.consumer.ODataClientResponse;
import org.odata4j.consumer.behaviors.OClientBehavior;
import org.odata4j.core.ODataConstants.Charsets;
import org.odata4j.core.OEntity;
import org.odata4j.core.OEntityGetRequest;
import org.odata4j.core.OEntityKey;
import org.odata4j.core.OQueryRequest;
import org.odata4j.edm.EdmEntitySet;
import org.odata4j.edm.EdmNavigationProperty;
import org.odata4j.exceptions.ODataProducerException;
import org.odata4j.format.Entry;
import org.odata4j.format.Feed;
import org.odata4j.format.FormatType;
import org.odata4j.format.xml.AtomEntryFormatParserExt;
import org.odata4j.format.xml.AtomFeedFormatParserExt;
import org.odata4j.internal.EntitySegment;
import org.odata4j.internal.InternalUtil;
import org.odata4j.jersey.consumer.DefaultJerseyClientFactory;
import org.odata4j.jersey.consumer.JerseyClientFactory;
import org.odata4j.jersey.consumer.ODataJerseyClient;

import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.Action;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.odataext.entity.MetadataOData4j;

/**
 * Extended OData Jersey consumer with additional support for e.g. Bag types.
 */
public class ODataJerseyConsumerExt extends AbstractODataConsumer {
	private ODataClient client;

	public ODataJerseyConsumerExt(String serviceRootUri, OClientBehavior... behaviors) {
		this(serviceRootUri, FormatType.ATOM, DefaultJerseyClientFactory.INSTANCE, behaviors);
	}

	public ODataJerseyConsumerExt(String serviceRootUri, FormatType formatType, JerseyClientFactory clientFactory,
			OClientBehavior... behaviors) {
		super(serviceRootUri);

		// ensure that a correct JAX-RS implementation (Jersey, server or
		// default) is loaded
		if (!(RuntimeDelegate.getInstance() instanceof com.sun.jersey.core.spi.factory.AbstractRuntimeDelegate)) {
			RuntimeDelegate.setInstance(new com.sun.ws.rs.ext.RuntimeDelegateImpl());
		}
		this.client = new ODataJerseyClient(formatType, clientFactory, behaviors);
	}

	@Override
	protected ODataClient getClient() {
		return client;
	}

	@Override
	public <T> OEntityGetRequest<T> getEntity(Class<T> entityType, final String entitySetName, OEntityKey key) {
		return new ConsumerGetEntityRequest<T>(getClient(), null, getServiceRootUri(), getMetadata(), entitySetName,
				OEntityKey.create(key), null) {
			@SuppressWarnings("unchecked")
			@Override
			public T execute() throws ODataProducerException {
				String path = Enumerable.create(getSegments()).join("/");
				ODataClientRequest request = ODataClientRequest.get(getServiceRootUri() + path);
				ODataClientResponse response = getClient().getEntity(request);
				if (response == null)
					return null;

				// the first segment contains the entitySetName we start from

				EdmEntitySet entitySet = null;

				try {
					entitySet = getMetadata().getEdmEntitySet(getSegments().get(0).segment);

				} catch (Exception e) {
				}

				Entry entry = null;

				if (entitySet == null) {
					MetadataOData4j metadataOData4j = getMetadataOData4j();
					entitySet = metadataOData4j.getEdmEntitySetByEntitySetName(entitySetName);

					for (EntitySegment segment : getSegments().subList(1, getSegments().size())) {
						EdmNavigationProperty navProperty = entitySet.getType().findNavigationProperty(segment.segment);
						entitySet = getMetadata().getEdmEntitySet(navProperty.getToRole().getType());
					}

					OEntityKey key = Enumerable.create(getSegments()).last().key;

					// Use the extended atom entry parser
					entry = new AtomEntryFormatParserExt(metadataOData4j, entitySet.getName(), key, null)
							.parse(getClient().getFeedReader(response));
				} else {
					for (EntitySegment segment : getSegments().subList(1, getSegments().size())) {
						EdmNavigationProperty navProperty = entitySet.getType().findNavigationProperty(segment.segment);
						entitySet = getMetadata().getEdmEntitySet(navProperty.getToRole().getType());
					}

					OEntityKey key = Enumerable.create(getSegments()).last().key;

					// Use the extended atom entry parser
					entry = new AtomEntryFormatParserExt(getMetadata(), entitySet.getName(), key, null)
							.parse(getClient().getFeedReader(response));
				}

				response.close();
				return (T) InternalUtil.toEntity(OEntity.class, entry.getEntity());
			}
		};
	}

	@Override
	public <T> OQueryRequest<T> getEntities(final Class<T> entityType, String entitySetName) {
		return new ConsumerQueryEntitiesRequest<T>(getClient(), null, getServiceRootUri(), getMetadata(),
				entitySetName, null) {
			@Override
			public Enumerable<T> execute() throws ODataProducerException {
				final ODataClientRequest request = buildRequest(null);
				ODataClientResponse response = getClient().getEntities(request);
				if (response == null)
					return null;

				// the first segment contains the entitySetName we start from
				EdmEntitySet entitySet = getEntitySet();
				OEntityKey key = null;

				// Use the extended atom entry parser
				final Feed feed = new AtomFeedFormatParserExt(getMetadata(), entitySet.getName(), key, null)
						.parse(getClient().getFeedReader(response));

				response.close();

				Enumerable<Entry> entries = Enumerable.createFromIterator(new Func<Iterator<Entry>>() {
					public Iterator<Entry> apply() {
						return new EntryIterator(request, feed);
					}
				});

				return entries.select(new Func1<Entry, T>() {
					public T apply(Entry input) {
						return InternalUtil.toEntity(entityType, input.getEntity());
					}
				}).cast(entityType);
			}
		};

	}

	private class EntryIterator extends ReadOnlyIterator<Entry> {

		private ODataClientRequest request;
		private Feed feed;
		private Iterator<Entry> feedEntries;
		private int feedEntryCount;

		public EntryIterator(ODataClientRequest request, Feed feed) {
			this.request = request;
			this.feed = feed;
			feedEntries = feed.getEntries().iterator();
			feedEntryCount = 0;
		}

		@Override
		protected IterationResult<Entry> advance() throws Exception {

			// if (feed == null) {
			// feed = doRequest(request);
			// feedEntries = feed.getEntries().iterator();
			// feedEntryCount = 0;
			// }

			if (feedEntries.hasNext()) {
				feedEntryCount++;
				return IterationResult.next(feedEntries.next());
			}

			// old-style paging: $page and $itemsPerPage
			if (request.getQueryParams().containsKey("$page") && request.getQueryParams().containsKey("$itemsPerPage")) {
				if (feedEntryCount == 0)
					return IterationResult.done();

				int page = Integer.parseInt(request.getQueryParams().get("$page"));
				// int itemsPerPage =
				// Integer.parseInt(request.getQueryParams().get("$itemsPerPage"));

				request = request.queryParam("$page", Integer.toString(page + 1));
			}

			// new-style paging: $skiptoken
			else {
				if (feed.getNext() == null)
					return IterationResult.done();

				int skipTokenIndex = feed.getNext().indexOf("$skiptoken=");
				if (skipTokenIndex > -1) {
					String skiptoken = feed.getNext().substring(skipTokenIndex + "$skiptoken=".length());
					// decode the skiptoken first since it gets encoded as a
					// query param
					skiptoken = URLDecoder.decode(skiptoken, Charsets.Upper.UTF_8);
					request = request.queryParam("$skiptoken", skiptoken);
				} else if (feed.getNext().toLowerCase().startsWith("http")) {
					request = ODataClientRequest.get(feed.getNext());
				} else {
					throw new UnsupportedOperationException();
				}

			}

			feed = null;

			return advance(); // TODO stackoverflow possible here
		}

	}

	// @Override
	// public OCreateRequest<OEntity> createEntity(String entitySetName) {
	// EdmDataServices edmDataServices = new
	// EdmDataServicesAdapter(getMetadataOData4j());
	//
	// return new ConsumerCreateEntityRequest<OEntity>(getClient(),
	// getServiceRootUri(), edmDataServices, entitySetName, null);
	// }
	//
	// @Override
	// public OModifyRequest<OEntity> updateEntity(OEntity entity) {
	// EdmDataServices edmDataServices = new
	// EdmDataServicesAdapter(getMetadataOData4j());
	//
	// return new ConsumerEntityModificationRequest<OEntity>(entity,
	// getClient(), getServiceRootUri(), edmDataServices,
	// entity.getEntitySet().getName(), entity.getEntityKey(),
	// entity.getEntityTag());
	// }

	private MetadataOData4j getMetadataOData4j() {
		String model = getMetadata().getSchemas().get(0).getEntityContainers().get(0).getName();
		Metadata tmpMetadata = new Metadata(model);

		ResourceState serviceRoot = new ResourceState("SD", "ServiceDocument", new ArrayList<Action>(), "/");
		ResourceStateMachine hypermediaEngine = new ResourceStateMachine(serviceRoot);

		return new MetadataOData4j(tmpMetadata, hypermediaEngine);
	}
}
