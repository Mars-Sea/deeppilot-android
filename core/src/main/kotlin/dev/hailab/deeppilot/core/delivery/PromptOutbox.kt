package dev.hailab.deeppilot.core.delivery

import dev.hailab.deeppilot.core.protocol.PromptReceipt
import kotlinx.serialization.Serializable

/**
 * A prompt whose delivery the host has not confirmed yet (iOS `PendingSend`).
 * Only the text and attachment count are kept: an unconfirmed send is never
 * retried automatically, so the image data is not needed again.
 */
@Serializable
data class PendingSend(
    /** The stable send identity, also the row key. */
    val clientSendId: String,
    val sessionId: String,
    val text: String,
    val attachmentCount: Int,
    val delivery: Delivery = Delivery.Sending,
) {
    @Serializable
    enum class Delivery { Sending, Unknown, Rejected }
}

class OutboxFullException : Exception("outbox full")

/**
 * The pending-send rules from PROTOCOL.md §10, kept pure so they can be tested.
 * The app persists the list before every network send.
 */
object PromptOutbox {
    /** At most this many unconfirmed sends per host. */
    const val CAPACITY = 100

    fun add(list: List<PendingSend>, pending: PendingSend): List<PendingSend> {
        if (list.size >= CAPACITY) throw OutboxFullException()
        return list.filterNot { it.clientSendId == pending.clientSendId } + pending
    }

    fun remove(list: List<PendingSend>, clientSendId: String): List<PendingSend> =
        list.filterNot { it.clientSendId == clientSendId }

    fun mark(list: List<PendingSend>, clientSendId: String, delivery: PendingSend.Delivery): List<PendingSend> =
        list.map { if (it.clientSendId == clientSendId) it.copy(delivery = delivery) else it }

    /**
     * Applies a receipt: accepted clears the row, rejected marks it, and anything
     * else (unknown, notFound, expired) stays unconfirmed. Missing journal history
     * is not proof of rejection, and nothing is ever resent.
     */
    fun apply(list: List<PendingSend>, receipt: PromptReceipt): List<PendingSend> = when (receipt.status) {
        "accepted" -> remove(list, receipt.clientSendId)
        "rejected" -> mark(list, receipt.clientSendId, PendingSend.Delivery.Rejected)
        else -> mark(list, receipt.clientSendId, PendingSend.Delivery.Unknown)
    }
}
