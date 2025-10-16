package no.liflig.publicexception

import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.lang.reflect.Modifier
import kotlin.reflect.KVisibility
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.memberProperties
import no.liflig.logging.LogLevel
import no.liflig.logging.field
import org.junit.jupiter.api.Test

internal class PublicExceptionTest {
  @Test
  fun `PublicException constructor works as expected`() {
    val exception =
        PublicException(
            ErrorCode.INTERNAL_SERVER_ERROR,
            publicMessage = "Invalid request body",
            publicDetail = "Missing required field 'id'",
            internalDetail = "Internal detail",
            cause = Exception("Cause exception"),
            severity = LogLevel.WARN,
            logFields = listOf(field("requestBody", "value1"), field("key2", "value2")),
        )

    exception.errorCode.shouldBe(ErrorCode.INTERNAL_SERVER_ERROR)
    exception.publicMessage.shouldBe("Invalid request body")
    exception.publicDetail.shouldBe("Missing required field 'id'")
    exception.internalDetail.shouldBe("Internal detail")
    exception.message.shouldBe(
        "Invalid request body - Missing required field 'id' (Internal detail)",
    )
    exception.cause?.message.shouldBe("Cause exception")
    exception.severity.shouldBe(LogLevel.WARN)
  }

  @Test
  fun `PublicException with only required parameters`() {
    val exception = PublicException(ErrorCode.FORBIDDEN, publicMessage = "Insufficient permissions")

    exception.errorCode.shouldBe(ErrorCode.FORBIDDEN)
    exception.publicMessage.shouldBe("Insufficient permissions")
    exception.publicDetail.shouldBeNull()
    exception.internalDetail.shouldBeNull()
    exception.message.shouldBe("Insufficient permissions")
    exception.cause.shouldBeNull()
    exception.severity.shouldBeNull()
  }

  @Test
  fun `PublicException message with only public or internal detail`() {
    // We test PublicException with both public detail and internal detail above, and message with
    // only public message. So to cover all cases, we want to test the exception message with public
    // message and public detail but no internal detail, and vice versa.
    val exception1 =
        PublicException(
            ErrorCode.BAD_REQUEST,
            publicMessage = "Public message",
            publicDetail = "Public detail",
        )
    exception1.message.shouldBe("Public message - Public detail")

    val exception2 =
        PublicException(
            ErrorCode.BAD_REQUEST,
            publicMessage = "Public message",
            internalDetail = "Internal detail",
        )
    exception2.message.shouldBe("Public message (Internal detail)")
  }

  @Test
  fun `ErrorCode has expected members, HTTP status codes and toString representations`() {
    data class ErrorCodeTest(
        val errorCode: ErrorCode,
        val expectedHttpStatusCode: Int,
        val expectedToString: String,
    )

    // When adding a new ErrorCode entry, add a test for it here
    val tests =
        listOf(
            ErrorCodeTest(
                ErrorCode.BAD_REQUEST,
                expectedHttpStatusCode = 400,
                expectedToString = "Bad Request",
            ),
            ErrorCodeTest(
                ErrorCode.UNAUTHORIZED,
                expectedHttpStatusCode = 401,
                expectedToString = "Unauthorized",
            ),
            ErrorCodeTest(
                ErrorCode.FORBIDDEN,
                expectedHttpStatusCode = 403,
                expectedToString = "Forbidden",
            ),
            ErrorCodeTest(
                ErrorCode.NOT_FOUND,
                expectedHttpStatusCode = 404,
                expectedToString = "Not Found",
            ),
            ErrorCodeTest(
                ErrorCode.CONFLICT,
                expectedHttpStatusCode = 409,
                expectedToString = "Conflict",
            ),
            ErrorCodeTest(
                ErrorCode.INTERNAL_SERVER_ERROR,
                expectedHttpStatusCode = 500,
                expectedToString = "Internal Server Error",
            ),
        )

    // Use reflection to get all ErrorCode members, so we can verify that our tests cover them all
    val expectedErrorCodes =
        ErrorCode.Companion::class
            .memberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.get(ErrorCode.Companion) }

    // Verify that every ErrorCode has @JvmField annotation, so they're compiled as static fields
    val staticFields =
        ErrorCode::class
            .java
            .fields
            .filter { field -> Modifier.isStatic(field.modifiers) }
            .mapNotNull { field -> field.get(null) as? ErrorCode }
    expectedErrorCodes.shouldContainExactlyInAnyOrder(staticFields)

    tests.map { it.errorCode }.shouldContainExactlyInAnyOrder(expectedErrorCodes)

    // Verify that `ErrorCode.entries` contains all ErrorCode members
    ErrorCode.entries.shouldContainExactlyInAnyOrder(expectedErrorCodes)

    // Finally, verify that each ErrorCode member has expected HTTP status code
    tests.forEach { test ->
      test.asClue {
        test.errorCode.httpStatusCode.shouldBe(test.expectedHttpStatusCode)

        ErrorCode.fromHttpStatusCode(test.expectedHttpStatusCode)
            .shouldBeSameInstanceAs(test.errorCode)
      }
    }
  }

  @Test
  fun `PublicException has public companion object`() {
    PublicException::class.companionObject.shouldNotBeNull().visibility.shouldBe(KVisibility.PUBLIC)
  }
}
