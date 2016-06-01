package com.temenos.interaction.sample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.odata4j.consumer.ODataConsumer;
import org.odata4j.consumer.behaviors.OClientBehavior;
import org.odata4j.consumer.behaviors.OClientBehaviors;
import org.odata4j.core.OCollection;
import org.odata4j.core.OCollections;
import org.odata4j.core.OComplexObjects;
import org.odata4j.core.OEntities;
import org.odata4j.core.OEntity;
import org.odata4j.core.OObject;
import org.odata4j.core.OProperties;
import org.odata4j.core.OProperty;
import org.odata4j.edm.EdmCollectionType;
import org.odata4j.edm.EdmComplexType;
import org.odata4j.edm.EdmDataServices;
import org.odata4j.edm.EdmProperty;
import org.odata4j.jersey.consumer.ODataJerseyConsumerExt;

import com.temenos.interaction.sample.ext.ExtendedOClientBehaviour;

/**
 * Example for consuming IRIS Services through Odata4j
 *
 * @author sjunejo
 *
 */
public class IRISClientPostOdata4jTest {
    
    // Please update URL and User Configuration for your environment
    // http://localhost:9089/Test-iris/Test.svc/GB0010001/
    private static String baseUrl = "http://localhost:9089/Test-iris/Test.svc/GB0010001/";
    private static String userName = "INPUTT", password = "123456";    
    
    protected ODataConsumer odataConsumer = null;
    
    private final static String CUSTOMER_INPUT_ENTITY_SET = "verCustomer_Inputs";
    private final static String odataServiceModel =  "Test-modelsModel";
    protected final static String MS_ODATA = "http://schemas.microsoft.com/ado/2007/08/dataservices";
    protected final static String MS_ODATA_METADATA = "http://schemas.microsoft.com/ado/2007/08/dataservices/metadata";
    protected final static String MS_ODATA_RELATED = "http://schemas.microsoft.com/ado/2007/08/dataservices/related";
    
    // Existing Customer to Update
    private String customerCode1 = "100100";
        
    @Before
    public void setup() {
        try {
            releaseODataConsumer();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @After
    public void tearDown() {
        try {
            releaseODataConsumer();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    public void testIRISUpdateServiceOdata4j() {
        
        // Below class is to support concept of ETag so that we can make sure 
        ExtendedOClientBehaviour behaviour = new ExtendedOClientBehaviour();
        ODataConsumer consumer = createODataConsumer(behaviour);
        
        //Get customer
        OEntity customer1 = consumer.getEntity(CUSTOMER_INPUT_ENTITY_SET, customerCode1).execute();
        String etagCustomer1 = behaviour.getEtag();
        
        System.out.println("Old Customer Entity ---- "+ customer1.toString());
        
        //Modify Details
        Map<String, OProperty<?>> newValues = new HashMap<String, OProperty<?>>();
        newValues.put("Gender", OProperties.simple("Gender", "FEMALE"));
        newValues.put("Title", OProperties.simple("Title", "MS"));
        
        // Update Complex Type
        
        EdmDataServices metadata = getMetadata();
        EdmComplexType shortNameType = metadata.findEdmComplexType(odataServiceModel + ".verCustomer_Input_ShortNameMvGroup");          
        OCollection.Builder<OObject> shortNameBuilder = OCollections.newBuilder( shortNameType );
        List<OProperty<?>> shortName1 = new ArrayList<OProperty<?>>();
        shortName1.add(OProperties.string("ShortName", "MyShortName28"));
        shortNameBuilder.add((OObject) OComplexObjects.create( shortNameType, shortName1 ));
        
        List<OProperty<?>> shortName2 = new ArrayList<OProperty<?>>();
        shortName2.add(OProperties.string("ShortName", "MyShortName644"));
        shortNameBuilder.add((OObject) OComplexObjects.create( shortNameType, shortName2 ));
        
        OCollection<? extends OObject> shortNameColl = shortNameBuilder.build();
        OProperty<?> shortNameOp = OProperties.collection( shortNameType.getName(), 
                                                    new EdmCollectionType( EdmProperty.CollectionKind.List, shortNameType ),
                                                    shortNameColl);
        newValues.put("verCustomer_Input_ShortNameMvGroup", shortNameOp);
        
        // Modify OEntity of Customer with the values mentioned in newValues HapMap
        OEntity entity = modifyEntity(customer1, newValues);
        
        System.out.println("New Customer Entity ---- "+ entity.toString());
        
        //Set Etag Of Customer
        behaviour.setIfMatch(etagCustomer1);
        
        //Call T24 and update Customer record
        consumer.updateEntity(entity).execute();
        
        //Check customer has been modified
        customer1 = consumer.getEntity(CUSTOMER_INPUT_ENTITY_SET, customerCode1).execute();
        assertEquals("FEMALE", customer1.getProperty("Gender").getValue());
    }
    
   
    
   //Modify an OEntity variable with the values passed through HashMap
    protected OEntity modifyEntity(OEntity entity, Map<String, OProperty<?>> updatedValues) {
        List<OProperty<?>> originalProps = entity.getProperties();
        List<OProperty<?>> props = new ArrayList<OProperty<?>>();
        for(OProperty<?> originalProp : originalProps) {
            String propName = originalProp.getName();
            if(updatedValues.containsKey(propName)) {
                props.add(updatedValues.get(propName));
            }
            else {
                props.add(originalProp);
            }
        }
        return OEntities.create(entity.getEntitySet(), entity.getEntityKey(), props, null);
    }
               
    
    //Creates a new odata consumer with the specified client behaviour 
    private ODataConsumer createODataConsumer(OClientBehavior clientBehaviour) {
        OClientBehavior basicAuthBehaviour = OClientBehaviors.basicAuth(userName,password);
        odataConsumer = new ODataJerseyConsumerExt(baseUrl, clientBehaviour, basicAuthBehaviour);
        return odataConsumer;
    }          
   
   //This function is used to get Entity Data Model from odataConsumer
    private EdmDataServices getMetadata() {
        return odataConsumer.getMetadata();
    }
    
   // Release OData Consumer 
   protected void releaseODataConsumer() {
       if(odataConsumer != null) {
           odataConsumer = null;
       }
   }
   
}
