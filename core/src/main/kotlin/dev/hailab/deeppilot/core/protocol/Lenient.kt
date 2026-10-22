package dev.hailab.deeppilot.core.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull

// The bridge is TypeScript, where every number is a double, and iOS decodes them
// as Double. A Kotlin Long refuses `1756000000000.5` or `1.756e12`, and one such
// value used to fail the whole frame: a tail with one odd row showed no messages.

/** A Long that also accepts a fractional or exponent JSON number (truncated). */
object LenientLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        if (decoder !is JsonDecoder) return decoder.decodeLong()
        val primitive = decoder.decodeJsonElement() as? JsonPrimitive ?: throw IllegalArgumentException("expected a number")
        return primitive.longOrNull
            ?: primitive.doubleOrNull?.takeIf { it.isFinite() }?.toLong()
            ?: throw IllegalArgumentException("expected a number, got ${primitive.content}")
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/** A list that drops elements it cannot decode instead of failing the whole frame. */
open class SkippingListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val strict = ListSerializer(element)
    override val descriptor: SerialDescriptor = strict.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        if (decoder !is JsonDecoder) return strict.deserialize(decoder)
        val array = decoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            try {
                decoder.json.decodeFromJsonElement(element, item)
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<T>) = strict.serialize(encoder, value)
}

object MessageListSerializer : SkippingListSerializer<MessageProjection>(MessageProjection.serializer())

object SessionListSerializer : SkippingListSerializer<SessionSummary>(SessionSummary.serializer())
