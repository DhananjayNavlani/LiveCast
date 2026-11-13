package com.dhananjay.livecast.firestore.grpc

import com.google.firestore.v1.ArrayValue
import com.google.firestore.v1.Document
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Value
import com.google.protobuf.NullValue
import com.google.protobuf.Timestamp
import java.time.Instant
import java.util.Date

fun toProtoValue(value: Any?): Value {
    val builder = Value.newBuilder()
    when (value) {
        null -> builder.setNullValue(NullValue.NULL_VALUE)
        is Boolean -> builder.setBooleanValue(value)
        is Long -> builder.setIntegerValue(value)
        is Int -> builder.setIntegerValue(value.toLong())
        is Short -> builder.setIntegerValue(value.toLong())
        is Byte -> builder.setIntegerValue(value.toLong())
        is Double -> builder.setDoubleValue(value)
        is Float -> builder.setDoubleValue(value.toDouble())
        is String -> builder.setStringValue(value)
        is Instant -> {
            val ts = Timestamp.newBuilder()
                .setSeconds(value.epochSecond)
                .setNanos(value.nano)
                .build()
            builder.setTimestampValue(ts)
        }
        is Date -> {
            val instant = value.toInstant()
            val ts = Timestamp.newBuilder()
                .setSeconds(instant.epochSecond)
                .setNanos(instant.nano)
                .build()
            builder.setTimestampValue(ts)
        }
        is Map<*, *> -> {
            val mapBuilder = MapValue.newBuilder()
            value.forEach { (k, v) ->
                if (k != null) {
                    mapBuilder.putFields(k.toString(), toProtoValue(v))
                }
            }
            builder.setMapValue(mapBuilder)
        }
        is Iterable<*> -> {
            val arrayBuilder = ArrayValue.newBuilder()
            value.forEach { item ->
                arrayBuilder.addValues(toProtoValue(item))
            }
            builder.setArrayValue(arrayBuilder)
        }
        is Array<*> -> {
            val arrayBuilder = ArrayValue.newBuilder()
            value.forEach { item ->
                arrayBuilder.addValues(toProtoValue(item))
            }
            builder.setArrayValue(arrayBuilder)
        }
        else -> throw IllegalArgumentException("Unsupported Firestore value type: ${value::class.java.name}")
    }
    return builder.build()
}

fun toKotlinValue(value: Value): Any? {
    return when (value.valueTypeCase) {
        Value.ValueTypeCase.NULL_VALUE,
        Value.ValueTypeCase.VALUETYPE_NOT_SET,
        null -> null
        Value.ValueTypeCase.BOOLEAN_VALUE -> value.booleanValue
        Value.ValueTypeCase.INTEGER_VALUE -> value.integerValue
        Value.ValueTypeCase.DOUBLE_VALUE -> value.doubleValue
        Value.ValueTypeCase.TIMESTAMP_VALUE -> {
            val ts = value.timestampValue
            Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong())
        }
        Value.ValueTypeCase.STRING_VALUE -> value.stringValue
        Value.ValueTypeCase.BYTES_VALUE -> value.bytesValue.toByteArray()
        Value.ValueTypeCase.REFERENCE_VALUE -> value.referenceValue
        Value.ValueTypeCase.GEO_POINT_VALUE -> value.geoPointValue
        Value.ValueTypeCase.ARRAY_VALUE -> value.arrayValue.valuesList.map { toKotlinValue(it) }
        Value.ValueTypeCase.MAP_VALUE -> value.mapValue.fieldsMap.mapValues { (_, v) -> toKotlinValue(v) }
        else -> null
    }
}

internal fun normalizeDocPath(path: String, projectId: String): String {
    val clean = path.trim().trim('/')
    val prefix = "projects/$projectId/databases/(default)/documents/"
    return if (clean.startsWith(prefix)) {
        clean
    } else if (clean.startsWith("projects/")) {
        clean
    } else {
        "$prefix$clean"
    }
}

internal fun toRelativeDocPath(fullOrRelativePath: String, projectId: String): String {
    val clean = fullOrRelativePath.trim().trim('/')
    val prefix = "projects/$projectId/databases/(default)/documents/"
    return if (clean.startsWith(prefix)) {
        clean.removePrefix(prefix)
    } else if (clean.contains("/documents/")) {
        clean.substringAfter("/documents/").trim('/')
    } else {
        clean
    }
}

internal fun toFirestoreDoc(doc: Document, projectId: String): FirestoreDoc {
    val relativePath = toRelativeDocPath(doc.name, projectId)
    val id = relativePath.substringAfterLast('/')
    val fields = doc.fieldsMap.mapValues { (_, v) -> toKotlinValue(v) }
    return FirestoreDoc(path = relativePath, id = id, fields = fields)
}
