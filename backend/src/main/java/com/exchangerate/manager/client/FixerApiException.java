package com.exchangerate.manager.client;

/**
 * Unchecked exception thrown by the Fixer.io HTTP client when a call to the
 * {@code /latest} endpoint fails.
 *
 * <p>Covers three failure modes:
 * <ol>
 *   <li>A network-level failure (connection refused, timeout, DNS failure, etc.) — use
 *       {@link #FixerApiException(String, Throwable)} to chain the underlying cause.</li>
 *   <li>A non-2xx HTTP response from the provider — use {@link #FixerApiException(String)}
 *       with a message describing the HTTP status received.</li>
 *   <li>A 2xx response whose JSON body reports {@code "success": false}, i.e. Fixer.io's own
 *       documented error envelope ({@code {success:false, error:{code, type, info}}}) — use
 *       {@link #FixerApiException(String)} with a message describing the provider's error
 *       code/type/info.</li>
 * </ol>
 *
 * <p>Extends {@link RuntimeException} (unchecked) since it is thrown from a scheduled job /
 * service layer that already handles it via its own try/catch, and should not force checked
 * exception handling on every caller.
 */
public class FixerApiException extends RuntimeException {

    /**
     * Constructs an exception for the non-2xx HTTP response case or the
     * {@code "success": false} error-envelope case. The message should be descriptive enough to
     * log usefully on its own, e.g. include the HTTP status, or the provider's error
     * code/type/info.
     *
     * @param message a descriptive message identifying the failure
     */
    public FixerApiException(String message) {
        super(message);
    }

    /**
     * Constructs an exception for the network-level failure case, chaining the underlying cause
     * (e.g. connection refused, timeout).
     *
     * @param message a descriptive message identifying the failure
     * @param cause   the underlying network-level cause
     */
    public FixerApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
