package com.exchangerate.manager.client;

import com.exchangerate.manager.config.FixerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Plain unit test for {@link FixerClient} against a {@link MockRestServiceServer} — no Spring
 * context is loaded, so this runs fast and exercises only the HTTP call/deserialization logic.
 */
class FixerClientTest {

    private static final String BASE_URL = "http://fixer.test";
    private static final String API_KEY = "test-api-key";

    @Test
    void getLatestRatesReturnsDeserializedResponseAndSendsNoSymbolsParam() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String responseBody = """
                {"success":true,"base":"EUR","date":"2026-08-22","rates":{"USD":1.08,"GBP":0.86,"EUR":1.0}}
                """;

        server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        FixerClient fixerClient = client(builder, 1);

        FixerLatestResponse response = fixerClient.getLatestRates();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getBase()).isEqualTo("EUR");
        assertThat(response.getDate()).isEqualTo(LocalDate.of(2026, 8, 22));
        assertThat(response.getRates()).containsOnlyKeys("USD", "GBP", "EUR");
        assertThat(response.getRates().get("USD")).isEqualByComparingTo(new BigDecimal("1.08"));
        assertThat(response.getRates().get("GBP")).isEqualByComparingTo(new BigDecimal("0.86"));
        assertThat(response.getRates().get("EUR")).isEqualByComparingTo(new BigDecimal("1.0"));

        server.verify();
    }

    @Test
    void getLatestRatesThrowsOnNon2xxResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        FixerClient fixerClient = client(builder, 1);

        assertThatThrownBy(fixerClient::getLatestRates)
                .isInstanceOf(FixerApiException.class);

        server.verify();
    }

    @Test
    void getLatestRatesThrowsOnNetworkFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException("connection refused");
                });

        FixerClient fixerClient = client(builder, 1);

        assertThatThrownBy(fixerClient::getLatestRates)
                .isInstanceOf(FixerApiException.class)
                .hasCauseInstanceOf(Exception.class);

        server.verify();
    }

    @Test
    void getLatestRatesThrowsOnProviderErrorEnvelope() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String responseBody = """
                {"success":false,"error":{"code":101,"type":"invalid_access_key","info":"You have not supplied a valid API Access Key."}}
                """;

        server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        FixerClient fixerClient = client(builder, 3);

        assertThatThrownBy(fixerClient::getLatestRates)
                .isInstanceOf(FixerApiException.class)
                .hasMessageContaining("invalid_access_key");

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 503})
    void retriesTransientHttpStatusesAndCanRecover(int status) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String success = """
                {"success":true,"base":"EUR","date":"2026-08-22","rates":{"USD":1.08}}
                """;

        server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andRespond(withSuccess(success, MediaType.APPLICATION_JSON));

        assertThat(client(builder, 3).getLatestRates().isSuccess()).isTrue();
        server.verify();
    }

    @Test
    void retriesTransportFailureAndCanRecover() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String success = """
                {"success":true,"base":"EUR","date":"2026-08-22","rates":{"USD":1.08}}
                """;

        server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andRespond(request -> { throw new IOException("connection reset"); });
        server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andRespond(withSuccess(success, MediaType.APPLICATION_JSON));

        assertThat(client(builder, 3).getLatestRates().isSuccess()).isTrue();
        server.verify();
    }

    @Test
    void stopsAfterConfiguredTotalAttemptsAndPreservesLastCause() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                    .andRespond(withServerError());
        }

        assertThatThrownBy(() -> client(builder, 3).getLatestRates())
                .isInstanceOf(FixerApiException.class)
                .hasMessageContaining("500")
                .hasCauseInstanceOf(RestClientResponseException.class);
        server.verify();
    }

    @Test
    void doesNotRetryOrdinaryClientError() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client(builder, 3).getLatestRates())
                .isInstanceOf(FixerApiException.class)
                .hasMessageContaining("400");
        server.verify();
    }

    @Test
    void doesNotRetryMalformedOrEmptySuccessfulResponse() {
        RestClient.Builder malformedBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedBuilder).build();
        malformedServer.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(malformedBuilder, 3).getLatestRates())
                .isInstanceOf(FixerApiException.class);
        malformedServer.verify();

        RestClient.Builder emptyBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build();
        emptyServer.expect(requestTo(BASE_URL + "/latest?access_key=" + API_KEY))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> client(emptyBuilder, 3).getLatestRates())
                .isInstanceOf(FixerApiException.class)
                .hasMessageContaining("empty response body");
        emptyServer.verify();
    }

    private static FixerClient client(RestClient.Builder builder, int maxAttempts) {
        FixerProperties.Retry retry = new FixerProperties.Retry(
                maxAttempts, Duration.ZERO, 1.0, Duration.ofMillis(1));
        return new FixerClient(builder.baseUrl(BASE_URL).build(), retry, API_KEY);
    }
}
