package edu.mit.ll.nics.processor.raws;

import edu.mit.ll.nics.processor.DataStoreManager;
import edu.mit.ll.nics.processor.factory.RAWSFeatureFactory;
import edu.mit.ll.nics.processor.raws.model.*;
import edu.mit.ll.nics.processor.raws.parser.RAWSResponseParser;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.geotools.data.DataStore;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.filter.text.cql2.CQL;
import org.mockito.Mockito;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.util.*;

import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;

public class RAWSFeedProcessorTest {

    private DataStoreManager dataStoreManager;
    private final String rawsFeatureSource = "raws";
    private final RAWSResponseParser rawsResponseParser = mock(RAWSResponseParser.class);
    private final RAWSFeatureFactory rawsFeatureFactory = mock(RAWSFeatureFactory.class);

    private final DataStore dataStore = mock(DataStore.class);
    private final SimpleFeatureSource featureSourceReadOnly = mock(SimpleFeatureSource.class);
    private SimpleFeatureStore featureStore;
    private final SimpleFeatureType rawsFeatureType = mock(SimpleFeatureType.class);
    private final SimpleFeature simpleFeature1 = mock(SimpleFeature.class);
    private final SimpleFeature simpleFeature2 = mock(SimpleFeature.class);

    private RAWSFeedProcessor rawsFeedProcessor;
    private final Exchange exchange = mock(Exchange.class);
    private final Message message = mock(Message.class);
    private final String testGeoJson = "Test Geo Json";

    private RAWSResponse response = mock(RAWSResponse.class);
    private final RAWSSummary summary = mock(RAWSSummary.class);

    @BeforeMethod
    public void setup() throws Exception {
        dataStoreManager = mock(DataStoreManager.class);
        response = mock(RAWSResponse.class);
        rawsFeedProcessor = new RAWSFeedProcessor(dataStoreManager, rawsFeatureSource, rawsResponseParser, rawsFeatureFactory);
        featureStore = mock(SimpleFeatureStore.class);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn(testGeoJson);
        when(rawsResponseParser.parse(testGeoJson)).thenReturn(response);
    }

    @Test
    public void testWhenResponseHasErrors() throws Exception {
        when(response.hasErrors()).thenReturn(true);
        when(response.getRAWSSummary()).thenReturn(summary);
        when(summary.getResponseMessage()).thenReturn("Test error");
        rawsFeedProcessor.process(exchange);
        verify(response, never()).getRAWSFeatures();
        verify(dataStoreManager, never()).getInstance();
    }

    @Test
    public void testWhenResponseHasZeroResults() throws Exception {
        when(response.hasErrors()).thenReturn(false);
        when(response.getRAWSFeatures()).thenReturn(Collections.EMPTY_LIST);
        rawsFeedProcessor.process(exchange);
        verify(dataStoreManager, never()).getInstance();
    }

    @Test
    public void testWhenFeatureSourceIsNotWritable() throws Exception {
        when(response.hasErrors()).thenReturn(false);
        when(response.getRAWSFeatures()).thenReturn(Arrays.asList(mock(RAWSFeature.class)));
        when(dataStoreManager.getInstance()).thenReturn(dataStore);
        when(dataStore.getFeatureSource(rawsFeatureSource)).thenReturn(featureSourceReadOnly);
        rawsFeedProcessor.process(exchange);
        Mockito.verifyZeroInteractions(featureSourceReadOnly);
    }

    @Test
    public void testWhenResponseHasValidFeaturesAndFeatureSourceIsWritable() throws Exception {
        RAWSFeatureGeometry rawsFeatureGeometry1 = new RAWSFeatureGeometry("Point", Arrays.asList(-121.0, 36.0));
        RAWSObservations rawsObservations1 = new RAWSObservations("ACTIVE", "POSITIVE", "POSITIVE VIBES", "CA",
                62.0,10.0,2.0,4.0,238.0,10.0,new Timestamp(new Date().getTime()),"http://test-station.com/more-observations/POSITIVE",false);
        RAWSFeature rawsFeature1 = new RAWSFeature(rawsFeatureGeometry1, "Feature", rawsObservations1);
        List<RAWSFeature> rawsFeatures = Arrays.asList(rawsFeature1);//, rawsFeature2);
        Filter filter = CQL.toFilter("station_id = '" + rawsFeature1.getRawsObservations().getStationId() + "'");

        when(response.hasErrors()).thenReturn(false);
        when(response.getRAWSFeatures()).thenReturn(rawsFeatures);
        when(dataStoreManager.getInstance()).thenReturn(dataStore);
        when(dataStore.getFeatureSource(rawsFeatureSource)).thenReturn(featureStore);
        when(featureStore.getSchema()).thenReturn(rawsFeatureType);
        when(rawsFeatureFactory.buildFeature(eq(rawsFeature1), any(SimpleFeatureBuilder.class),(RAWSQCSummary)isNull())).thenReturn(simpleFeature1);
        rawsFeedProcessor.process(exchange);
        verify(featureStore).setTransaction(any(DefaultTransaction.class));
        verify(featureStore).removeFeatures(eq(filter));
        verify(featureStore).addFeatures(any(ListFeatureCollection.class));
    }

    @Test
    public void testWhenFailsToBuildSimpleFeatureContinuesToProcessRemainingFeatures()  throws Exception {
        RAWSFeatureGeometry rawsFeatureGeometry1 = new RAWSFeatureGeometry("Point", Arrays.asList(-121.0, 36.0));
        RAWSObservations rawsObservations1 = new RAWSObservations("ACTIVE", "NOT INTERESTING", "NOT INTERESTING VIBES", "CA",
                62.0,10.0,2.0,4.0,238.0,10.0,new Timestamp(new Date().getTime()),"http://test-station.com/more-observations/NOTINTERESTING",false);
        RAWSFeature rawsFeature1 = new RAWSFeature(rawsFeatureGeometry1, "Feature", rawsObservations1);
        Filter filter1 = CQL.toFilter("station_id = '" + rawsFeature1.getRawsObservations().getStationId() + "'");

        RAWSFeatureGeometry rawsFeatureGeometry2 = new RAWSFeatureGeometry("Point", Arrays.asList(-121.0, 36.0));
        RAWSObservations rawsObservations2 = new RAWSObservations("ACTIVE", "POSITIVE", "POSITIVE VIBES", "CA",
                62.0,10.0,2.0,4.0,238.0,10.0,new Timestamp(new Date().getTime()),"http://test-station.com/more-observations/POSITIVE",false);
        RAWSFeature rawsFeature2 = new RAWSFeature(rawsFeatureGeometry2, "Feature", rawsObservations2);
        Filter filter2 = CQL.toFilter("station_id = '" + rawsFeature2.getRawsObservations().getStationId() + "'");

        List<RAWSFeature> rawsFeatures = Arrays.asList(rawsFeature1, rawsFeature2);

        when(response.hasErrors()).thenReturn(false);
        when(response.getRAWSFeatures()).thenReturn(rawsFeatures);
        when(dataStoreManager.getInstance()).thenReturn(dataStore);
        when(dataStore.getFeatureSource(rawsFeatureSource)).thenReturn(featureStore);
        when(featureStore.getSchema()).thenReturn(rawsFeatureType);
        when(rawsFeatureFactory.buildFeature(eq(rawsFeature1), any(SimpleFeatureBuilder.class),(RAWSQCSummary)isNull())).thenThrow(new Exception("Test error"));
        when(rawsFeatureFactory.buildFeature(eq(rawsFeature2), any(SimpleFeatureBuilder.class),(RAWSQCSummary)isNull())).thenReturn(simpleFeature2);
        rawsFeedProcessor.process(exchange);
        verify(featureStore).setTransaction(any(DefaultTransaction.class));
        verify(featureStore).removeFeatures(eq(filter1));
        verify(featureStore).removeFeatures(eq(filter2));
        verify(featureStore, times(1)).addFeatures(any(ListFeatureCollection.class));
    }

    @Test
    public void testWhenFeaturePersistOperationThrowsRuntimeExceptionFailsProcessRemainingFeatures() throws Exception {
        RAWSFeatureGeometry rawsFeatureGeometry1 = new RAWSFeatureGeometry("Point", Arrays.asList(-121.0, 36.0));
        RAWSObservations rawsObservations1 = new RAWSObservations("ACTIVE", "NOT INTERESTING", "NOT INTERESTING VIBES", "CA",
                62.0,10.0,2.0,4.0,238.0,10.0,new Timestamp(new Date().getTime()),"http://test-station.com/more-observations/NOTINTERESTING",false);
        RAWSFeature rawsFeature1 = new RAWSFeature(rawsFeatureGeometry1, "Feature", rawsObservations1);
        Filter filter = CQL.toFilter("station_id = '" + rawsFeature1.getRawsObservations().getStationId() + "'");

        RAWSFeatureGeometry rawsFeatureGeometry2 = new RAWSFeatureGeometry("Point", Arrays.asList(-121.0, 36.0));
        RAWSObservations rawsObservations2 = new RAWSObservations("ACTIVE", "POSITIVE", "POSITIVE VIBES", "CA",
                62.0,10.0,2.0,4.0,238.0,10.0,new Timestamp(new Date().getTime()),"http://test-station.com/more-observations/POSITIVE",false);
        RAWSFeature rawsFeature2 = new RAWSFeature(rawsFeatureGeometry2, "Feature", rawsObservations2);
        Filter filter2 = CQL.toFilter("station_id = '" + rawsFeature2.getRawsObservations().getStationId() + "'");

        List<RAWSFeature> rawsFeatures = Arrays.asList(rawsFeature1, rawsFeature2);

        when(response.hasErrors()).thenReturn(false);
        when(response.getRAWSFeatures()).thenReturn(rawsFeatures);
        when(dataStoreManager.getInstance()).thenReturn(dataStore);
        when(dataStore.getFeatureSource(rawsFeatureSource)).thenReturn(featureStore);
        when(featureStore.getSchema()).thenReturn(rawsFeatureType);
        when(rawsFeatureFactory.buildFeature(eq(rawsFeature1), any(SimpleFeatureBuilder.class),(RAWSQCSummary)isNull())).thenReturn(simpleFeature1);
        when(featureStore.addFeatures(any(ListFeatureCollection.class))).thenThrow(new RuntimeException("Test exception"));
        rawsFeedProcessor.process(exchange);
        verify(featureStore).setTransaction(any(DefaultTransaction.class));
        verify(featureStore).removeFeatures(eq(filter));
        verify(featureStore, never()).removeFeatures(eq(filter2));
        verify(rawsFeatureFactory, never()).buildFeature(eq(rawsFeature2), any(SimpleFeatureBuilder.class),(RAWSQCSummary)isNull());
    }

    @Test
    public void testFeatureIsRemovedWhenStationStatusIsInactive () throws Exception {
        RAWSFeatureGeometry rawsFeatureGeometry1 = new RAWSFeatureGeometry("Point", Arrays.asList(-121.0, 36.0));
        RAWSObservations rawsObservations1 = new RAWSObservations("INACTIVE", "INTERESTING BUT INACTIVE", "NOT INTERESTING VIBES", "CA",
                62.0,10.0,2.0,4.0,238.0,10.0,new Timestamp(new Date().getTime()),"http://test-station.com/more-observations/NOTINTERESTING",false);
        RAWSFeature rawsFeature1 = new RAWSFeature(rawsFeatureGeometry1, "Feature", rawsObservations1);
        Filter filter = CQL.toFilter("station_id = '" + rawsFeature1.getRawsObservations().getStationId() + "'");
        List<RAWSFeature> rawsFeatures = Arrays.asList(rawsFeature1);

        when(response.hasErrors()).thenReturn(false);
        when(response.getRAWSFeatures()).thenReturn(rawsFeatures);
        when(dataStoreManager.getInstance()).thenReturn(dataStore);
        when(dataStore.getFeatureSource(rawsFeatureSource)).thenReturn(featureStore);
        when(featureStore.getSchema()).thenReturn(rawsFeatureType);
        when(rawsFeatureFactory.buildFeature(eq(rawsFeature1), any(SimpleFeatureBuilder.class), (RAWSQCSummary)isNull())).thenReturn(simpleFeature1);
        rawsFeedProcessor.process(exchange);
        verify(featureStore).setTransaction(any(DefaultTransaction.class));
        verify(featureStore).removeFeatures(eq(filter));
        verify(featureStore, never()).addFeatures(any(ListFeatureCollection.class));
    }
}
