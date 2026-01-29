package com.marvel.hospitality.reservationservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock(@ConfigureWireMock(name = "credit-card-payment-server", port= 9090, registerSpringBean = true))
@ActiveProfiles("test")
class CreditCardClientIntegrationTest {

    @Autowired
    private CreditCardClient creditCardClient;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private RetryRegistry retryRegistry;

    @Qualifier("credit-card-payment-server")
    @Autowired
    private WireMockServer wireMockServer;

    private CircuitBreaker circuitBreaker;
    private Retry retry;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("creditCard");
        retry = retryRegistry.retry("creditCard");
        circuitBreaker.reset();
        wireMockServer.resetAll();
    }

    @Test
    void testRetry_OnInternalServerError() {
        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(500))
                .inScenario("RetrySuccess")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("Attempt2"));

        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(500))
                .inScenario("RetrySuccess")
                .whenScenarioStateIs("Attempt2")
                .willSetStateTo("Attempt3"));

        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"CONFIRMED\"}"))
                .inScenario("RetrySuccess")
                .whenScenarioStateIs("Attempt3"));

        creditCardClient.verifyPayment("REF-123");

        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
        verify(3, postRequestedFor(urlPathMatching("/credit-card-payment-api/.*")));
    }

    @Test
    void testNoRetry_OnBadRequest() {
        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(400)));
        assertThatThrownBy(() -> creditCardClient.verifyPayment("REF-BAD"))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
        verify(1, postRequestedFor(urlPathMatching("/credit-card-payment-api/.*")));
    }

    @Test
    void testCircuitBreaker_StateTransition_ToOpen() {
        // Scénario: Atteindre le threshold (50% de 5 appels minimum)
        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 5; i++) {
            try { creditCardClient.verifyPayment("REF-FAIL"); } catch (Exception ignored) {}
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> creditCardClient.verifyPayment("REF-NEW"))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void testCircuitBreaker_HalfOpen_ToClosed() {
        circuitBreaker.transitionToOpenState();

        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"CONFIRMED\"}")));

        for (int i = 0; i < 3; i++) {
            creditCardClient.verifyPayment("REF-RECOVERY-" + i);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}