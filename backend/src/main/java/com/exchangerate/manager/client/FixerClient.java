package com.exchangerate.manager.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP client for Fixer.io's {@code /latest} endpoint.
 *
 * <p>Fetches the full provider-supported set of latest exchange rates (no {@code symbols} query
 * param, per FR-005), deserializes the response, and translates any failure — network-level,
 * non-2xx HTTP status, or a 2xx body with {@code success: false} — into a {@link FixerApiException}.
 *
 * <p>Does not log; callers are responsible for logging failures.
 */
@Component
public class FixerClient {

    private final RestClient restClient;

    private final String apiKey;

    public FixerClient(RestClient.Builder restClientBuilder,
                        @Value("${fixer.api-key}") String apiKey,
                        @Value("${fixer.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    // Note: base-url is consumed only to build restClient above and isn't retained as a field,
    // so @RequiredArgsConstructor can't derive this constructor — kept explicit.

    /**
     * Calls Fixer.io's {@code GET /latest} endpoint and returns the deserialized response.
     *
     * @return the deserialized {@link FixerLatestResponse}
     * @throws FixerApiException if the call fails at the network level, returns a non-2xx HTTP
     *                            status, or returns a 2xx body reporting {@code success: false}
     */
    public FixerLatestResponse getLatestRates() {
        FixerLatestResponse response;
        try {
            response = restClient.get()
                    .uri("/latest?access_key={accessKey}", apiKey)
                    .retrieve()
                    .body(FixerLatestResponse.class);
        } catch (RestClientResponseException e) {
            throw new FixerApiException(
                    "Fixer.io /latest call failed with HTTP status " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new FixerApiException("Fixer.io /latest call failed: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new FixerApiException("Fixer.io /latest call returned an empty response body");
        }

        if (!response.isSuccess()) {
            throw new FixerApiException(buildProviderErrorMessage(response.getError()));
        }

        return response;
    }

    private String buildProviderErrorMessage(FixerLatestResponse.FixerError error) {
        StringBuilder message = new StringBuilder("Fixer.io /latest call reported failure");
        if (error == null) {
            return message.append(" (no error details provided)").toString();
        }
        message.append(" [code=").append(error.getCode()).append(']');
        if (error.getType() != null) {
            message.append(" [type=").append(error.getType()).append(']');
        }
        if (error.getInfo() != null) {
            message.append(" [info=").append(error.getInfo()).append(']');
        }
        return message.toString();
    }
}
