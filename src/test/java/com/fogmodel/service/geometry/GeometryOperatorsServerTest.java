/*
 * Copyright 2016, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.fogmodel.service.geometry;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.esri.core.geometry.*;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import com.fogmodel.service.geometry.*;
import com.fogmodel.service.geometry.Feature;
import com.fogmodel.service.geometry.ReplacePoint;
import com.fogmodel.service.geometry.RouteNote;
import com.fogmodel.service.geometry.RouteSummary;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link GeometryOperatorsServer}.
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 *
 * <p>For basic unit test examples see {@link io.grpc.examples.helloworld.HelloWorldClientTest} and
 * {@link io.grpc.examples.helloworld.HelloWorldServerTest}.
 */
@RunWith(JUnit4.class)
public class GeometryOperatorsServerTest {
  private GeometryOperatorsServer server;
  private ManagedChannel inProcessChannel;
  private Collection<com.fogmodel.service.geometry.Feature> features;

  @Before
  public void setUp() throws Exception {
    String uniqueServerName = "in-process server for " + getClass();
    features = new ArrayList<com.fogmodel.service.geometry.Feature>();
    // use directExecutor for both InProcessServerBuilder and InProcessChannelBuilder can reduce the
    // usage timeouts and latches in test. But we still add timeout and latches where they would be
    // needed if no directExecutor were used, just for demo purpose.
    server = new GeometryOperatorsServer(
        InProcessServerBuilder.forName(uniqueServerName).directExecutor(), 0, features);
    server.start();
    inProcessChannel = InProcessChannelBuilder.forName(uniqueServerName).directExecutor().build();
  }

  @After
  public void tearDown() throws Exception {
    inProcessChannel.shutdownNow();
    server.stop();
  }

  @Test
  public void getFeature() {
    com.fogmodel.service.geometry.ReplacePoint point = com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(1).setLatitude(1).build();
    com.fogmodel.service.geometry.Feature unnamedFeature = com.fogmodel.service.geometry.Feature.newBuilder()
        .setName("").setLocation(point).build();
    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);

    // feature not found in the server
    com.fogmodel.service.geometry.Feature feature = stub.getFeature(point);

    assertEquals(unnamedFeature, feature);

    // feature found in the server
    com.fogmodel.service.geometry.Feature namedFeature = com.fogmodel.service.geometry.Feature.newBuilder()
        .setName("name").setLocation(point).build();
    features.add(namedFeature);

    feature = stub.getFeature(point);

    assertEquals(namedFeature, feature);
  }

  @Test
  public void getWKTGeometry() {
    Polyline polyline = new Polyline();
    polyline.startPath(0,0);
    polyline.lineTo(2, 3);
    polyline.lineTo(3, 3);
    OperatorExportToWkt op = OperatorExportToWkt.local();
    String geom = op.execute(0, polyline, null);
    ServiceGeometry serviceGeom = ServiceGeometry.newBuilder().setGeometryString(geom).setGeometryEncodingType("wkt").build();
    OperatorRequest requestOp = OperatorRequest.newBuilder()
            .setLeftGeometry(serviceGeom)
            .setOperatorType(Operator.Type.ExportToWkt.toString())
            .build();

    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
    OperatorResult operatorResult = stub.executeOperation(requestOp);

    assertEquals(operatorResult.getGeometry().getGeometryString(), serviceGeom.getGeometryString());
  }

  @Test
  public void getWKTGeometryFromWKB() {
    Polyline polyline = new Polyline();
    polyline.startPath(0,0);
    polyline.lineTo(2, 3);
    polyline.lineTo(3, 3);
    OperatorExportToWkb op = OperatorExportToWkb.local();

    ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().setGeometryEncodingType("wkb").setGeometryBinary(ByteString.copyFrom(op.execute(0, polyline, null))).build();
    OperatorRequest requestOp = OperatorRequest.newBuilder()
            .setLeftGeometry(serviceGeometry )
            .setOperatorType(Operator.Type.ExportToWkt.toString())
            .build();

    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
    OperatorResult operatorResult = stub.executeOperation(requestOp);

    OperatorExportToWkt op2 = OperatorExportToWkt.local();
    String geom = op2.execute(0, polyline, null);
    assertEquals(operatorResult.getGeometry().getGeometryString(), geom);
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
    OperatorExportToWkb op = OperatorExportToWkb.local();
    //TODO why does esri shape fail
//    OperatorExportToESRIShape op = OperatorExportToESRIShape.local();
//    ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().setGeometryEncodingType("esrishape").setGeometryBinary(ByteString.copyFrom(op.execute(0, polyline))).build();
    ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().setGeometryEncodingType("wkb").setGeometryBinary(ByteString.copyFrom(op.execute(0, polyline, null))).build();
    OperatorRequest serviceOp = OperatorRequest
            .newBuilder()
            .setLeftGeometry(serviceGeometry)
            .setOperatorType(Operator.Type.ConvexHull.toString())
            .build();

    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
    OperatorResult operatorResult = stub.executeOperation(serviceOp);

    OperatorImportFromWkt op2 = OperatorImportFromWkt.local();
    Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getGeometryString(), null);

    boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

    assertTrue(bContains);
  }

  @Test
  public void listFeatures() throws Exception {
    // setup
    com.fogmodel.service.geometry.Rectangle rect = com.fogmodel.service.geometry.Rectangle.newBuilder()
        .setLo(com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(0).setLatitude(0).build())
        .setHi(com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(10).setLatitude(10).build())
        .build();
    com.fogmodel.service.geometry.Feature f1 = com.fogmodel.service.geometry.Feature.newBuilder()
        .setLocation(com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(-1).setLatitude(-1).build())
        .setName("f1")
        .build(); // not inside rect
    com.fogmodel.service.geometry.Feature f2 = com.fogmodel.service.geometry.Feature.newBuilder()
        .setLocation(com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(2).setLatitude(2).build())
        .setName("f2")
        .build();
    com.fogmodel.service.geometry.Feature f3 = com.fogmodel.service.geometry.Feature.newBuilder()
        .setLocation(com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(3).setLatitude(3).build())
        .setName("f3")
        .build();
    com.fogmodel.service.geometry.Feature f4 = com.fogmodel.service.geometry.Feature.newBuilder()
        .setLocation(com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(4).setLatitude(4).build())
        .build(); // unamed
    features.add(f1);
    features.add(f2);
    features.add(f3);
    features.add(f4);
    final Collection<com.fogmodel.service.geometry.Feature> result = new HashSet<com.fogmodel.service.geometry.Feature>();
    final CountDownLatch latch = new CountDownLatch(1);
    StreamObserver<com.fogmodel.service.geometry.Feature> responseObserver =
        new StreamObserver<com.fogmodel.service.geometry.Feature>() {
          @Override
          public void onNext(com.fogmodel.service.geometry.Feature value) {
            result.add(value);
          }

          @Override
          public void onError(Throwable t) {
            fail();
          }

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        };
    GeometryOperatorsGrpc.GeometryOperatorsStub stub = GeometryOperatorsGrpc.newStub(inProcessChannel);

    // run
    stub.listFeatures(rect, responseObserver);
    assertTrue(latch.await(1, TimeUnit.SECONDS));

    // verify
    assertEquals(new HashSet<com.fogmodel.service.geometry.Feature>(Arrays.asList(f2, f3)), result);
  }

  @Test
  public void recordRoute() {
    com.fogmodel.service.geometry.ReplacePoint p1 = com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(1000).setLatitude(1000).build();
    com.fogmodel.service.geometry.ReplacePoint p2 = com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(2000).setLatitude(2000).build();
    com.fogmodel.service.geometry.ReplacePoint p3 = com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(3000).setLatitude(3000).build();
    com.fogmodel.service.geometry.ReplacePoint p4 = com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(4000).setLatitude(4000).build();
    com.fogmodel.service.geometry.Feature f1 = com.fogmodel.service.geometry.Feature.newBuilder().setLocation(p1).build(); // unamed
    com.fogmodel.service.geometry.Feature f2 = com.fogmodel.service.geometry.Feature.newBuilder().setLocation(p2).setName("f2").build();
    com.fogmodel.service.geometry.Feature f3 = com.fogmodel.service.geometry.Feature.newBuilder().setLocation(p3).setName("f3").build();
    com.fogmodel.service.geometry.Feature f4 = Feature.newBuilder().setLocation(p4).build(); // unamed
    features.add(f1);
    features.add(f2);
    features.add(f3);
    features.add(f4);

    @SuppressWarnings("unchecked")
    StreamObserver<com.fogmodel.service.geometry.RouteSummary> responseObserver =
        (StreamObserver<com.fogmodel.service.geometry.RouteSummary>) mock(StreamObserver.class);
    GeometryOperatorsGrpc.GeometryOperatorsStub stub = GeometryOperatorsGrpc.newStub(inProcessChannel);
    ArgumentCaptor<com.fogmodel.service.geometry.RouteSummary> routeSummaryCaptor = ArgumentCaptor.forClass(com.fogmodel.service.geometry.RouteSummary.class);

    StreamObserver<com.fogmodel.service.geometry.ReplacePoint> requestObserver = stub.recordRoute(responseObserver);

    requestObserver.onNext(p1);
    requestObserver.onNext(p2);
    requestObserver.onNext(p3);
    requestObserver.onNext(p4);

    verify(responseObserver, never()).onNext(any(com.fogmodel.service.geometry.RouteSummary.class));

    requestObserver.onCompleted();

    // allow some ms to let client receive the response. Similar usage later on.
    verify(responseObserver, timeout(100)).onNext(routeSummaryCaptor.capture());
    RouteSummary summary = routeSummaryCaptor.getValue();
    assertEquals(45, summary.getDistance()); // 45 is the hard coded distance from p1 to p4.
    assertEquals(2, summary.getFeatureCount());
    verify(responseObserver, timeout(100)).onCompleted();
    verify(responseObserver, never()).onError(any(Throwable.class));
  }

  @Test
  public void routeChat() {
    com.fogmodel.service.geometry.ReplacePoint p1 = com.fogmodel.service.geometry.ReplacePoint.newBuilder().setLongitude(1).setLatitude(1).build();
    com.fogmodel.service.geometry.ReplacePoint p2 = ReplacePoint.newBuilder().setLongitude(2).setLatitude(2).build();
    com.fogmodel.service.geometry.RouteNote n1 = com.fogmodel.service.geometry.RouteNote.newBuilder().setLocation(p1).setMessage("m1").build();
    com.fogmodel.service.geometry.RouteNote n2 = com.fogmodel.service.geometry.RouteNote.newBuilder().setLocation(p2).setMessage("m2").build();
    com.fogmodel.service.geometry.RouteNote n3 = com.fogmodel.service.geometry.RouteNote.newBuilder().setLocation(p1).setMessage("m3").build();
    com.fogmodel.service.geometry.RouteNote n4 = com.fogmodel.service.geometry.RouteNote.newBuilder().setLocation(p2).setMessage("m4").build();
    com.fogmodel.service.geometry.RouteNote n5 = com.fogmodel.service.geometry.RouteNote.newBuilder().setLocation(p1).setMessage("m5").build();
    com.fogmodel.service.geometry.RouteNote n6 = com.fogmodel.service.geometry.RouteNote.newBuilder().setLocation(p1).setMessage("m6").build();
    int timesOnNext = 0;

    @SuppressWarnings("unchecked")
    StreamObserver<com.fogmodel.service.geometry.RouteNote> responseObserver =
        (StreamObserver<com.fogmodel.service.geometry.RouteNote>) mock(StreamObserver.class);
    GeometryOperatorsGrpc.GeometryOperatorsStub stub = GeometryOperatorsGrpc.newStub(inProcessChannel);

    StreamObserver<com.fogmodel.service.geometry.RouteNote> requestObserver = stub.routeChat(responseObserver);
    verify(responseObserver, never()).onNext(any(com.fogmodel.service.geometry.RouteNote.class));

    requestObserver.onNext(n1);
    verify(responseObserver, never()).onNext(any(com.fogmodel.service.geometry.RouteNote.class));

    requestObserver.onNext(n2);
    verify(responseObserver, never()).onNext(any(com.fogmodel.service.geometry.RouteNote.class));

    requestObserver.onNext(n3);
    ArgumentCaptor<com.fogmodel.service.geometry.RouteNote> routeNoteCaptor = ArgumentCaptor.forClass(com.fogmodel.service.geometry.RouteNote.class);
    verify(responseObserver, timeout(100).times(++timesOnNext)).onNext(routeNoteCaptor.capture());
    com.fogmodel.service.geometry.RouteNote result = routeNoteCaptor.getValue();
    assertEquals(p1, result.getLocation());
    assertEquals("m1", result.getMessage());

    requestObserver.onNext(n4);
    routeNoteCaptor = ArgumentCaptor.forClass(com.fogmodel.service.geometry.RouteNote.class);
    verify(responseObserver, timeout(100).times(++timesOnNext)).onNext(routeNoteCaptor.capture());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 1);
    assertEquals(p2, result.getLocation());
    assertEquals("m2", result.getMessage());

    requestObserver.onNext(n5);
    routeNoteCaptor = ArgumentCaptor.forClass(com.fogmodel.service.geometry.RouteNote.class);
    timesOnNext += 2;
    verify(responseObserver, timeout(100).times(timesOnNext)).onNext(routeNoteCaptor.capture());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 2);
    assertEquals(p1, result.getLocation());
    assertEquals("m1", result.getMessage());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 1);
    assertEquals(p1, result.getLocation());
    assertEquals("m3", result.getMessage());

    requestObserver.onNext(n6);
    routeNoteCaptor = ArgumentCaptor.forClass(RouteNote.class);
    timesOnNext += 3;
    verify(responseObserver, timeout(100).times(timesOnNext)).onNext(routeNoteCaptor.capture());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 3);
    assertEquals(p1, result.getLocation());
    assertEquals("m1", result.getMessage());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 2);
    assertEquals(p1, result.getLocation());
    assertEquals("m3", result.getMessage());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 1);
    assertEquals(p1, result.getLocation());
    assertEquals("m5", result.getMessage());

    requestObserver.onCompleted();
    verify(responseObserver, timeout(100)).onCompleted();
    verify(responseObserver, never()).onError(any(Throwable.class));
  }
}
