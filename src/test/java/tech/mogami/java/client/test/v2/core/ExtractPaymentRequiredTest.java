package tech.mogami.java.client.test.v2.core;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.mogami.commons.exception.X402Exception;
import tech.mogami.commons.test.BaseMogamiTest;
import tech.mogami.java.client.X402V2Client;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_REQUIRED_HEADER;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_RESPONSE_HEADER;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_SIGNATURE_HEADER;
import static tech.mogami.commons.constant.version.X402Versions.X402_SUPPORTED_VERSION_BY_MOGAMI;

@DisplayName("X402Client extractPaymentRequired() tests")
public class ExtractPaymentRequiredTest extends BaseMogamiTest {

    @Test
    @DisplayName("Method execution")
    void execute() {
        // No headers ==================================================================================================
        Map<String, String> headers = Map.of();
        Assertions.assertThat(X402V2Client.extractPaymentRequired(headers)).isEmpty();

        // Some headers but without PAYMENT-REQUIRED ===================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_SIGNATURE_HEADER, "Some-Signature",
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        assertThat(X402V2Client.extractPaymentRequired(headers)).isEmpty();

        // With PAYMENT-REQUIRED but the encoded value is invalid ======================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, "Invalid-Encoded-Value",
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        Map<String, String> finalHeaders1 = headers;
        assertThatThrownBy(() -> X402V2Client.extractPaymentRequired(finalHeaders1))
                .isInstanceOf(X402Exception.class)
                .hasMessageContaining("Invalid base64 payment-required header");

        // With PAYMENT-REQUIRED but empty JSON ========================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, getEmptyJson(),
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        Map<String, String> finalHeaders2 = headers;
        assertThatThrownBy(() -> X402V2Client.extractPaymentRequired(finalHeaders2))
                .isInstanceOf(X402Exception.class)
                .hasMessageContaining("Invalid x402 payment requirements");

        // With valid PAYMENT-REQUIRED but invalid version =============================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, getSampleEncodedPaymentRequiredV1(), // V1 is unsupported
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        Map<String, String> finalHeaders3 = headers;
        assertThatThrownBy(() -> X402V2Client.extractPaymentRequired(finalHeaders3))
                .isInstanceOf(X402Exception.class)
                .hasMessageContaining("Unsupported x402 version: 1");

        // With PAYMENT-REQUIRED but no accepts ========================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, getSampleEncodedPaymentPayloadWithoutAccepts(), // V2 without accepts
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        Map<String, String> finalHeaders4 = headers;
        assertThatThrownBy(() -> X402V2Client.extractPaymentRequired(finalHeaders4))
                .isInstanceOf(X402Exception.class)
                .hasMessageContaining("Invalid x402 payment requirements");

        // With valid PAYMENT-REQUIRED =================================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, getSampleEncodedPaymentRequired(),
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        assertThat(X402V2Client.extractPaymentRequired(headers))
                .isPresent().get()
                .satisfies(paymentRequired -> {
                    assertThat(paymentRequired.x402Version().equals(X402_SUPPORTED_VERSION_BY_MOGAMI.version()));
                    assertThat(paymentRequired.resource())
                            .isNotNull()
                            .satisfies(paymentResource -> {
                                assertThat(paymentResource).isNotNull();
                                assertThat(paymentResource.url()).isEqualTo("https://api.example.com/premium-data");
                                assertThat(paymentResource.description()).isEqualTo("Access to premium market data");
                                assertThat(paymentResource.mimeType()).isEqualTo("application/json");
                            });
                    assertThat(paymentRequired.accepts())
                            .hasSize(1)
                            .satisfies(paymentRequirements -> {
                                assertThat(paymentRequirements.getFirst().scheme()).isEqualTo("exact");
                                assertThat(paymentRequirements.getFirst().network()).isEqualTo("eip155:84532");
                                assertThat(paymentRequirements.getFirst().amount()).isEqualTo("10000");
                                assertThat(paymentRequirements.getFirst().asset()).isEqualTo("0x036CbD53842c5426634e7929541eC2318f3dCF7e");
                                assertThat(paymentRequirements.getFirst().payTo()).isEqualTo("0x209693Bc6afc0C5328bA36FaF03C514EF312287C");
                                assertThat(paymentRequirements.getFirst().maxTimeoutSeconds()).isEqualTo(60);
                                assertThat(paymentRequirements.getFirst().getExtra("name")).isPresent();
                                assertThat(paymentRequirements.getFirst().getExtra("name")).get().isEqualTo("USDC");
                                assertThat(paymentRequirements.getFirst().getExtra("version")).isPresent();
                                assertThat(paymentRequirements.getFirst().getExtra("version")).get().isEqualTo("2");
                            });
                });

        // TODO Add test for invalid payment requirements (e.g., negative amount)
    }

}
