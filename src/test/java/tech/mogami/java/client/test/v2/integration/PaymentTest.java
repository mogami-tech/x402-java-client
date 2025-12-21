package tech.mogami.java.client.test.v2.integration;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.payment.PaymentPayload;
import tech.mogami.commons.payment.PaymentRequirements;
import tech.mogami.java.client.X402V2Client;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_SIGNATURE_HEADER;
import static tech.mogami.commons.test.BaseTestData.TEST_CLIENT_WALLET_ADDRESS_1;
import static tech.mogami.commons.test.BaseTestData.TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY;

@DisplayName("X402Client payment integration tests")
public class PaymentTest {

    /** OkHttpClient instance for making HTTP requests */
    private static final OkHttpClient CLIENT = new OkHttpClient();

    @Test
    @DisplayName("Payment on https://www.x402.org/protected")
    void paymentOnX402OrgProtected() {
        final String url = "https://www.x402.org/protected";

        // Calling the URL.
        Request initialRequest = new Request.Builder()
                .url(url)
                .get()
                .build();
        try (Response initialResponse = CLIENT.newCall(initialRequest).execute()) {
            // Check if the response indicates a payment is required (e.g., HTTP 402)
            if (initialResponse.code() == 402) {

                // We make a payment ===================================================================================

                // Extracting the payments requirements from the header.
                Map<String, String> headers = initialResponse.headers().toMultimap()
                        .entrySet().stream()
                        .filter(e -> !e.getValue().isEmpty())
                        .collect(toMap(Map.Entry::getKey, e -> e.getValue().getFirst()));
                List<PaymentRequirements> requirements = X402V2Client.fetchPaymentRequirements(headers);
                if (requirements.isEmpty()) {
                    fail("No payment requirements found in the response headers.");
                }

                // Payload creation.
                PaymentPayload payload = X402V2Client.createPaymentPayload(requirements.getFirst(), TEST_CLIENT_WALLET_ADDRESS_1);

                // Signing the payload.
                var signedPayload = X402V2Client.signPaymentPayload(
                        requirements.getFirst(),
                        payload,
                        Credentials.create(TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY)
                );

                // Building the payment headers.
                Map<String, String> paymentHeaders = X402V2Client.buildPaymentHeaders(signedPayload);

                // We make the paid request ============================================================================
                Request paidRequest = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader(
                                X402_PAYMENT_SIGNATURE_HEADER,
                                paymentHeaders.get(X402_PAYMENT_SIGNATURE_HEADER)
                        )
                        .build();
                try (Response paidResponse = CLIENT.newCall(paidRequest).execute()) {

                    // Checking the response header.
                    // TODO Fix when https://github.com/coinbase/x402/issues/836 will be resolved
//                    headers = paidResponse.headers().toMultimap()
//                            .entrySet().stream()
//                            .filter(e -> !e.getValue().isEmpty())
//                            .peek(e -> System.out.println("Header: " + e.getKey() + " = " + e.getValue().getFirst()))
//                            .collect(toMap(Map.Entry::getKey, e -> e.getValue().getFirst()));
//                    X402V2Client.fetchSettlementResponse(headers).ifPresentOrElse(
//                            settlementResponse -> System.out.println("✅ Settlement response received: " + settlementResponse),
//                            () -> fail("No settlement response found in the paid request headers.")
//                    );

                    // Checking the response body.
                    System.out.println("Server response: " + paidResponse);
                    assertThat(paidResponse).isNotNull();
                    assertThat(paidResponse.isSuccessful()).isTrue();
                    assertThat(paidResponse.body()).isNotNull();
                    assertThat(paidResponse.body().string()).contains("Your payment was successful!");
                    System.out.println("✅ Payment accepted");
                }

            } else {
                fail("Expected HTTP 402 Payment Required, but got: " + initialResponse.code());
            }
        } catch (IOException e) {
            fail("IOException during HTTP request to " + url + ": " + e.getMessage());
        }

    }

}
