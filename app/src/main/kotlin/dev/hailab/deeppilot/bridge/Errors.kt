package dev.hailab.deeppilot.bridge

/** The paired host does not advertise the capability this action needs. */
class UnsupportedFeatureException : Exception("unsupported")

/** The host answered with a frame this client cannot use. */
class InvalidResponseException : Exception("invalid response")
