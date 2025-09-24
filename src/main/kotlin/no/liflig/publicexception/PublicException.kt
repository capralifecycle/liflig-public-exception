package no.liflig.publicexception

import no.liflig.logging.ExceptionWithLoggingContext
import no.liflig.logging.LogField
import no.liflig.logging.LogLevel

/**
 * Exception with a message that is meant to be exposed publicly to users.
 *
 * When this is thrown in the context of an HTTP handler using
 * [`liflig-http4k-setup`](https://github.com/capralifecycle/liflig-http4k-setup), then it will be
 * caught, logged, and turned into an HTTP response with a body on the
 * [Problem Details](https://datatracker.ietf.org/doc/html/rfc7807) format.
 *
 * If you're mapping a different exception to a `PublicException`, remember to set [cause] so that
 * the original exception is included in the logs.
 *
 * It is recommended to use named parameters for [publicMessage] and [publicDetail], to make it
 * explicit that these are exposed publicly.
 *
 * ### Example
 *
 * ```kotlin
 * throw PublicException(
 *     ErrorCode.BAD_REQUEST,
 *     publicMessage = "Invalid request body",
 *     publicDetail = "Missing required field 'id'",
 * )
 * ```
 *
 * This maps to a 400 Bad Request HTTP response, with the following Problem Details body:
 * ```json
 * {
 *   "title": "Invalid request body",
 *   "detail": "Missing required field 'id'",
 *   "status": 400,
 *   "instance": "/api/example" // API path where the error occurred
 * }
 * ```
 *
 * @param errorCode The status code to use when the exception is mapped to an HTTP response.
 * @param publicMessage The safe-to-expose public message. When mapped to an HTTP response, it will
 *   be in the `title` field of the Problem Details JSON body.
 *
 *   Also included in the logged exception message.
 *
 * @param publicDetail An optional extra message to show to the client. When mapped to an HTTP
 *   response, it will be in the `detail` field of the Problem Details body.
 *
 *   Also included in the logged exception message (separated by " - " after [publicMessage]).
 *
 * @param internalDetail Additional detail message to attach to the exception for internal logging.
 *   Not included in HTTP responses.
 *
 *   Included in the exception message in parentheses after [publicMessage]/[publicDetail].
 *
 * @param cause If mapping a different exception to a `PublicException`, set or override this
 *   parameter so that the original exception is also included in the logs.
 * @param severity By default, the `LoggingFilter` from `liflig-http4k-setup` logs at the `ERROR`
 *   log level for [ErrorCode.INTERNAL_SERVER_ERROR], and `INFO` for everything else. If you want to
 *   override this log level (independent of the error status code), you can pass a custom severity
 *   for the exception here.
 * @param logFields Structured key-value fields to include when the exception is logged. You can
 *   construct fields with the [field][no.liflig.logging.field] function from `liflig-logging`.
 */
public open class PublicException(
    public val errorCode: ErrorCode,
    public val publicMessage: String,
    public val publicDetail: String? = null,
    public val internalDetail: String? = null,
    public override val cause: Throwable? = null,
    public val severity: LogLevel? = null,
    logFields: Collection<LogField> = emptyList(),
) : ExceptionWithLoggingContext(logFields) {
  /**
   * The exception message. Includes [publicMessage], [publicDetail] and [internalDetail], on the
   * following format:
   * ```text
   * Public message - Public detail (Internal detail)
   * ```
   *
   * This message should _not_ be exposed to clients, since it may include [internalDetail].
   * Instead, you should do one of the following:
   * - Use [publicMessage]/[publicDetail] directly
   * - Use the `PublicException.toErrorResponse` extension function from `liflig-http4k-setup`
   * - Let the exception propagate and be caught by the `PublicExceptionFilter` from
   *   `liflig-http4k-setup` (included in the default filter stack), which will map it to an
   *   appropriate HTTP response
   */
  public override val message: String
    get() {
      // If we only have publicMessage, we can just return it as-is, saving an allocation
      if (publicDetail == null && internalDetail == null) {
        return publicMessage
      }

      // Pre-calculate capacity, so that StringBuilder only has to allocate once
      val capacity =
          publicMessage.length +
              (if (publicDetail == null) 0 else publicDetail.length + 3) + // +3 for " - "
              (if (internalDetail == null) 0 else internalDetail.length + 3) // +3 for " ()"

      val message = StringBuilder(capacity)
      message.append(publicMessage)
      if (publicDetail != null) {
        message.append(' ')
        message.append('-')
        message.append(' ')
        message.append(publicDetail)
      }
      if (internalDetail != null) {
        message.append(' ')
        message.append('(')
        message.append(internalDetail)
        message.append(')')
      }
      return message.toString()
    }

  // Public companion object, so that liflig-http4k-setup can add extension functions to it
  public companion object
}

/**
 * The status code to use when a [PublicException] is mapped to an HTTP response (e.g. when caught
 * by the `PublicExceptionFilter` from `liflig-http4k-setup`).
 *
 * We use our own class here instead of the `Status` class from http4k, as we don't want to depend
 * on http4k-specific things in [PublicException] (which can be adapted to other
 * frameworks/protocols).
 *
 * Error codes are implemented as static instances on the companion object, instead of as an enum.
 * This is because we don't want to bind ourselves to the public API that an enum exposes
 * ([Enum.name] and [Enum.ordinal]), and also allow ourselves to add more variants in the future.
 */
public class ErrorCode private constructor(@JvmField public val httpStatusCode: Int) {
  /** See [ErrorCode]. */
  public companion object {
    /** Maps to a 400 Bad Request HTTP status. */
    @JvmField public val BAD_REQUEST: ErrorCode = ErrorCode(400)

    /** Maps to a 401 Unauthorized HTTP status. */
    @JvmField public val UNAUTHORIZED: ErrorCode = ErrorCode(401)

    /** Maps to a 403 Forbidden HTTP status. */
    @JvmField public val FORBIDDEN: ErrorCode = ErrorCode(403)

    /** Maps to a 404 Not Found HTTP status. */
    @JvmField public val NOT_FOUND: ErrorCode = ErrorCode(404)

    /** Maps to a 409 Conflict HTTP status. */
    @JvmField public val CONFLICT: ErrorCode = ErrorCode(409)

    /**
     * Maps to a 500 Internal Server Error HTTP status.
     *
     * It may seem counter-intuitive to have an Internal Server Error on a [PublicException], but
     * sometimes we do want to map an exception to an Internal Server Error and still provide a
     * descriptive error message to the user.
     */
    @JvmField public val INTERNAL_SERVER_ERROR: ErrorCode = ErrorCode(500)

    /** When adding a new error code, remember to add it to this list! */
    @JvmField
    internal val entries =
        listOf(
            BAD_REQUEST,
            UNAUTHORIZED,
            FORBIDDEN,
            NOT_FOUND,
            CONFLICT,
            INTERNAL_SERVER_ERROR,
        )

    /** Returns `null` if no [ErrorCode] entry was found for the given HTTP status code. */
    @JvmStatic
    public fun fromHttpStatusCode(statusCode: Int): ErrorCode? {
      return entries.find { it.httpStatusCode == statusCode }
    }
  }

  override fun toString(): String {
    return when (this) {
      BAD_REQUEST -> "Bad Request"
      UNAUTHORIZED -> "Unauthorized"
      FORBIDDEN -> "Forbidden"
      NOT_FOUND -> "Not Found"
      CONFLICT -> "Conflict"
      INTERNAL_SERVER_ERROR -> "Internal Server Error"
      // Since we don't use an enum (see ErrorCode docstring for why), we need an else branch here.
      // We verify in our tests that this case is never reached.
      else -> "Unknown ErrorCode"
    }
  }
}
