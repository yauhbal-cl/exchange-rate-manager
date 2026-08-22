package com.exchangerate.manager.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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

        FixerClient fixerClient = new FixerClient(builder, API_KEY, BASE_URL);

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

        FixerClient fixerClient = new FixerClient(builder, API_KEY, BASE_URL);

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

        FixerClient fixerClient = new FixerClient(builder, API_KEY, BASE_URL);

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

        FixerClient fixerClient = new FixerClient(builder, API_KEY, BASE_URL);

        assertThatThrownBy(fixerClient::getLatestRates)
                .isInstanceOf(FixerApiException.class)
                .hasMessageContaining("invalid_access_key");

        server.verify();
    }
}
