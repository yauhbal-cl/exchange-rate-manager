package com.exchangerate.manager.client;

import com.exchangerate.manager.config.FixerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

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

    private final RetryTemplate retryTemplate;

    @Autowired
    public FixerClient(FixerProperties properties) {
        this(createRestClient(properties), createRetryTemplate(properties.http().retry()),
                properties.apiKey());
    }

    FixerClient(RestClient restClient, FixerProperties.Retry retryProperties, String apiKey) {
        this(restClient, createRetryTemplate(retryProperties), apiKey);
    }

    private FixerClient(RestClient restClient, RetryTemplate retryTemplate, String apiKey) {
        this.restClient = restClient;
        this.retryTemplate = retryTemplate;
        this.apiKey = apiKey;
    }

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
            response = retryTemplate.invoke(this::requestLatestRates);
        } catch (RestClientResponseException e) {
            throw httpFailure(e);
        } catch (RestClientException e) {
            throw transportFailure(e);
        }

        if (response == null) {
            throw new FixerApiException("Fixer.io /latest call returned an empty response body");
        }

        if (!response.isSuccess()) {
            throw new FixerApiException(buildProviderErrorMessage(response.getError()));
        }

        return response;
    }

    private FixerLatestResponse requestLatestRates() {
        return restClient.get()
                .uri("/latest?access_key={accessKey}", apiKey)
                .retrieve()
                .body(FixerLatestResponse.class);
    }

    private FixerApiException httpFailure(RestClientResponseException exception) {
        return new FixerApiException(
                "Fixer.io /latest call failed with HTTP status "
                        + exception.getStatusCode().value(), exception);
    }

    private FixerApiException transportFailure(RestClientException exception) {
        return new FixerApiException("Fixer.io /latest call failed: " + exception.getMessage(),
                exception);
    }

    private static RestClient createRestClient(FixerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.http().connectTimeout());
        requestFactory.setReadTimeout(properties.http().readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private static RetryTemplate createRetryTemplate(FixerProperties.Retry properties) {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(properties.maxAttempts() - 1L)
                .delay(properties.initialDelay())
                .multiplier(properties.multiplier())
                .maxDelay(properties.maxDelay())
                .predicate(FixerClient::isRetryable)
                .build();
        return new RetryTemplate(retryPolicy);
    }

    private static boolean isRetryable(Throwable failure) {
        if (failure instanceof ResourceAccessException) {
            return true;
        }
        if (failure instanceof RestClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            return status.value() == 408 || status.value() == 429 || status.is5xxServerError();
        }
        return false;
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
