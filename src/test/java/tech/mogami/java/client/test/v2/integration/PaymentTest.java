package tech.mogami.java.client.test.v2.integration;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.payment.PaymentPayload;
import tech.mogami.commons.payment.PaymentRequired;
import tech.mogami.java.client.X402V2Client;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_PAYMENT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.mogami.commons.constant.network.Networks.BASE_SEPOLIA;
import static tech.mogami.commons.test.BaseMogamiTestData.TEST_CLIENT_WALLET_ADDRESS_1;
import static tech.mogami.commons.test.BaseMogamiTestData.TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY;

@DisplayName("Payment integration tests")
public class PaymentTest {

    /** OkHttpClient instance for making HTTP requests */
    private static final OkHttpClient CLIENT = new OkHttpClient();

    @Test
    @DisplayName("Payment on https://www.x402.org/protected")
    void paymentOnX402OrgProtected() {
        final String url = "https://www.x402.org/protected";
        PaymentRequired paymentRequired = null;

        // We make the initial request without any payment =============================================================
        try (Response initialResponse = CLIENT.newCall(new Request.Builder().url(url).get().build()).execute()) {

            // Extracting the payments requirements from the header
            assertEquals(SC_PAYMENT_REQUIRED, initialResponse.code());
            paymentRequired = X402V2Client.extractPaymentRequired(getHeaders(initialResponse))
                    .orElseThrow(() -> new IllegalStateException("PaymentRequired should be present"));

        } catch (IOException e) {
            fail("IOException during HTTP request to " + url + ": " + e.getMessage());
        }

        // We create a payment with a valid PaymentPayload =============================================================
        assertThat(paymentRequired).isNotNull();
        assertThat(paymentRequired.accepts()).isNotEmpty();
        PaymentPayload payload = X402V2Client.buildPaymentPayload(
                paymentRequired,
                paymentRequired.accepts().getFirst(),
                Credentials.create(TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY)
        );

        // We call the protected resource with the payment =============================================================
        try (Response paidResponse = CLIENT.newCall(new Request.Builder().url(url).get()
                .headers(Headers.of(X402V2Client.buildPaymentHeaders(payload)))
                .build()).execute()) {
            assertThat(paidResponse.body()).isNotNull();
            var content = paidResponse.body().string();
            System.out.println("=> Content: " + content);

            X402V2Client.extractSettlementResponse(getHeaders(paidResponse)).ifPresentOrElse(
                    settlementResponse -> {
                        System.out.println("✅ Settlement response received: " + settlementResponse);
                        assertThat(settlementResponse)
                                .satisfies(response -> {
                                    assertThat(response.success()).isTrue();
                                    assertThat(response.errorReason()).isBlank();
                                    assertThat(response.payer()).isEqualToIgnoringCase(TEST_CLIENT_WALLET_ADDRESS_1);
                                    assertThat(response.transaction()).isNotBlank();
                                    assertThat(response.network()).isEqualTo(BASE_SEPOLIA.networkId());
                                });
                    },
                    () -> fail("No settlement response found in the paid request headers.")
            );

            assertThat(paidResponse).isNotNull();
            assertThat(paidResponse.isSuccessful()).isTrue();
            assertThat(content).contains("Your payment was successful!");
            System.out.println("✅ Payment accepted");

        } catch (IOException e) {
            fail("IOException during HTTP request to " + url + ": " + e.getMessage());
        }
    }

    private Map<String, String> getHeaders(Response response) {
        return response.headers().toMultimap()
                .entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getFirst()));
    }

}
