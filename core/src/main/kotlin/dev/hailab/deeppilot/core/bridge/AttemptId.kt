package dev.hailab.deeppilot.core.bridge

import okhttp3.HttpUrl
import okhttp3.Request
import kotlin.random.Random

/**
 * The diagnostic `attemptId` (PROTOCOL.md §2.0): a random 8-hex-digit tag that
 * lets a phone-side failure be matched with the computer's log. It takes no part
 * in authentication; anything that goes wrong attaching it leaves the request as it was.
 */
object AttemptId {
    const val HEADER = "X-DeepPilot-Attempt"
    const val QUERY = "at"
    private val PATTERN = Regex("^[A-Za-z0-9_-]{1,16}$")

    fun make(random: Random = Random.Default): String = "%08x".format(random.nextInt())

    fun isValid(value: String?): Boolean = value != null && PATTERN.matches(value)

    /** The WebSocket upgrade URL with `at=<id>`, replacing an older one. */
    fun handshakeUrl(url: HttpUrl, attemptId: String?): HttpUrl {
        if (!isValid(attemptId)) return url
        return runCatching { url.newBuilder().removeAllQueryParameters(QUERY).addQueryParameter(QUERY, attemptId).build() }.getOrDefault(url)
    }

    /** Adds the header for `/phone/health` and `/phone/pair`. */
    fun apply(builder: Request.Builder, attemptId: String?): Request.Builder =
        if (isValid(attemptId)) builder.header(HEADER, attemptId!!) else builder
}
