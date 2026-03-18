package tech.mogami.java.client.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.mogami.commons.payment.PaymentRequired;
import tech.mogami.java.client.X402V2Client;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_PAYMENT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.mogami.commons.constant.BodyType.JSON;
import static tech.mogami.commons.constant.HttpMethod.POST;

@DisplayName("Bazaar extension integration tests")
public class BazaarTest {

    /** OkHttpClient instance for making HTTP requests */
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Extract BazaarExtension from cookies4humans")
    void extractBazaarExtensionFromCookies4Humans() {
        final String url = "https://www.cookies4humans.com/api/send-cookie";
        PaymentRequired paymentRequired = null;

        // We make the initial request without any payment =============================================================
        // Equivalent to curl https://www.cookies4humans.com/api/send-cookie -v -X POST -H "Content-Type: application/json" -d '{}'
        RequestBody emptyJsonBody = RequestBody.create("{}", MediaType.parse("application/json"));
        try (Response response = CLIENT.newCall(new Request.Builder().url(url).post(emptyJsonBody).build()).execute()) {
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
                                            assertThat(input.method()).isEqualTo(POST);
                                            assertThat(input.bodyType()).isEqualTo(JSON);
                                            // BazaarInput.body is declared as JsonNode in mogami-commons
                                            assertThat(input.body()).isNotNull();
                                            JsonNode bodyNode = input.body();

                                            // Verify simple string fields
                                            assertThat(bodyNode.path("recipient_first_name").asText()).isEqualTo("Jane");
                                            assertThat(bodyNode.path("recipient_last_name").asText()).isEqualTo("Doe");
                                            assertThat(bodyNode.path("recipient_email").asText()).isEqualTo("jane@example.com");
                                            assertThat(bodyNode.path("from_name").asText()).isEqualTo("Your Friendly AI");
                                            assertThat(bodyNode.path("message").asText()).isEqualTo("You've been wonderful. Here are some cookies.");

                                            // Verify variants is an array with expected value
                                            JsonNode variantsNode = bodyNode.path("variants");
                                            assertThat(variantsNode.isArray()).isTrue();
                                            assertThat(variantsNode.size()).isEqualTo(1);
                                            assertThat(variantsNode.get(0).asText()).isEqualTo("chocolate chip");
                                        });
                                assertThat(info.output())
                                        .isNotNull()
                                        .satisfies(output -> {
                                            // defensive null-check for static analysis
                                            assertThat(output).isNotNull();
                                            assertThat(output.type()).isEqualTo("json");
                                            // If the example is present, normalize to JsonNode and assert
                                            if (output.example() != null) {
                                                JsonNode ex = output.example();

                                                assertThat(ex.path("success").asBoolean()).isTrue();
                                                assertThat(ex.path("order_id")).isNotNull();

                                                // gift
                                                JsonNode giftNode = ex.path("gift");
                                                assertThat(giftNode.path("id").asText()).isEqualTo("agent-cookie-drop-v1");
                                                assertThat(giftNode.path("name").asText()).isEqualTo("Agent Surprise Cookies");

                                                // order
                                                JsonNode orderNode = ex.path("order");
                                                assertThat(orderNode.path("goody_order_batch_id").asText()).isEqualTo("batch-uuid");
                                                assertThat(orderNode.path("goody_order_id").asText()).isEqualTo("order-uuid");
                                                assertThat(orderNode.path("gift_link").asText()).isEqualTo("https://gifts.ongoody.com/gift/...");
                                                assertThat(orderNode.path("status").asText()).isEqualTo("created");

                                                JsonNode paymentNode = ex.path("payment");
                                                assertThat(paymentNode.path("amount_usdc").asText()).isEqualTo("20.00");
                                                assertThat(paymentNode.path("network").asText()).isEqualTo("eip155:8453");
                                                assertThat(paymentNode.path("payer")).isNotNull();
                                            }
                                        });
                            });
                    // Schema should be present and contain the $schema declaration
                    assertThat(bazaarExtension.schema()).isNotNull();
                    String schemaStr = bazaarExtension.schema().toString();
                    assertThat(schemaStr).contains("https://json-schema.org/draft/2020-12/schema");
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
