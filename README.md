# liflig-public-exception

Kotlin library that provides a `PublicException` type: an exception with a message that is meant to
be exposed to users.

When using this with [`liflig-http4k-setup`](https://github.com/capralifecycle/liflig-http4k-setup),
any `PublicException` thrown in the context of an HTTP handler will be caught by a
`PublicExceptionFilter`, and mapped to an HTTP response.

**Contents:**

- [Motivation](#motivation)
- [Adding to your project](#adding-to-your-project)
- [Maintainer's guide](#maintainers-guide)

## Motivation

We distinguish between _handling_ and _reporting_ errors on servers, defining them as follows:

- Error _handling_ is when your application encounters an error that it knows about and can recover
  from there-and-then
- Error _reporting_ is when your application encounters an error that it cannot solve by itself. It
  must then report it, both to:
  - The user, by returning a response signaling that something failed
  - The developer, by logging the error, ideally with as much context as possible for debugging

This library aims to improve error _reporting_.

The base case for error reporting of exceptions in Kotlin/Java HTTP servers typically looks like
this:

- A 500 Internal Server Error is returned to the user, with no details about the specific exception
- The exception is logged, with a stack trace

This is a pretty good base case:

- The user is alerted that something failed, but no potentially sensitive information is exposed
- The developer gets valuable debugging context from the stack trace

When trying to improve error reporting, we want to _build_ on this base case, without taking away
from it.

One thing that you may want to do to improve on this base case, is to return a more specific
response to the user (perhaps a different HTTP status code, and a response body describing more
details about the error, so the user may solve it themselves). This is easy enough to do when the
error occurs in your HTTP handler. But often, errors occur further down the call stack, and then
we must deal with how to propagate the error up to the HTTP handler, with enough context to return
our intended response to the user.

This is where Kotlin developers may reach for something like
[Arrow](https://github.com/arrow-kt/arrow). Using Arrow, we'd change our functions to return an
`Either<Error, Success>` type, and then we'd check the `Error` variant in our HTTP handler, mapping
it to an appropriate error response. This is fine on paper, but in practice, using Arrow may take
away from the other aspect of error reporting: including as much context as possible when logging
the error for the developer. This is because:

- You'll typically define custom error types when using Arrow. If the error originally was caused
  by an exception, you must remember to attach the exception to your error type, and propagate
  that up to your HTTP handler - and then, you must remember to log that exception, so that its
  stack trace is included.
  - In practice, it's easy to forget to attach exceptions to your custom error types. Then, when
    an error occurs in production, we're left without a stack trace of the underlying error, losing
    valuable context.
- If there is more than 1 level between the original error and your HTTP handler, then you must
  wrap underlying error variants as you propagate errors up the stack.
  - Let's say that our HTTP handler calls `Service1`, which again calls `Service2`. Both services
    define sealed classes with error variants they may return, `Service1Error` and `Service2Error`.
    If we encounter an error in `Service2`, which we want to propagate up to our HTTP handler, then
    `Service1Error` must attach the `Service2Error`, or else we lose context about the root cause.

These issues may sound banal, but we have seen them happen again and again when using Arrow.
Fundamentally, this comes down to using the wrong tool for the job: Arrow is a great tool for
explicit error _handling_, but is not ideal for error _reporting_. Exceptions are quite good for
error reporting, since they automatically propagate up the stack, and keep their original context
(the stack trace). And they have a built-in way of attaching more context, by wrapping an exception
in a new exception and setting the original exception as the `cause`. When using Arrow, we have to
recreate these mechanisms, by manually propagating errors up the stack, and attaching context about
the original error as fields on our custom error types. But when doing this manually, it's easy to
lose context on the way.

So: we want to use exceptions for error reporting, so that we don't lose context when logging the
error for the developer. But then how do we return a more specific error response to the user?
That's where `PublicException` fits in: it gives you the benefits of exceptions (stack
trace, automatic propagation up the stack), but lets you set a status code and user-facing message,
to give a more useful error response to the user. When using
[`liflig-http4k-setup`](https://github.com/capralifecycle/liflig-http4k-setup), the default server
middleware will catch `PublicException`s, automatically mapping them to appropriate HTTP responses.

`PublicException` is a pragmatic solution to serve both aspects of error reporting: keeping as much
context as possible for the developer, while also allowing you to give a more useful response to the
user. Note that it is not the appropriate tool for error _handling_ - if a part of your application
deals with well-known, recoverable errors, you may want to use something like Arrow instead for
that. But when all you want to do is to give the user more context about an error error, without the
risk of losing internal context for the developer, then `PublicException` may be the right tool.

## Adding to your project

**Maven:** We recommend adding `liflig-public-exception` to the `dependencyManagement` section, so
that the same version is used across Liflig libraries. It's good practice to pair this with the
[Maven Enforcer Plugin](https://maven.apache.org/enforcer/maven-enforcer-plugin/), with the
[
`<dependencyConvergence/>`](https://maven.apache.org/enforcer/enforcer-rules/dependencyConvergence.html)
and
[
`<requireUpperBoundDeps/>`](https://maven.apache.org/enforcer/enforcer-rules/requireUpperBoundDeps.html)
rules.

<!-- @formatter:off -->
```xml
<dependencyManagement>
  <dependency>
    <groupId>no.liflig</groupId>
    <artifactId>liflig-public-exception</artifactId>
    <version>${liflig-public-exception.version}</version>
  </dependency>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>no.liflig</groupId>
    <artifactId>liflig-public-exception</artifactId>
  </dependency>
</dependencies>
```
<!-- @formatter:on -->

## Maintainer's guide

### Build & Test

```sh
mvn clean install
```

### Lint code

```sh
mvn spotless:check
```

### Format code

```sh
mvn spotless:apply
```
