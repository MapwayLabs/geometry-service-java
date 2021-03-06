/*
Copyright 2017 Echo Park Labs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

For additional information, contact:

email: info@echoparklabs.io
*/

package com.epl.service.geometry;

import com.esri.core.geometry.*;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import junit.framework.TestCase;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Unit tests for {@link GeometryOperatorsServer}.
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 */
@RunWith(JUnit4.class)
public class GeometryOperatorsServerTest {
    private GeometryOperatorsServer server;
    private ManagedChannel inProcessChannel;

    @Before
    public void setUp() throws Exception {
        String uniqueServerName = "in-process server for " + getClass();
        // use directExecutor for both InProcessServerBuilder and InProcessChannelBuilder can reduce the
        // usage timeouts and latches in test. But we still add timeout and latches where they would be
        // needed if no directExecutor were used, just for demo purpose.
        server = new GeometryOperatorsServer(InProcessServerBuilder.forName(uniqueServerName).directExecutor(), 0);
        server.start();
        inProcessChannel = InProcessChannelBuilder.forName(uniqueServerName).directExecutor().build();
    }

    @After
    public void tearDown() throws Exception {
        inProcessChannel.shutdownNow();
        server.stop();
    }


    @Test
    public void getWKTGeometry() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        OperatorExportToWkt op = OperatorExportToWkt.local();
        String geom = op.execute(0, polyline, null);

        GeometryBagData geometryBag = GeometryBagData.newBuilder().addWkt(geom).build();

        OperatorRequest requestOp = OperatorRequest.newBuilder()
                .setLeftGeometryBag(geometryBag)
                .setOperatorType(ServiceOperatorType.ExportToWkt)
                .build();

        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
        OperatorResult operatorResult = stub.executeOperation(requestOp);

        assertEquals(operatorResult.getGeometryBag().getWkt(0), geometryBag.getWkt(0));
    }

    @Test
    public void getWKTGeometryFromWKB() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        OperatorExportToWkb op = OperatorExportToWkb.local();


        GeometryBagData geometryBag = GeometryBagData.newBuilder()
                .addWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        OperatorRequest requestOp = OperatorRequest.newBuilder()
                .setLeftGeometryBag(geometryBag)
                .setOperatorType(ServiceOperatorType.ExportToWkt)
                .build();

        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
        OperatorResult operatorResult = stub.executeOperation(requestOp);

        OperatorExportToWkt op2 = OperatorExportToWkt.local();
        String geom = op2.execute(0, polyline, null);
        assertEquals(operatorResult.getGeometryBag().getWkt(0), geom);
    }

    @Test
    public void getConvexHullGeometryFromWKB() {
        Polyline polyline = new Polyline();
        polyline.startPath(-200, -90);
        polyline.lineTo(-180, -85);
        polyline.lineTo(-90, -70);
        polyline.lineTo(0, 0);
        polyline.lineTo(100, 25);
        polyline.lineTo(170, 45);
        polyline.lineTo(225, 65);
        OperatorExportToESRIShape op = OperatorExportToESRIShape.local();

        GeometryBagData geometryBag = GeometryBagData.newBuilder()
                .addEsriShape(ByteString.copyFrom(op.execute(0, polyline)))
                .build();

        OperatorRequest serviceOp = OperatorRequest
                .newBuilder()
                .setLeftGeometryBag(geometryBag)
                .setOperatorType(ServiceOperatorType.ConvexHull)
                .build();

        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
        OperatorResult operatorResult = stub.executeOperation(serviceOp);

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
        Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometryBag().getWkb(0).asReadOnlyByteBuffer(), null);

        boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

        assertTrue(bContains);
    }

    @Test
    public void testProjection() {
        Polyline polyline = new Polyline();
        polyline.startPath(500000, 0);
        polyline.lineTo(400000, 100000);
        polyline.lineTo(600000, -100000);
        OperatorExportToWkb op = OperatorExportToWkb.local();

        SpatialReferenceData inputSpatialReference = SpatialReferenceData.newBuilder()
                .setWkid(32632)
                .build();

        GeometryBagData geometryBag = GeometryBagData.newBuilder()
                .addWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSpatialReference(inputSpatialReference)
                .build();

        SpatialReferenceData outputSpatialReference = SpatialReferenceData.newBuilder()
                .setWkid(4326)
                .build();


        OperatorRequest serviceProjectOp = OperatorRequest
                .newBuilder()
                .setLeftGeometryBag(geometryBag)
                .setOperatorType(ServiceOperatorType.Project)
                .setOperationSpatialReference(outputSpatialReference)
                .build();

        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
        OperatorResult operatorResult = stub.executeOperation(serviceProjectOp);

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
        Polyline result = (Polyline) op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometryBag().getWkb(0).asReadOnlyByteBuffer(), null);
        TestCase.assertNotNull(result);

        TestCase.assertFalse(polyline.equals(result));
        assertEquals(polyline.getPointCount(), result.getPointCount());
//    projectionTransformation = new ProjectionTransformation(SpatialReference.create(4326), SpatialReference.create(32632));
//    Polyline originalPolyline = (Polyline)OperatorProject.local().execute(polylineOut, projectionTransformation, null);
//
//    for (int i = 0; i < polyline.getPointCount(); i++) {
//      assertEquals(polyline.getPoint(i).getX(), originalPolyline.getPoint(i).getX(), 1e-10);
//      assertEquals(polyline.getPoint(i).getY(), originalPolyline.getPoint(i).getY(), 1e-10);
//    }
    }

    @Test
    public void testChainingBufferConvexHull() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        // TODO inspect bug where it crosses dateline
//        polyline.startPath(-200, -90);
//        polyline.lineTo(-180, -85);
//        polyline.lineTo(-90, -70);
//        polyline.lineTo(0, 0);
//        polyline.lineTo(100, 25);
//        polyline.lineTo(170, 45);
//        polyline.lineTo(225, 64);

        OperatorExportToWkb op = OperatorExportToWkb.local();
        GeometryBagData geometryBag = GeometryBagData
                .newBuilder()
                .addWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        OperatorRequest serviceConvexOp = OperatorRequest
                .newBuilder()
                .setLeftGeometryBag(geometryBag)
                .setOperatorType(ServiceOperatorType.ConvexHull)
                .build();


        OperatorRequest serviceOp = OperatorRequest.newBuilder()
                .setLeftGeometryRequest(serviceConvexOp)
                .setBufferParams(BufferParams.newBuilder().addDistances(1).build())
                .setOperatorType(ServiceOperatorType.Buffer)
                .build();


        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
        OperatorResult operatorResult = stub.executeOperation(serviceOp);

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
        Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometryBag().getWkb(0).asReadOnlyByteBuffer(), null);

        boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

        assertTrue(bContains);
    }

    static double randomWithRange(double min, double max) {
        double range = Math.abs(max - min);
        return (Math.random() * range) + (min <= max ? min : max);
    }

    @Test
    public void testUnion() {
        int size = 1000;
        List<String> points = new ArrayList<>(size);
        List<Point> pointList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double x = randomWithRange(-20, 20);
            double y = randomWithRange(-20, 20);
            points.add(String.format("Point(%f %f)", x, y));
            pointList.add(new Point(x, y));
        }
        GeometryBagData geometryBag = GeometryBagData.newBuilder()
                .addAllWkt(points)
                .build();

        BufferParams bufferParams = BufferParams.newBuilder().addDistances(2.5).setUnionResult(true).build();

        OperatorRequest serviceBufferOp = OperatorRequest.newBuilder()
                .setLeftGeometryBag(geometryBag)
                .setOperatorType(ServiceOperatorType.Buffer)
                .setBufferParams(bufferParams)
                .build();

        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
        OperatorResult operatorResult = stub.executeOperation(serviceBufferOp);

        List<ByteBuffer> byteBufferList = operatorResult.getGeometryBag().getWkbList().stream().map(com.google.protobuf.ByteString::asReadOnlyByteBuffer).collect(Collectors.toList());
        SimpleByteBufferCursor simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferList);
        OperatorImportFromWkbCursor operatorImportFromWkbCursor = new OperatorImportFromWkbCursor(0, simpleByteBufferCursor);
        Geometry result = OperatorImportFromWkb.local().execute(0, Geometry.Type.Unknown, operatorResult.getGeometryBag().getWkb(0).asReadOnlyByteBuffer(), null);
        assertTrue(result.calculateArea2D() > (Math.PI * 2.5 * 2.5 * 2));

//    assertEquals(resultSR.calculateArea2D(), Math.PI * 2.5 * 2.5, 0.1);
//    shape_start = datetime.datetime.now()
//    spots = [p.buffer(2.5) for p in points]
//    patches = cascaded_union(spots)
//    shape_end = datetime.datetime.now()
//    shape_delta = shape_end - shape_start
//    shape_microseconds = int(shape_delta.total_seconds() * 1000)
//
//    stub = geometry_grpc.GeometryOperatorsStub(self.channel)
//    geometryBag = GeometryBagData()
//
//    epl_start = datetime.datetime.now()
//    geometryBag.geometry_binary.extend([s.wkb for s in spots])
//    geometryBag.geometry_encoding_type = GeometryEncodingType.Value('wkb')
//
//        # opRequestBuffer = OperatorRequest(left_geometry=geometryBag,
//            #                                   operator_type=ServiceOperatorType.Value('Buffer'),
//            #                                   buffer_distances=[2.5])
//
//    opRequestUnion = OperatorRequest(left_geometry=geometryBag,
//            operator_type=ServiceOperatorType.Value('Union'))
//
//    response = stub.ExecuteOperation(opRequestUnion)
//    unioned_result = wkbloads(response.geometry.geometry_binary[0])
//    epl_end = datetime.datetime.now()
//    epl_delta = epl_end - epl_start
//    epl_microseconds = int(epl_delta.total_seconds() * 1000)
//    self.assertGreater(shape_microseconds, epl_microseconds)
//    self.assertGreater(shape_microseconds / 8, epl_microseconds)
//
//    self.assertAlmostEqual(patches.area, unioned_result.area, 4)
    }

    @Test
    public void testCrazyNesting() {
        Polyline polyline = new Polyline();
        polyline.startPath(-120, -45);
        polyline.lineTo(-100, -55);
        polyline.lineTo(-90, -63);
        polyline.lineTo(0, 0);
        polyline.lineTo(1, 1);
        polyline.lineTo(100, 25);
        polyline.lineTo(170, 45);
        polyline.lineTo(175, 65);
        OperatorExportToWkb op = OperatorExportToWkb.local();

        SpatialReferenceData spatialReferenceNAD = SpatialReferenceData.newBuilder().setWkid(4269).build();
        SpatialReferenceData spatialReferenceMerc = SpatialReferenceData.newBuilder().setWkid(3857).build();
        SpatialReferenceData spatialReferenceWGS = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData spatialReferenceGall = SpatialReferenceData.newBuilder().setWkid(54016).build();
        //TODO why does esri shape fail


        GeometryBagData geometryBagLeft = GeometryBagData.newBuilder()
                .addWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSpatialReference(spatialReferenceNAD)
                .build();

        BufferParams bufferParams = BufferParams.newBuilder().addDistances(.5).build();

        OperatorRequest serviceOpLeft = OperatorRequest
                .newBuilder()
                .setLeftGeometryBag(geometryBagLeft)
                .setOperatorType(ServiceOperatorType.Buffer)
                .setBufferParams(bufferParams)

                .setResultSpatialReference(spatialReferenceWGS)
                .build();
        OperatorRequest nestedLeft = OperatorRequest
                .newBuilder()
                .setLeftGeometryRequest(serviceOpLeft)
                .setOperatorType(ServiceOperatorType.ConvexHull)
                .setResultSpatialReference(spatialReferenceGall)
                .build();

        GeometryBagData geometryBagRight = GeometryBagData.newBuilder()
                .addWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSpatialReference(spatialReferenceNAD)
                .build();

        OperatorRequest serviceOpRight = OperatorRequest
                .newBuilder()
                .setLeftGeometryBag(geometryBagRight)
                .setOperatorType(ServiceOperatorType.GeodesicBuffer)
                .setBufferParams(BufferParams.newBuilder().addDistances(1000).setUnionResult(false).build())
                .setOperationSpatialReference(spatialReferenceWGS)
                .build();
        OperatorRequest nestedRight = OperatorRequest
                .newBuilder()
                .setLeftGeometryRequest(serviceOpRight)
                .setOperatorType(ServiceOperatorType.ConvexHull)
                .setResultSpatialReference(spatialReferenceGall)
                .build();

        OperatorRequest operatorRequestContains = OperatorRequest
                .newBuilder()
                .setLeftGeometryRequest(nestedLeft)
                .setRightGeometryRequest(nestedRight)
                .setOperatorType(ServiceOperatorType.Contains)
                .setOperationSpatialReference(spatialReferenceMerc)
                .build();

        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
        OperatorResult operatorResult = stub.executeOperation(operatorRequestContains);
        Map<Integer, Boolean> map = operatorResult.getRelateMapMap();

        assertTrue(map.get(0));
    }


    @Test
    public void testCrazyNesting2() {
        Polyline polyline = new Polyline();
        polyline.startPath(-120, -45);
        polyline.lineTo(-100, -55);
        polyline.lineTo(-91, -63);
        polyline.lineTo(0, 0);
        polyline.lineTo(1, 1);
        polyline.lineTo(100, 25);
        polyline.lineTo(170, 45);
        polyline.lineTo(175, 65);
        OperatorExportToWkb op = OperatorExportToWkb.local();
        OperatorImportFromWkb operatorImportFromWkb = OperatorImportFromWkb.local();

        SpatialReferenceData spatialReferenceNAD = SpatialReferenceData.newBuilder().setWkid(4269).build();
        SpatialReferenceData spatialReferenceMerc = SpatialReferenceData.newBuilder().setWkid(3857).build();
        SpatialReferenceData spatialReferenceWGS = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData spatialReferenceGall = SpatialReferenceData.newBuilder().setWkid(54016).build();
        //TODO why does esri shape fail
        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);


        GeometryBagData geometryBagLeft = GeometryBagData.newBuilder()
                .addWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSpatialReference(spatialReferenceNAD)
                .build();

        OperatorRequest serviceOpLeft = OperatorRequest
                .newBuilder()
                .setLeftGeometryBag(geometryBagLeft)
                .setOperatorType(ServiceOperatorType.Buffer)
                .setBufferParams(BufferParams.newBuilder().addDistances(.5).build())
                .setResultSpatialReference(spatialReferenceWGS)
                .build();

        Geometry bufferedLeft = GeometryEngine.buffer(polyline, SpatialReference.create(4269), .5);
        Geometry projectedBuffered = GeometryEngine.project(bufferedLeft, SpatialReference.create(4269), SpatialReference.create(4326));
        OperatorResult operatorResultLeft = stub.executeOperation(serviceOpLeft);
        SimpleByteBufferCursor simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultLeft.getGeometryBag().getWkb(0).asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(projectedBuffered, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(4326)));


        OperatorRequest nestedLeft = OperatorRequest
                .newBuilder()
                .setLeftGeometryRequest(serviceOpLeft)
                .setOperatorType(ServiceOperatorType.ConvexHull)
                .setResultSpatialReference(spatialReferenceGall)
                .build();
        Geometry projectedBufferedConvex = GeometryEngine.convexHull(projectedBuffered);
        Geometry reProjectedBufferedConvexHull = GeometryEngine.project(projectedBufferedConvex, SpatialReference.create(4326), SpatialReference.create(54016));
        OperatorResult operatorResultLeftNested = stub.executeOperation(nestedLeft);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultLeftNested.getGeometryBag().getWkb(0).asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(reProjectedBufferedConvexHull, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(54016)));

        GeometryBagData geometryBagRight = GeometryBagData.newBuilder()
                .addWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSpatialReference(spatialReferenceNAD)
                .build();

        OperatorRequest serviceOpRight = OperatorRequest
                .newBuilder()
                .setLeftGeometryBag(geometryBagRight)
                .setOperatorType(ServiceOperatorType.GeodesicBuffer)
                .setBufferParams(BufferParams.newBuilder()
                        .addDistances(1000)
                        .setUnionResult(false)
                        .build())
                .setOperationSpatialReference(spatialReferenceWGS)
                .build();

        Geometry projectedRight = GeometryEngine.project(polyline, SpatialReference.create(4269), SpatialReference.create(4326));
        Geometry projectedBufferedRight = GeometryEngine.geodesicBuffer(projectedRight, SpatialReference.create(4326), 1000);
        OperatorResult operatorResultRight = stub.executeOperation(serviceOpRight);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultRight.getGeometryBag().getWkb(0).asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(projectedBufferedRight, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(4326)));

        OperatorRequest nestedRight = OperatorRequest
                .newBuilder()
                .setLeftGeometryRequest(serviceOpRight)
                .setOperatorType(ServiceOperatorType.ConvexHull)
                .setResultSpatialReference(spatialReferenceGall)
                .build();

        Geometry projectedBufferedConvexRight = GeometryEngine.convexHull(projectedBufferedRight);
        Geometry reProjectedBufferedConvexHullRight = GeometryEngine.project(projectedBufferedConvexRight, SpatialReference.create(4326), SpatialReference.create(54016));
        OperatorResult operatorResultRightNested = stub.executeOperation(nestedRight);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultRightNested.getGeometryBag().getWkb(0).asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(reProjectedBufferedConvexHullRight, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(54016)));

        OperatorRequest operatorRequestSymDifference = OperatorRequest
                .newBuilder()
                .setLeftGeometryRequest(nestedLeft)
                .setRightGeometryRequest(nestedRight)
                .setOperatorType(ServiceOperatorType.SymmetricDifference)
                .setOperationSpatialReference(spatialReferenceMerc)
                .setResultSpatialReference(spatialReferenceNAD)
                .build();


        Geometry rightFinal = GeometryEngine.project(reProjectedBufferedConvexHullRight, SpatialReference.create(54016), SpatialReference.create(3857));
        Geometry leftFinal = GeometryEngine.project(reProjectedBufferedConvexHull, SpatialReference.create(54016), SpatialReference.create(3857));
        Geometry difference = GeometryEngine.symmetricDifference(leftFinal, rightFinal, SpatialReference.create(3857));
        Geometry differenceProjected = GeometryEngine.project(difference, SpatialReference.create(3857), SpatialReference.create(4269));

        OperatorResult operatorResult = stub.executeOperation(operatorRequestSymDifference);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResult.getGeometryBag().getWkb(0).asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(differenceProjected, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(4269)));

    }

    @Test
    public void testMultipointRoundTrip() {
        MultiPoint multiPoint = new MultiPoint();
        for (double longitude = -180; longitude < 180; longitude+=10.0) {
            for (double latitude = -80; latitude < 80; latitude+=10.0) {
                multiPoint.add(longitude, latitude);
            }
        }

        SpatialReferenceData spatialReferenceWGS = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData spatialReferenceGall = SpatialReferenceData.newBuilder().setWkid(32632).build();

        GeometryBagData geometryBag = GeometryBagData.newBuilder()
                .addWkt(GeometryEngine.geometryToWkt(multiPoint, 0))
                .setSpatialReference(spatialReferenceWGS)
                .build();

        OperatorRequest serviceProjectOp = OperatorRequest.newBuilder()
                .setLeftGeometryBag(geometryBag)
                .setOperatorType(ServiceOperatorType.Project)
                .setOperationSpatialReference(spatialReferenceGall)
                .build();

        OperatorRequest serviceReProjectOp = OperatorRequest.newBuilder()
                .setLeftGeometryRequest(serviceProjectOp)
                .setOperatorType(ServiceOperatorType.Project)
                .setOperationSpatialReference(spatialReferenceWGS)
                .setResultsEncodingType(GeometryEncodingType.wkt)
                .build();

        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
        OperatorResult operatorResult = stub.executeOperation(serviceReProjectOp);

    }

    @Test
    public void testETRS() {
        List<String> arrayDeque = new ArrayList<>();
        for (double longitude = -180; longitude < 180; longitude+=15.0) {
            for (double latitude = -90; latitude < 80; latitude+=15.0) {
                Point point = new Point(longitude, latitude);
                arrayDeque.add(OperatorExportToWkt.local().execute(0, point,null));
            }
        }

        SpatialReferenceData serviceSpatialReference = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData outputSpatialReference = SpatialReferenceData.newBuilder().setWkid(3035).build();

        GeometryBagData geometryBag = GeometryBagData.newBuilder()
                .addAllWkt(arrayDeque)
                .setSpatialReference(serviceSpatialReference)
                .build();

        OperatorRequest serviceProjectOp = OperatorRequest.newBuilder()
                .setLeftGeometryBag(geometryBag)
                .setOperatorType(ServiceOperatorType.Project)
                .setOperationSpatialReference(outputSpatialReference)
                .build();

        OperatorRequest serviceReProjectOp = OperatorRequest.newBuilder()
                .setLeftGeometryRequest(serviceProjectOp)
                .setOperatorType(ServiceOperatorType.Project)
                .setOperationSpatialReference(serviceSpatialReference)
                .setResultsEncodingType(GeometryEncodingType.wkt)
                .build();

        GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
        OperatorResult operatorResult = stub.executeOperation(serviceReProjectOp);
        SimpleStringCursor simpleByteBufferCursor = new SimpleStringCursor(operatorResult.getGeometryBag().getWktList());
        boolean bFoundEmpty = false;
        while (simpleByteBufferCursor.hasNext()) {
            String words = simpleByteBufferCursor.next();
            if (words.equals("POINT EMPTY")) {
                bFoundEmpty = true;
            }
        }
        assertTrue(bFoundEmpty);
    }
}
