package com.dhananjay.livecast.firestore.grpc

import com.google.firestore.v1.StructuredQuery
import com.google.protobuf.Int32Value

fun buildStructuredQuery(query: FirestoreQuery): StructuredQuery {
    val fromSelector = StructuredQuery.CollectionSelector.newBuilder()
        .setCollectionId(query.collectionId)
        .setAllDescendants(query.allDescendants)

    val sq = StructuredQuery.newBuilder()
        .addFrom(fromSelector)

    val filters = query.whereEquals.map { (fieldPath, value) ->
        StructuredQuery.Filter.newBuilder()
            .setFieldFilter(
                StructuredQuery.FieldFilter.newBuilder()
                    .setField(StructuredQuery.FieldReference.newBuilder().setFieldPath(fieldPath))
                    .setOp(StructuredQuery.FieldFilter.Operator.EQUAL)
                    .setValue(toProtoValue(value))
            ).build()
    }

    if (filters.size == 1) {
        sq.setWhere(filters[0])
    } else if (filters.size > 1) {
        sq.setWhere(
            StructuredQuery.Filter.newBuilder()
                .setCompositeFilter(
                    StructuredQuery.CompositeFilter.newBuilder()
                        .setOp(StructuredQuery.CompositeFilter.Operator.AND)
                        .addAllFilters(filters)
                )
        )
    }

    query.orderBy.forEach { (fieldPath, direction) ->
        val dir = when (direction) {
            Direction.ASCENDING -> StructuredQuery.Direction.ASCENDING
            Direction.DESCENDING -> StructuredQuery.Direction.DESCENDING
        }
        sq.addOrderBy(
            StructuredQuery.Order.newBuilder()
                .setField(StructuredQuery.FieldReference.newBuilder().setFieldPath(fieldPath))
                .setDirection(dir)
                .build()
        )
    }

    query.limit?.let { limitVal ->
        sq.setLimit(Int32Value.of(limitVal))
    }

    return sq.build()
}
