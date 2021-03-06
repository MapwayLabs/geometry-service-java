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


import com.epl.service.geometry.GeometryOperatorsGrpc.GeometryOperatorsBlockingStub;
import com.epl.service.geometry.GeometryOperatorsGrpc.GeometryOperatorsStub;
import com.esri.core.geometry.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.util.RoundRobinLoadBalancerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * Sample client code that makes gRPC calls to the server.
 */
public class GeometryOperatorsClient {
    private static final Logger logger = Logger.getLogger(GeometryOperatorsClient.class.getName());


    private final ManagedChannel channel;
    private final GeometryOperatorsBlockingStub blockingStub;
    private final GeometryOperatorsStub asyncStub;


    private Random random = new Random();
    private TestHelper testHelper;


    /**
     * Construct client for accessing GeometryOperators server at {@code host:port}.
     */
    public GeometryOperatorsClient(String host, int port) {
        this(ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext(true));
    }

    public GeometryOperatorsClient(String serviceTarget) {
        this(ManagedChannelBuilder
                .forTarget(serviceTarget)
                .nameResolverFactory(new KubernetesNameResolverProvider())
                .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
                .executor(Executors.newFixedThreadPool(4))
                .usePlaintext(true));
    }

    /**
     * Construct client for accessing GeometryOperators server using the existing channel.
     */
    public GeometryOperatorsClient(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder.build();
        blockingStub = GeometryOperatorsGrpc.newBlockingStub(channel);
        asyncStub = GeometryOperatorsGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void testWRSShapefile(String pathFile) throws IOException, InterruptedException {
        File inFile = new File(pathFile);
        SpatialReferenceData operatorSpatialReference = SpatialReferenceData.newBuilder().setWkid(3857).build();
        SpatialReferenceData inputSpatialReference = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData outputSpatialReference = inputSpatialReference;

        GeometryBagData.Builder geometryBagBuilder = GeometryBagData.newBuilder()
                .addEsriShape(ByteString.copyFromUtf8(""))
                .addGeometryIds(0)
                .setSpatialReference(inputSpatialReference);

        OperatorRequest.Builder operatorRequestBuilder = OperatorRequest.newBuilder()
                .setOperatorType(ServiceOperatorType.ConvexHull)
                .getLeftGeometryRequestBuilder()
                .setResultsEncodingType(GeometryEncodingType.wkt)
                .setOperationSpatialReference(operatorSpatialReference)
                .setResultSpatialReference(outputSpatialReference);
//        OperatorRequest.Builder operatorRequestBuilder = OperatorRequest.newBuilder()
//                .setOperatorType(ServiceOperatorType.Buffer)
//                .addBufferDistances(2.5)
//                .setMaxVerticesInFullCircle(66)
//                .setResultsEncodingType(GeometryEncodingType.wkb)
//                .setOperationSpatialReference(operatorSpatialReference)
//                .setResultSpatialReference(outputSpatialReference);

        this.shapefileThrottled(inFile, operatorRequestBuilder, geometryBagBuilder);
    }

    public void testParcelsFile(String pathFile) throws IOException, InterruptedException {
        File inFile = new File(pathFile);
        String prfFile = inFile.getAbsolutePath().substring(0, inFile.getAbsolutePath().lastIndexOf('.')) + ".prj";
        String projectionWKT = new String(Files.readAllBytes(Paths.get(prfFile)));

        SpatialReferenceData serviceSpatialReference = SpatialReferenceData.newBuilder()
                .setEsriWkt(projectionWKT).build();

        SpatialReferenceData wgs84SpatiralReference = SpatialReferenceData.newBuilder()
                .setWkid(4326).build();

        GeometryBagData.Builder geometryBagBuilder = GeometryBagData.newBuilder()
                .addEsriShape(ByteString.copyFromUtf8(""))
                .addGeometryIds(0)
                .setSpatialReference(serviceSpatialReference);

        BufferParams bufferParams = BufferParams.newBuilder().addDistances(2.5).build();

        OperatorRequest.Builder operatorRequestBuilder = OperatorRequest.newBuilder()
                .setOperatorType(ServiceOperatorType.Buffer)
                .setBufferParams(bufferParams)
//                .addBufferDistances(2.5)
                .setResultsEncodingType(GeometryEncodingType.wkt)
                .setResultSpatialReference(wgs84SpatiralReference);

        this.shapefileThrottled(inFile, operatorRequestBuilder, geometryBagBuilder);
    }

    /**
     * https://github.com/ReactiveX/RxJava/wiki/Backpressure
     *
     * @param inFile
     * @throws IOException
     * @throws InterruptedException
     */
    public void shapefileThrottled(File inFile,
                                   OperatorRequest.Builder operatorRequestBuilder,
                                   GeometryBagData.Builder geometryBagBuilder) throws IOException, InterruptedException {
        CountDownLatch done = new CountDownLatch(4);
        ShapefileByteReader shapefileByteReader = new ShapefileByteReader(inFile);

        GeometryOperatorsStub geometryOperatorsStub = asyncStub
                .withMaxInboundMessageSize(2147483647)
                .withMaxOutboundMessageSize(2147483647);

        // When using manual flow-control and back-pressure on the client, the ClientResponseObserver handles both
        // request and response streams.
        ClientResponseObserver<OperatorRequest, OperatorResult> clientResponseObserver =
                new ClientResponseObserver<OperatorRequest, OperatorResult>() {
                    ClientCallStreamObserver<OperatorRequest> requestStream;

                    @Override
                    public void beforeStart(ClientCallStreamObserver<OperatorRequest> requestStream) {
                        this.requestStream = requestStream;
                        // Set up manual flow control for the response stream. It feels backwards to configure the response
                        // stream's flow control using the request stream's observer, but this is the way it is.
                        requestStream.disableAutoInboundFlowControl();

                        // Set up a back-pressure-aware producer for the request stream. The onReadyHandler will be invoked
                        // when the consuming side has enough buffer space to receive more messages.
                        //
                        // Messages are serialized into a transport-specific transmit buffer. Depending on the size of this buffer,
                        // MANY messages may be buffered, however, they haven't yet been sent to the server. The server must call
                        // request() to pull a buffered message from the client.
                        //
                        // Note: the onReadyHandler's invocation is serialized on the same thread pool as the incoming
                        // StreamObserver'sonNext(), onError(), and onComplete() handlers. Blocking the onReadyHandler will prevent
                        // additional messages from being processed by the incoming StreamObserver. The onReadyHandler must return
                        // in a timely manor or else message processing throughput will suffer.
                        requestStream.setOnReadyHandler(() -> {
                            while (requestStream.isReady()) {
                                if (shapefileByteReader.hasNext()) {
                                    byte[] data = shapefileByteReader.next();
                                    int id = shapefileByteReader.getGeometryID();
                                    ByteString byteString = ByteString.copyFrom(data);
//                                    logger.info("bytes length -->" + data.length);

                                    GeometryBagData geometryBag = geometryBagBuilder
                                            .setEsriShape(0, byteString)
                                            .setGeometryIds(0, id)
                                            .build();
                                    OperatorRequest operatorRequest = operatorRequestBuilder
                                            .setLeftGeometryBag(geometryBag).build();
                                    requestStream.onNext(operatorRequest);
                                } else {
                                    break;
                                }
                            }
                        });
                    }

                    @Override
                    public void onNext(OperatorResult operatorResult) {
                        long id = operatorResult.getGeometryBag().getGeometryIds(0);
//                        logger.info(operatorResult.getGeometryBag().getGeometryStrings(0));
                        if (id % 1000 == 0) {
                            logger.info("Geometry number " + id);
                            logger.info(operatorResult.getGeometryBag().getWkt(0));
                        }
                        // Signal the sender to send one message.
                        requestStream.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        t.printStackTrace();
                        done.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("All Done");
                        done.countDown();
                    }
                };
        // Note: clientResponseObserver is handling both request and response stream processing.
        geometryOperatorsStub.streamOperations(clientResponseObserver);

        done.await();

        channel.shutdown();
        channel.awaitTermination(1, TimeUnit.SECONDS);
    }

    public void getProjected() {
        Polyline polyline = new Polyline();
        polyline.startPath(500000, 0);
        polyline.lineTo(400000, 100000);
        polyline.lineTo(600000, -100000);
        OperatorExportToWkb op = OperatorExportToWkb.local();

        SpatialReferenceData inputSpatialReference = SpatialReferenceData.newBuilder()
                .setWkid(32632)
                .build();

        GeometryBagData geometryBag = GeometryBagData.newBuilder()
                .setSpatialReference(inputSpatialReference)
                .addWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
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

        System.out.println("executing request");
        OperatorResult operatorResult = blockingStub.executeOperation(serviceProjectOp);
        System.out.println("finished request");

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();

        Polyline result = (Polyline) op2.execute(
                0,
                Geometry.Type.Unknown,
                operatorResult
                        .getGeometryBag()
                        .getWkb(0)
                        .asReadOnlyByteBuffer(),
                null);
        System.out.println(GeometryEngine.geometryToWkt(result, 0));
    }

    /**
     * Issues several different requests and then exits.
     */
    public static void main(String[] args) throws InterruptedException {
        GeometryOperatorsClient geometryOperatorsClient = null;
        String target = System.getenv("GEOMETRY_SERVICE_TARGET");
        if (target != null)
            geometryOperatorsClient = new GeometryOperatorsClient(target);
        else
            geometryOperatorsClient = new GeometryOperatorsClient(args[0], 8980);

        System.out.println("Starting main");
        try {
            String filePath = null;
            if (args.length >= 2) {
                filePath = args[1];
            } else {
                filePath = "/data/Parcels/PARCELS.shp";
            }

            long startTime = System.nanoTime();
            geometryOperatorsClient.testWRSShapefile(filePath);
//            geometryOperatorsClient.shapefileThrottled(filePath);
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1000000;
            System.out.println("Test duration");
            System.out.println(duration);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            geometryOperatorsClient.shutdown();
        }
    }

    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }

    private void warning(String msg, Object... params) {
        logger.log(Level.WARNING, msg, params);
    }


    /**
     * Only used for unit test, as we do not want to introduce randomness in unit test.
     */
    @VisibleForTesting
    void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Only used for helping unit test.
     */
    @VisibleForTesting
    interface TestHelper {
        /**
         * Used for verify/inspect message received from server.
         */
        void onMessage(Message message);

        /**
         * Used for verify/inspect error received from server.
         */
        void onRpcError(Throwable exception);
    }

    @VisibleForTesting
    void setTestHelper(TestHelper testHelper) {
        this.testHelper = testHelper;
    }
}
