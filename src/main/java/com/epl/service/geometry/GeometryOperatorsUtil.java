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
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Common utilities for the GeometryOperators demo.
 */
class SpatialReferenceGroup {
    SpatialReference leftSR;
    SpatialReference rightSR;
    SpatialReference resultSR;
    SpatialReference operatorSR;

    static SpatialReference spatialFromGeometry(GeometryBagData geometryBagData,
                                                    OperatorRequest nestedRequest) {
        if (geometryBagData.hasSpatialReference()) {
            return GeometryOperatorsUtil.__extractSpatialReference(geometryBagData);
        }

        return GeometryOperatorsUtil.__extractSpatialReferenceCursor(nestedRequest);
    }

    SpatialReferenceGroup(OperatorRequest operatorRequest1,
                          SpatialReferenceData paramsSR,
                          GeometryBagData geometryBagData,
                          OperatorRequest nestedRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryOperatorsUtil.__extractSpatialReference(paramsSR);

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest1.getResultSpatialReference());

        leftSR = SpatialReferenceGroup.spatialFromGeometry(geometryBagData, nestedRequest);

        // TODO, there are possibilities for error in here. Also possiblities for too many assumptions. ass of you an me.
        // if there is a rightSR and a leftSR geometry but no operatorSR spatial reference, then set operatorSpatialReference
        if (operatorSR == null && leftSR != null) {
            operatorSR = leftSR;
        }

        if (leftSR == null) {
            leftSR = operatorSR;
        }

        // if there is no resultSpatialReference set it to be the operatorSpatialReference
        if (resultSR == null) {
            resultSR = operatorSR;
        }
    }

    SpatialReferenceGroup(OperatorRequest operatorRequest1,
                          SpatialReferenceData paramsSR,
                          GeometryBagData leftGeometryBagData,
                          OperatorRequest leftNestedRequest,
                          GeometryBagData rightGeometryBagData,
                          OperatorRequest rightNestedRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryOperatorsUtil.__extractSpatialReference(paramsSR);

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest1.getResultSpatialReference());

        leftSR = SpatialReferenceGroup.spatialFromGeometry(leftGeometryBagData, leftNestedRequest);

        rightSR = SpatialReferenceGroup.spatialFromGeometry(rightGeometryBagData, rightNestedRequest);

        // TODO, there are possibilities for error in here. Also possiblities for too many assumptions. ass of you an me.
        // if there is a rightSR and a leftSR geometry but no operatorSR spatial reference, then set operatorSpatialReference
        if (operatorSR == null && leftSR != null && (rightSR == null || leftSR.equals(rightSR))) {
            operatorSR = leftSR;
        }

        if (leftSR == null) {
            leftSR = operatorSR;
            if (rightSR == null) {
                rightSR = operatorSR;
            }
        }

        // TODO improve geometry to work with local spatial references. This is super ugly as it stands
        if (((leftSR != null && rightSR == null) || (leftSR == null && rightSR != null))) {
            throw new IllegalArgumentException("either both spatial references are local or neither");
        }

        // if there is no resultSpatialReference set it to be the operatorSpatialReference
        if (resultSR == null) {
            resultSR = operatorSR;
        }
    }

    SpatialReferenceGroup(OperatorRequest operatorRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getOperationSpatialReference());

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getResultSpatialReference());

        if (operatorRequest.hasLeftGeometryBag() && operatorRequest.getLeftGeometryBag().hasSpatialReference()) {
            leftSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getLeftGeometryBag());
        } else {
            // assumes left cursor exists
            leftSR = GeometryOperatorsUtil.__extractSpatialReferenceCursor(operatorRequest.getLeftNestedRequest());
        }

        if (operatorRequest.hasRightGeometryBag() && operatorRequest.getRightGeometryBag().hasSpatialReference()) {
            rightSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getRightGeometryBag());
        } else if (operatorRequest.hasRightNestedRequest()){
            rightSR = GeometryOperatorsUtil.__extractSpatialReferenceCursor(operatorRequest.getRightNestedRequest());
        }

        // TODO, there are possibilities for error in here. Also possiblities for too many assumptions. ass of you an me.
        // if there is a rightSR and a leftSR geometry but no operatorSR spatial reference, then set operatorSpatialReference
        if (operatorSR == null && leftSR != null
                && (rightSR == null || leftSR.equals(rightSR))) {
            operatorSR = leftSR;
        }

        if (leftSR == null) {
            leftSR = operatorSR;
            if (rightSR == null && (operatorRequest.hasRightGeometryBag() || operatorRequest.hasRightNestedRequest())) {
                rightSR = operatorSR;
            }
        }

        // TODO improve geometry to work with local spatial references. This is super ugly as it stands
        if ((operatorRequest.hasRightNestedRequest() || operatorRequest.hasRightGeometryBag()) &&
                ((leftSR != null && rightSR == null) ||
                        (leftSR == null && rightSR != null))) {
            throw new IllegalArgumentException("either both spatial references are local or neither");
        }

        // if there is no resultSpatialReference set it to be the operatorSpatialReference
        if (resultSR == null) {
            resultSR = operatorSR;
        }
    }
}

class ByteStringIterable implements Iterable<com.google.protobuf.ByteString> {
    ByteBufferCursor m_byteBufferCursor;
    ByteStringIterable(ByteBufferCursor byteBufferCursor) {
        m_byteBufferCursor = byteBufferCursor;
    }

    @Override
    public Iterator<ByteString> iterator() {
        return new Iterator<ByteString>() {
            @Override
            public boolean hasNext() {
                return m_byteBufferCursor.hasNext();
            }

            @Override
            public ByteString next() {
                return ByteString.copyFrom(m_byteBufferCursor.next());
            }
        };
    }
}

class StringIterable implements Iterable<String> {
    StringCursor m_stringCursor;
    StringIterable(StringCursor stringCursor) {
        m_stringCursor = stringCursor;
    }


    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return m_stringCursor.hasNext();
            }

            @Override
            public String next() {
                return m_stringCursor.next();
            }
        };
    }
}

public class GeometryOperatorsUtil {
    public static GeometryBagData __encodeGeometry(GeometryCursor geometryCursor, OperatorRequest operatorRequest, GeometryEncodingType encodingType) {
        GeometryBagData.Builder geometryBagBuilder = GeometryBagData.newBuilder();


        // TODO not getting stubbed out due to grpc proto stubbing bug
        if (encodingType == null || encodingType == GeometryEncodingType.unknown) {
            if (operatorRequest.getResultsEncodingType() == GeometryEncodingType.unknown) {
                encodingType = GeometryEncodingType.wkb;
            } else {
                encodingType = operatorRequest.getResultsEncodingType();
            }
        }

        ByteStringIterable binaryStringIterable;
        StringIterable stringIterable;
        switch (encodingType) {
            case wkb:
                binaryStringIterable = new ByteStringIterable(new OperatorExportToWkbCursor(0, geometryCursor));
                geometryBagBuilder.addAllGeometryBinaries(binaryStringIterable);
                break;
            case wkt:
                stringIterable = new StringIterable(new OperatorExportToWktCursor(0, geometryCursor, null));
                geometryBagBuilder.addAllGeometryStrings(stringIterable);
                break;
            case esrishape:
                binaryStringIterable = new ByteStringIterable(new OperatorExportToESRIShapeCursor(0, geometryCursor));
                geometryBagBuilder.addAllGeometryBinaries(binaryStringIterable);
                break;
            case geojson:
                //TODO I'm just blindly setting the spatial reference here instead of projecting the resultSR into the spatial reference
                // TODO add Spatial reference
                stringIterable = new StringIterable(new OperatorExportToJsonCursor(null, geometryCursor));
                geometryBagBuilder.addAllGeometryStrings(stringIterable);
                break;
        }

        //TODO I'm just blindly setting the spatial reference here instead of projecting the resultSR into the spatial reference
        // TODO There needs to be better tracking of geometry id throughout process
        geometryBagBuilder
                .setGeometryEncodingType(encodingType)
                .addAllGeometryIds(operatorRequest.getLeftGeometryBag().getGeometryIdsList())
                .setSpatialReference(operatorRequest.getResultSpatialReference());

        return geometryBagBuilder.build();
    }

    public static GeometryCursor __getLeftNestedRequestFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            SpatialReferenceGroup srGroup) throws IOException {
        if (leftCursor == null) {
            if (operatorRequest.hasLeftGeometryBag())
                leftCursor = __createGeometryCursor(operatorRequest.getLeftGeometryBag());
            else
                // assumes there is always a left geometry
                leftCursor = cursorFromRequest(operatorRequest.getLeftNestedRequest(), null, null);
        }

        // project left if needed
        if (srGroup.operatorSR != null && !srGroup.operatorSR.equals(srGroup.leftSR)) {
            ProjectionTransformation projectionTransformation = new ProjectionTransformation(srGroup.leftSR, srGroup.operatorSR);
            leftCursor = OperatorProject.local().execute(leftCursor, projectionTransformation, null);
        }

        return leftCursor;
    }

    public static GeometryCursor __getRightNestedRequestFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor,
            SpatialReferenceGroup srGroup) throws IOException {
        if (leftCursor != null && rightCursor == null) {
            if (operatorRequest.hasRightGeometryBag()) {
                rightCursor = __createGeometryCursor(operatorRequest.getRightGeometryBag());
            } else if (operatorRequest.hasRightNestedRequest()) {
                rightCursor = cursorFromRequest(operatorRequest.getRightNestedRequest(), null, null);
            }
        }

        if (rightCursor != null && srGroup.operatorSR != null && !srGroup.operatorSR.equals(srGroup.rightSR)) {
            ProjectionTransformation projectionTransformation = new ProjectionTransformation(srGroup.rightSR, srGroup.operatorSR);
            rightCursor = OperatorProject.local().execute(rightCursor, projectionTransformation, null);
        }
        return rightCursor;
    }

    public static OperatorResult nonCursorFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor) throws IOException {
        SpatialReferenceGroup srGroup = new SpatialReferenceGroup(operatorRequest);
        leftCursor = __getLeftNestedRequestFromRequest(operatorRequest, leftCursor, srGroup);
        rightCursor = __getRightNestedRequestFromRequest(operatorRequest, leftCursor, rightCursor, srGroup);

        OperatorResult.Builder operatorResultBuilder = OperatorResult.newBuilder();
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        switch (operatorType) {
            case Proximity2D:
                break;
            case Relate:
                boolean result = OperatorRelate.local().execute(leftCursor.next(), rightCursor.next(), srGroup.operatorSR, operatorRequest.getRelateParams().getDe9Im(), null);
                operatorResultBuilder.setSpatialRelationship(result);
                break;
            case Equals:
            case Disjoint:
            case Intersects:
            case Within:
            case Contains:
            case Crosses:
            case Touches:
            case Overlaps:
                HashMap<Integer, Boolean> result_map = ((OperatorSimpleRelation) OperatorFactoryLocal.getInstance().getOperator(operatorType)).execute(leftCursor.next(), rightCursor, srGroup.operatorSR, null);
                if (result_map.size() == 1) {
                    operatorResultBuilder.setSpatialRelationship(result_map.get(0));
                    operatorResultBuilder.putAllRelateMap(result_map);
                } else {
                    operatorResultBuilder.putAllRelateMap(result_map);
                }
                break;
            case Distance:
                operatorResultBuilder.setDistance(OperatorDistance.local().execute(leftCursor.next(), rightCursor.next(), null));
                break;
            case GeodeticLength:
                break;
            case GeodeticArea:
                break;
            default:
                throw new IllegalArgumentException();
        }
        return operatorResultBuilder.build();
    }

    public static GeometryCursor cursorFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor) throws IOException {
        SpatialReferenceGroup srGroup = new SpatialReferenceGroup(operatorRequest);
        leftCursor = __getLeftNestedRequestFromRequest(operatorRequest, leftCursor, srGroup);
        rightCursor = __getRightNestedRequestFromRequest(operatorRequest, leftCursor, rightCursor, srGroup);

        GeometryCursor resultCursor = null;
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        switch (operatorType) {
            case DensifyByAngle:
                break;
            case LabelPoint:
                break;
            case GeodesicBuffer:
                List<Double> doubleList = operatorRequest.getBufferParams().getDistancesList();
//                srGroup = new SpatialReferenceGroup(operatorRequest.getBufferParams(), operatorRequest.getResultSpatialReference());
                double maxDeviations = Double.NaN;
                if (operatorRequest.getBufferParams().getMaxDeviationsCount() > 0) {
                    maxDeviations = operatorRequest.getBufferParams().getMaxDeviations(0);
                }
                resultCursor = OperatorGeodesicBuffer.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        0,
                        doubleList.stream().mapToDouble(Double::doubleValue).toArray(),
                        maxDeviations,
                        false,
                        operatorRequest.getBufferParams().getUnionResult(),
                        null);
                break;
            case GeodeticDensifyByLength:
                resultCursor = OperatorGeodeticDensifyByLength.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        operatorRequest.getDensifyParams().getMaxLength(),
                        0,
                        null);
                break;
            case ShapePreservingDensify:
                break;
            case GeneralizeByArea:
                resultCursor = OperatorGeneralizeByArea.local().execute(
                        leftCursor,
                        operatorRequest.getGeneralizeParams().getMaxDeviation(),
                        operatorRequest.getGeneralizeParams().getRemoveDegenerates(),
                        GeneralizeType.ResultContainsOriginal,
                        srGroup.operatorSR,
                        null);
                break;
            case Project:
                resultCursor = leftCursor;
                break;
            case Union:
                resultCursor = OperatorUnion.local().execute(leftCursor, srGroup.operatorSR, null);
                break;
            case Difference:
                resultCursor = OperatorDifference.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null);
                break;
            case Buffer:
                // TODO clean this up
                //                GeometryCursor inputGeometryBag,
                //                SpatialReference sr,
                //                double[] distances,
                //                double max_deviation,
                //                int max_vertices_in_full_circle,
                //                boolean b_union,
                //                ProgressTracker progressTracker
                //
//                srGroup = new SpatialReferenceGroup(operatorRequest.getBufferParams(), operatorRequest.getResultSpatialReference());
                int maxverticesFullCircle = operatorRequest.getBufferParams().getMaxVerticesInFullCircle();
                if (maxverticesFullCircle == 0) {
                    maxverticesFullCircle = 96;
                }

                double[] d = operatorRequest.getBufferParams().getDistancesList().stream().mapToDouble(Double::doubleValue).toArray();
                resultCursor = OperatorBuffer.local().execute(leftCursor,
                                                              srGroup.operatorSR,
                                                              d,
                                                              Double.NaN,
                                                              maxverticesFullCircle,
                                                              operatorRequest.getBufferParams().getUnionResult(),
                                                              null);

                //                resultCursor = OperatorBuffer.local().execute(leftCursor, srGroup.operatorSR, d, operatorRequest.getBufferUnionResult(), null);
                break;
            case Intersection:
                // TODO hasIntersectionDimensionMask needs to be automagically generated
                if (operatorRequest.hasIntersectionParams() && operatorRequest.getIntersectionParams().getDimensionMask() != 0)
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null, operatorRequest.getIntersectionParams().getDimensionMask());
                else
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null);
                break;
            case Clip:
                Envelope2D envelope2D = __extractEnvelope2D(operatorRequest.getClipParams().getEnvelope());
                resultCursor = OperatorClip.local().execute(leftCursor, envelope2D, srGroup.operatorSR, null);
                break;
            case Cut:
                resultCursor = OperatorCut.local().execute(operatorRequest.getCutParams().getConsiderTouch(), leftCursor.next(), (Polyline) rightCursor.next(), srGroup.operatorSR, null);
                break;
            case DensifyByLength:
                resultCursor = OperatorDensifyByLength.local().execute(leftCursor, operatorRequest.getDensifyParams().getMaxLength(), null);
                break;
            case Simplify:
                resultCursor = OperatorSimplify.local().execute(leftCursor, srGroup.operatorSR, operatorRequest.getSimplifyParams().getForce(), null);
                break;
            case SimplifyOGC:
                resultCursor = OperatorSimplifyOGC.local().execute(leftCursor, srGroup.operatorSR, operatorRequest.getSimplifyParams().getForce(), null);
                break;
            case Offset:
                resultCursor = OperatorOffset.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        operatorRequest.getOffsetParams().getDistance(),
                        OperatorOffset.JoinType.valueOf(operatorRequest.getOffsetParams().getJoinType().toString()),
                        operatorRequest.getOffsetParams().getBevelRatio(),
                        operatorRequest.getOffsetParams().getFlattenError(), null);
                break;
            case Generalize:
                resultCursor = OperatorGeneralize.local().execute(
                        leftCursor,
                        operatorRequest.getGeneralizeParams().getMaxDeviation(),
                        operatorRequest.getGeneralizeParams().getRemoveDegenerates(),
                        null);
                break;
            case SymmetricDifference:
                resultCursor = OperatorSymmetricDifference.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null);
                break;
            case ConvexHull:
                resultCursor = OperatorConvexHull.local().execute(leftCursor, operatorRequest.getConvexParams().getConvexHullMerge(), null);
                break;
            case Boundary:
                resultCursor = OperatorBoundary.local().execute(leftCursor, null);
                break;
            case EnclosingCircle:
                resultCursor = new OperatorEnclosingCircleCursor(leftCursor, srGroup.operatorSR, null);
                break;
            case RandomPoints:
                double[] pointsPerSqrKm = operatorRequest.getRandomPointsParams().getPointsPerSquareKmList().stream().mapToDouble(Double::doubleValue).toArray();
                long seed = operatorRequest.getRandomPointsParams().getSeed();
                resultCursor = new OperatorRandomPointsCursor(
                        leftCursor,
                        pointsPerSqrKm,
                        seed,
                        srGroup.operatorSR,
                        null);
                break;
            default:
                throw new IllegalArgumentException();

        }

        if (srGroup.resultSR != null && !srGroup.resultSR.equals(srGroup.operatorSR)) {
            ProjectionTransformation projectionTransformation = new ProjectionTransformation(srGroup.operatorSR, srGroup.resultSR);
            resultCursor = OperatorProject.local().execute(resultCursor, projectionTransformation, null);
        }

        return resultCursor;
    }

    public static OperatorResult initExecuteOperatorEx(OperatorRequest operatorRequest) throws IOException {
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        GeometryEncodingType encodingType = GeometryEncodingType.unknown;
        GeometryCursor resultCursor = null;
        OperatorResult.Builder operatorResultBuilder = OperatorResult.newBuilder();
        switch (operatorType) {
            // results
            case Proximity2D:
            case Relate:
            case Equals:
            case Disjoint:
            case Intersects:
            case Within:
            case Contains:
            case Crosses:
            case Touches:
            case Overlaps:
            case Distance:
            case GeodeticLength:
            case GeodeticArea:
                return nonCursorFromRequest(operatorRequest, null, null);

            // cursors
            case Project:
            case Union:
            case Difference:
            case Buffer:
            case Intersection:
            case Clip:
            case Cut:
            case DensifyByLength:
            case DensifyByAngle:
            case LabelPoint:
            case GeodesicBuffer:
            case GeodeticDensifyByLength:
            case ShapePreservingDensify:
            case Simplify:
            case SimplifyOGC:
            case Offset:
            case Generalize:
            case GeneralizeByArea:
            case SymmetricDifference:
            case ConvexHull:
            case Boundary:
            case RandomPoints:
            case EnclosingCircle:
                resultCursor = cursorFromRequest(operatorRequest, null, null);
                break;
            case ExportToESRIShape:
                encodingType = GeometryEncodingType.esrishape;
                break;
            case ExportToWkb:
                encodingType = GeometryEncodingType.wkb;
                break;
            case ExportToWkt:
                encodingType = GeometryEncodingType.wkt;
                break;
            case ExportToGeoJson:
                encodingType = GeometryEncodingType.geojson;
                break;
        }
        // If the only operation used by the user is to export to one of the formats then enter this if statement and
        // assign the left cursor to the result cursor
        if (encodingType != GeometryEncodingType.unknown) {
            resultCursor = __createGeometryCursor(operatorRequest.getLeftGeometryBag());
        }
        operatorResultBuilder.setGeometryBag(__encodeGeometry(resultCursor, operatorRequest, encodingType));
        return operatorResultBuilder.build();
    }


    protected static GeometryCursor __createGeometryCursor(GeometryBagData geometryBag) throws IOException {
        return __extractGeometryCursor(geometryBag);
    }


    protected static SpatialReference __extractSpatialReference(GeometryBagData geometryBag) {
        return geometryBag.hasSpatialReference() ? __extractSpatialReference(geometryBag.getSpatialReference()) : null;
    }


    protected static SpatialReference __extractSpatialReferenceCursor(OperatorRequest operatorRequestCursor) {
        if (operatorRequestCursor.hasResultSpatialReference())
            return __extractSpatialReference(operatorRequestCursor.getResultSpatialReference());
        else if (operatorRequestCursor.hasOperationSpatialReference())
            return __extractSpatialReference(operatorRequestCursor.getOperationSpatialReference());
        else if (operatorRequestCursor.hasLeftNestedRequest())
            return __extractSpatialReferenceCursor(operatorRequestCursor.getLeftNestedRequest());
        else if (operatorRequestCursor.hasLeftGeometryBag())
            return __extractSpatialReference(operatorRequestCursor.getLeftGeometryBag().getSpatialReference());
        return null;
    }


    protected static SpatialReference __extractSpatialReference(SpatialReferenceData serviceSpatialReference) {
        // TODO there seems to be a bug where hasWkid() is not getting generated. check back later
        if (serviceSpatialReference.getWkid() != 0)
            return SpatialReference.create(serviceSpatialReference.getWkid());
        else if (serviceSpatialReference.getEsriWkt().length() > 0)
            return SpatialReference.create(serviceSpatialReference.getEsriWkt());

        return null;
    }


    protected static Envelope2D __extractEnvelope2D(EnvelopeData env) {
        return Envelope2D.construct(env.getXmin(), env.getYmin(), env.getXmax(), env.getYmax());
    }


    protected static GeometryCursor __extractGeometryCursor(GeometryBagData geometryBag) throws IOException {
        GeometryCursor geometryCursor = null;

        ArrayDeque<ByteBuffer> byteBufferArrayDeque = null;
        ArrayDeque<String> stringArrayDeque = null;
        SimpleByteBufferCursor simpleByteBufferCursor = null;
        SimpleStringCursor simpleStringCursor = null;
        switch (geometryBag.getGeometryEncodingType()) {
            case wkb:
                byteBufferArrayDeque = geometryBag
                        .getGeometryBinariesList()
                        .stream()
                        .map(com.google.protobuf.ByteString::asReadOnlyByteBuffer)
                        .collect(Collectors.toCollection(ArrayDeque::new));
                simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferArrayDeque);
                geometryCursor = new OperatorImportFromWkbCursor(0, simpleByteBufferCursor);
                break;
            case esrishape:
                byteBufferArrayDeque = geometryBag
                        .getGeometryBinariesList()
                        .stream()
                        .map(com.google.protobuf.ByteString::asReadOnlyByteBuffer)
                        .collect(Collectors.toCollection(ArrayDeque::new));
                simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferArrayDeque);
                geometryCursor = new OperatorImportFromESRIShapeCursor(0, 0, simpleByteBufferCursor);
                break;
            case wkt:
                stringArrayDeque = new ArrayDeque<>(geometryBag.getGeometryStringsList());
                simpleStringCursor = new SimpleStringCursor(stringArrayDeque);
                geometryCursor = new OperatorImportFromWktCursor(0, simpleStringCursor);
                break;
            case geojson:
                JsonFactory factory = new JsonFactory();
                String jsonString = geometryBag.getGeometryStrings(0);
                // TODO no idea whats going on here
                JsonParser jsonParser = factory.createJsonParser(jsonString);
                JsonParserReader jsonParserReader = new JsonParserReader(jsonParser);
                SimpleJsonReaderCursor simpleJsonParserCursor = new SimpleJsonReaderCursor(jsonParserReader);
                MapGeometryCursor mapGeometryCursor = new OperatorImportFromJsonCursor(0, simpleJsonParserCursor);
                geometryCursor = new SimpleGeometryCursor(mapGeometryCursor);
        }
        return geometryCursor;
    }
}
