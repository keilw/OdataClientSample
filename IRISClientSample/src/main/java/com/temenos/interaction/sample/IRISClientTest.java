package com.temenos.interaction.sample;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.abdera.Abdera;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.odata4j.consumer.ODataConsumer;
import org.odata4j.consumer.behaviors.OClientBehaviors;
import org.odata4j.core.OEntity;
import org.odata4j.edm.EdmDataServices;
import org.odata4j.format.FormatParser;
import org.odata4j.format.xml.AtomEntryFormatParserExt;
import org.odata4j.jersey.consumer.ODataJerseyConsumer;

/**
 * Example for consuming IRIS Services through Odata4j
 *
 * @author sjunejo
 *
 */
public class IRISClientTest {
    
    // Please update URL and User Configuration for your environment
    private static String baseUrl = "http://localhost:8080/Test-iris/Test.svc";
    private static String userName = "INPUTT", password = "123456";
    
    private HttpClient httpClient = null;
    protected ODataConsumer odataConsumer = null;

    public void testIRISService() {

        // Make a call to T24 and get Abdera List<Entry>
        String entityName = "enqCustomerInfos()", queryOption= "?$top=10"; 
        List<Entry> entries = getEntries("/" + entityName + queryOption);
        
        // Make sure you get the Links from Abdera Feed here if required
        // TODO 
        
        // Convert List<Entry> to Odata4J List<OEntity>
        List<OEntity> oEntityList = new ArrayList<OEntity>();
        // Following is now making a use of IRIS Extension of Odata4j to handle Bag/Collection Types
        FormatParser<org.odata4j.format.Entry> parser = new AtomEntryFormatParserExt(getMetadata(), entityName,
                null, null);
        for (Entry entry : entries) {
            org.odata4j.format.Entry odataEntry = parser.parse(new StringReader(entry.toString()));
            oEntityList.add(odataEntry.getEntity());
        }
        
        // Work with List<OEntity> Here
    }

    // Make a call to T24 using HttpClient and convert the Stream into Abdera Entry
    private List<Entry> getEntries(String entityUri) {
        try {
            GetMethod getMethod = executeGet(entityUri);

            if (HttpStatus.SC_OK == getMethod.getStatusCode()) {
                Abdera abdera = new Abdera();
                Parser parser = abdera.getParser();
                // If any information is required from Fee get it here
                Document<Feed> doc = parser.parse(new InputStreamReader(getMethod.getResponseBodyAsStream()));
                return doc.getRoot().getEntries();
            } else {
                throw new RuntimeException("Failed with status code:" + getMethod.getStatusCode());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed with IOException:" + e.getMessage());
        }
    }

    // Execute the Get method
    private GetMethod executeGet(String entityUri) throws IOException {
        GetMethod getMethod = new GetMethod(baseUrl + "/" + entityUri);
        HttpClient httpClient = getHttpClient();
        httpClient.executeMethod(getMethod);
        return getMethod;
    }

    // Initializing the HttpClient with Basic Auth
    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new HttpClient();
            httpClient.getState().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                    new UsernamePasswordCredentials(userName, password));
        }
        return httpClient;
    }

    // For $metadata only so that we can refer for converting Abdera Entry --> Odata4J OEntity
    private EdmDataServices getMetadata() {
        if(odataConsumer == null) {
            odataConsumer = ODataJerseyConsumer.newBuilder(baseUrl)
                            .setClientBehaviors(OClientBehaviors.basicAuth(userName,password)).build();
        }
        return odataConsumer.getMetadata();
    }
}
