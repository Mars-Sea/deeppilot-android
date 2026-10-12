package dev.hailab.deeppilot.bridge

/** The paired host does not advertise the capability this action needs. */
class UnsupportedFeatureException : Exception("unsupported")

/** The host answered with a frame this client cannot use. */
class InvalidResponseException : Exception("invalid response")

/** The host may or may not have received a prompt; it is never resent automatically. */
class PromptUnconfirmedException : Exception("prompt delivery unconfirmed")

/** The host refused a prompt outright. */
class PromptRejectedException : Exception("prompt rejected")

/** The pending-send row could not be written, so the prompt was not sent. */
class OutboxSaveException : Exception("outbox save failed")
