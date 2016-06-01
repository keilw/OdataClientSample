package com.temenos.interaction.sample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.abdera.protocol.client.AbderaClient;
import org.apache.abdera.protocol.client.ClientResponse;
import org.apache.abdera.protocol.client.RequestOptions;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static org.junit.Assert.assertNotNull;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.auth.BasicScheme;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.abdera.Abdera;
import org.apache.abdera.model.Content;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Element;
import org.apache.abdera.model.Entry;
import org.apache.abdera.parser.Parser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.odata4j.consumer.ODataConsumer;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

/**
 * Example for consuming IRIS Services through Odata4j
 *
 * @author sjunejo
 *
 */
public class IRISClientPostEntyTest {
    
    // Please update URL and User Configuration for your environment
    // "http://localhost:9089/Test-iris/Test.svc/GB0010001/";
    private static String baseUrl = "http://localhost:9089/Test-iris/Test.svc/GB0010001/";
    private static String userName = "INPUTT", password = "123456";    
    
    protected ODataConsumer odataConsumer = null;
    
    //CUSTOMER INPUT Version is used here to update Customer record
    private final static String CUSTOMER_INPUT_ENTITY_SET = "verCustomer_Inputs";    
    protected final static String MS_ODATA = "http://schemas.microsoft.com/ado/2007/08/dataservices";
    protected final static String MS_ODATA_METADATA = "http://schemas.microsoft.com/ado/2007/08/dataservices/metadata";
    protected final static String MS_ODATA_RELATED = "http://schemas.microsoft.com/ado/2007/08/dataservices/related";
    
    // Existing Customer to Update
    private String customerCode1 = "100100";
    
    // HTTPClient
    private HttpClient httpClient = null;
    // AbderaClient 
    protected AbderaClient abderaClient = null;
    
    @Before
    public void setup() {
        try {            
            releaseHttpClient();
            releaseAbderaClient();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @After
    public void tearDown() {
        try {            
            releaseHttpClient();
            releaseAbderaClient();            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }        
    
    @Test
    public void testIRISUpdateServiceWithEntry() throws IOException {
                           
        //Get customer from T24 using HTTPClient calls
        GetMethod getMethod=executeGet(CUSTOMER_INPUT_ENTITY_SET + "('" + customerCode1 + "')");
        
        //Fetch ETag from Response Body
        String etagCustomer1 = getMethod.getResponseHeader("ETag").toString();   
        
        //Get Customer Entry block from getMethod
        Entry customerEntry = getEntryFromGetMethod(getMethod);
        
        System.out.println("Old Customer Entry ---- "+ customerEntry.toString());
            
        // Create a HashMap which contains Customer attributes to be updated. 
        Map<String, String> updatedValues = new HashMap<String, String>();        
        updatedValues.put("Gender", "FEMALE");       
        updatedValues.put("Title", "MS");
        updatedValues.put("verCustomer_Input_ShortNameMvGroup.element(0).ShortName", "f29o");    
        updatedValues.put("verCustomer_Input_ShortNameMvGroup.element(1).ShortName", "f220o");  
        
        //This function is used to replace existing customer attributes with the one mentioned in updatedValues
        populateEntryProperties(customerEntry, updatedValues);
                     
        System.out.println("Modified Customer Entity ---- "+ customerEntry.toString());    
        
        //POST the updated Customer Record into T24 using AbderaClient
        //Created RequestOptins parameter and give ContentType and Etag values 
        RequestOptions opts = new RequestOptions();
        opts.setContentType("application/atom+xml;type=entry");
        opts.setIfMatch(etagCustomer1);
        
        //Make a PostRequest and Fetch updated CustomerUpdate Entry  from the post response.  
        Entry customerUpdatedEntry=makePostRequestForEntry(getAbderaClient(), baseUrl +CUSTOMER_INPUT_ENTITY_SET + "('" + customerCode1 + "')", customerEntry, opts);    
      
        System.out.println("Updated Customer Entity ---- "+ customerUpdatedEntry.toString());            
        System.out.println("Updated Customer Short Name one " + getEntryElement(customerUpdatedEntry, "verCustomer_Input_ShortNameMvGroup.element(0).ShortName").getText() );
        System.out.println("Updated Customer Short Name one " + getEntryElement(customerUpdatedEntry, "verCustomer_Input_ShortNameMvGroup.element(1).ShortName").getText() );
        
        //Verify if CustomerUpdate Entry contains updated values.
        assertEquals("f29o", getEntryElement(customerUpdatedEntry, "verCustomer_Input_ShortNameMvGroup.element(0).ShortName").getText() );
        assertEquals("f220o", getEntryElement(customerUpdatedEntry, "verCustomer_Input_ShortNameMvGroup.element(1).ShortName").getText() );
    }
    
  
    // Pass GetMethod and convert the Stream into Abdera Entry using HTTPClient
    private Entry getEntryFromGetMethod(GetMethod getMethod) {
        try {           
            if (HttpStatus.SC_OK == getMethod.getStatusCode()) {
                Abdera abdera = new Abdera();
                Parser parser = abdera.getParser();
                // If any information is required from Fee get it here
                Document<Entry> doc = parser.parse(new InputStreamReader(getMethod.getResponseBodyAsStream()));
                return doc.getRoot();
            } else {
                throw new RuntimeException("Failed with status code:" + getMethod.getStatusCode());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed with IOException:" + e.getMessage());
        }
    }
    
    
    //This function is used to make a post request to T24 using AbderaClient
    protected Entry makePostRequestForEntry(AbderaClient client, String uri, Entry newEntry, RequestOptions opts) {
        Entry entry = null;
        ClientResponse res = null;
        try {
            opts.setContentType("application/atom+xml;type=entry");
            res = client.post(uri, newEntry, opts);
            assertEquals(201, res.getStatus());

            if (res.getStatus() == HttpStatus.SC_CREATED) {
                Document<Entry> document = res.getDocument();
                entry = document.getRoot();
                // read as string for debugging
                System.out.println("Response = " + entry);
            }
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            res.release();
        }
        return entry;
    }
    
    // Execute the Get method
    private GetMethod executeGet(String entityUri) throws IOException {
        GetMethod getMethod = new GetMethod(baseUrl + "/" + entityUri);
        HttpClient httpClient = getHttpClient();
        httpClient.executeMethod(getMethod);
        return getMethod;
    }        
              
    
    //This function is used to replace existing customer Entry with the one mentioned in HashMap
    protected void populateEntryProperties(Entry entry, Map<String, String> replaceValues) {
        Pattern elementPattern = Pattern.compile("element\\((\\d+?)\\)");
        String content = entry.getContent();
        try {
            //Convert to entry content to a dom object to allow us to add new element
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);
            docFactory.setIgnoringElementContentWhitespace(true);
            docFactory.setIgnoringComments(true);
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            InputSource is = new InputSource();
            is.setCharacterStream(new StringReader(content));
            org.w3c.dom.Document doc = docBuilder.parse(is);
            for(String testValue : replaceValues.keySet()) {
                String[] testValueTokens = testValue.split("\\.");
                Node element = getNode(doc, testValueTokens[0]);
                removeWhitespaceNodes(element);     //Remove whitespaces
                assertNotNull(element);
                for(int i=1; i < testValueTokens.length; i++) {
                    String token = testValueTokens[i];
                    Matcher m = elementPattern.matcher(token);
                    if(m.find()) {
                        Node parentElement = element;
                        element = getChildNode(element, "element");
                        
                        //This is an element(i) token so parse element i
                        int elementIndex = Integer.valueOf(m.group(1)).intValue();
                        while(elementIndex > 0) {
                            Node nextElement = element.getNextSibling();
                            if(nextElement == null) {
                                //Sibling does not exist so add a copy of the previous one
                                nextElement = element.cloneNode(true);
                                parentElement.appendChild(nextElement);                         
                            }
                            element = nextElement;
                            elementIndex--;
                        }
                    }
                    else {
                        element = getChildNode(element, token);
                    }
                    assertNotNull(element);
                }
                String replacementValue = replaceValues.get(testValue);
                if(replacementValue != null) {
                    element.setTextContent(replaceValues.get(testValue));
                    Node nullAttrib = element.getAttributes().getNamedItem("m:null");
                    if (nullAttrib != null) {
                        ((org.w3c.dom.Element) element).removeAttribute(nullAttrib.getNodeName());
                    }
                }
            }

            //Convert dom document to Abdera entry content
            DOMSource domSource = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.transform(domSource, result);
            entry.setContent(writer.toString(), Content.Type.XML);
        }
        catch(Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
          
    
     // Obtain the specified element from the atom entry. 
    public Element getEntryElement(Entry entry, String fqElementName) {
        Pattern elementPattern = Pattern.compile("element\\((\\d+?)\\)");
        Element content = entry.getFirstChild(new QName("http://www.w3.org/2005/Atom", "content"));
        assertNotNull(content);
        Element properties = content.getFirstChild(new QName(MS_ODATA_METADATA, "properties"));
        assertNotNull(properties);
        String[] tokens = fqElementName.split("\\.");
        Element element = properties.getFirstChild(new QName(MS_ODATA, tokens[0]));
        assertNotNull(element);
        for(int i=1; i < tokens.length; i++) {
            Matcher m = elementPattern.matcher(tokens[i]);
            if(m.find()) {
                element = element.getFirstChild(new QName(MS_ODATA, "element"));
                int elementIndex = Integer.valueOf(m.group(1)).intValue();
                while(elementIndex > 0) {       //This is an element(i) token so parse element i
                    element = element.getNextSibling(new QName(MS_ODATA, "element"));
                    elementIndex--;
                }
            }
            else {
                element = element.getFirstChild(new QName(MS_ODATA, tokens[i]));
            }
            assertNotNull(element);
        }
        return element;
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
    
    // Remove WhitSpace From Nodes    
    private void removeWhitespaceNodes(Node e) {
        NodeList children = e.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child instanceof Text && ((Text) child).getData().trim().length() == 0) {
                e.removeChild(child);
            }
            else if (child instanceof Node || child instanceof org.w3c.dom.Element) {
                removeWhitespaceNodes(child);
            }
        }
    }
    
    private Node getChildNode(Node node, String name) throws Exception {
        NodeList nodes = node.getChildNodes();
        for(int i=0; i < nodes.getLength(); i++) {
            Node childNode = nodes.item(i);
            if(childNode.getLocalName().equals(name) || name.equals("element") && childNode.getLocalName().startsWith("element(")) {
                return childNode;
            }
        }
        throw new Exception("Failed to find child node [" + name + "] on node [" + node.getNodeName() + "].");
    }
    
    //Return the specified element in the DOM document
   private Node getNode(org.w3c.dom.Document doc, String name) {
       NodeList nodes = doc.getElementsByTagNameNS(MS_ODATA, name);
       assertNotNull(nodes);
       assertTrue("Failed to find [" + name + "]", nodes.getLength() > 0);
       Node node = nodes.item(0);
       assertNotNull(node);
       return node;
   }
        
  //Returns an Abdera client with authentication support
   protected AbderaClient getAbderaClient() {
       if(abderaClient == null) {
           abderaClient = createAbderaClient(userName,password);
       }
       return abderaClient;
   }
   
   //This function is used to Create Abdera Client 
   protected AbderaClient createAbderaClient(String username, String password) {
       AbderaClient abClient = new AbderaClient(new Abdera());

       AbderaClient.registerTrustManager(); // needed for SSL
       AbderaClient.registerScheme(AuthPolicy.BASIC, BasicScheme.class);
       abClient.setAuthenticationSchemePriority(AuthPolicy.BASIC);
       abClient.usePreemptiveAuthentication(false);
       try {
           abClient.addCredentials(AuthScope.ANY_HOST, AuthScope.ANY_REALM, AuthScope.ANY_SCHEME, 
                   new UsernamePasswordCredentials(username, password));
       }
       catch(URISyntaxException ue) {
           ue.printStackTrace();
           fail("Failed to set authentication scheme: " + ue.getMessage());
       }
       return abClient;
   }
   
   
// Release HTTP Client
   protected void releaseHttpClient() {
       if(httpClient != null) {
           httpClient = null;
       }
   }
 
// Release AbderaClient
   protected void releaseAbderaClient(){
       if(abderaClient != null) {
           abderaClient = null;
       }
   }
   
}
