package tech.mogami.java.client.test.integration;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.mogami.commons.constant.BodyType;
import tech.mogami.commons.constant.HttpMethod;
import tech.mogami.commons.payment.PaymentRequired;
import tech.mogami.java.client.X402V2Client;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_PAYMENT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Bazaar extension integration tests")
public class BazaarTest {

    /** OkHttpClient instance for making HTTP requests */
    private static final OkHttpClient CLIENT = new OkHttpClient();

    @Test
    @DisplayName("Extract BazaarExtension from https://www.cookies4humans.com/api/send-cookie")
    void extractBazaarExtensionFromCookies4Humans() {
        final String url = "https://www.cookies4humans.com/api/send-cookie";
        PaymentRequired paymentRequired = null;

        // We make the initial request without any payment =============================================================
        try (Response response = CLIENT.newCall(new Request.Builder().url(url).get().build()).execute()) {
            assertEquals(SC_PAYMENT_REQUIRED, response.code());
            paymentRequired = X402V2Client.extractPaymentRequired(getHeaders(response))
                    .orElseThrow(() -> new IllegalStateException("PaymentRequired should be present"));
        } catch (IOException e) {
            fail("IOException during HTTP request to " + url + ": " + e.getMessage());
        }

        // We extract and validate the BazaarExtension =================================================================
        assertThat(paymentRequired).isNotNull();
        X402V2Client.extractBazaarExtension(paymentRequired).ifPresentOrElse(
                bazaarExtension -> {
                    System.out.println("✅ BazaarExtension received: " + bazaarExtension);
                    assertThat(bazaarExtension.info())
                            .isNotNull()
                            .satisfies(info -> {
                                assertThat(info.input())
                                        .isNotNull()
                                        .satisfies(input -> {
                                            assertThat(input.type()).isEqualTo("http");
                                            assertThat(input.method()).isEqualTo(HttpMethod.POST);
                                            assertThat(input.bodyType()).isEqualTo(BodyType.JSON);
                                            assertThat(input.body()).isNotNull();
                                        });
                                assertThat(info.output())
                                        .isNotNull()
                                        .satisfies(output -> assertThat(output.type()).isEqualTo("json"));
                            });
                    assertThat(bazaarExtension.schema()).isNotNull();
                },
                () -> fail("No BazaarExtension found in the PaymentRequired extensions.")
        );
    }

    private Map<String, String> getHeaders(Response response) {
        return response.headers().toMultimap()
                .entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getFirst()));
    }

}
