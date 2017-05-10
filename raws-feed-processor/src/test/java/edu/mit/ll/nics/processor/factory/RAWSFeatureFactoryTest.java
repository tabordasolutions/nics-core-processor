package edu.mit.ll.nics.processor.factory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import edu.mit.ll.nics.processor.raws.model.RAWSFeature;
import edu.mit.ll.nics.processor.raws.model.RAWSFeatureGeometry;
import edu.mit.ll.nics.processor.raws.model.RAWSObservations;
import edu.mit.ll.nics.processor.util.Clock;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;

import static org.mockito.Mockito.*;

public class RAWSFeatureFactoryTest {
    private CRSFactory crsFactory = new CRSFactory();
    private GeometryFactory geometryFactory = mock(GeometryFactory.class);
    private GeometryFactory jtsGeometryFactory = JTSFactoryFinder.getGeometryFactory();
    private String rawsSourceCRS = "WGS84";
    private String rawsTargetCRS = "EPSG:3857";
    private SimpleFeatureBuilder featureBuilder = mock(SimpleFeatureBuilder.class);
    private SimpleFeature simpleFeature = mock(SimpleFeature.class);
    private Clock clock = mock(Clock.class);
    private Date currentDate = new Date();
    private Timestamp currentTimestamp = new Timestamp(currentDate.getTime());

    RAWSFeatureFactory rawsFeatureFactory;

    @BeforeTest
    public void setup() throws Exception {
        this.rawsFeatureFactory = new RAWSFeatureFactory(geometryFactory, clock, crsFactory, rawsSourceCRS, rawsTargetCRS);
        when(clock.getCurrentDate()).thenReturn(currentDate);
    }

    @Test
    public void testBuildFeature() throws Exception {
        RAWSFeatureGeometry RAWSFeatureGeometry = new RAWSFeatureGeometry("Point", Arrays.asList(-121.0, 36.0));
        RAWSObservations RAWSObservations = new RAWSObservations("ACTIVE", "POSITIVE", "POSITIVE VIBES", "CA",
                62,10,2,4,238,10,new Timestamp(new Date().getTime()),"http://test-station.com/more-observations/POSITIVE");
        RAWSFeature sourceRAWSFeature = new RAWSFeature(RAWSFeatureGeometry, "Feature", RAWSObservations);
        Coordinate pointCoordinates = new Coordinate(RAWSFeatureGeometry.getCoordinates().get(0), RAWSFeatureGeometry.getCoordinates().get(1));
        Point point1 = jtsGeometryFactory.createPoint(pointCoordinates);
        when(geometryFactory.createPoint(any(Coordinate.class))).thenReturn(point1);

        when(featureBuilder.buildFeature(eq("1"), any(Object[].class))).thenReturn(simpleFeature);
        SimpleFeature simpleFeatureReturned = rawsFeatureFactory.buildFeature(sourceRAWSFeature, featureBuilder);
        Assert.assertEquals(simpleFeatureReturned, simpleFeature);
    }
}
