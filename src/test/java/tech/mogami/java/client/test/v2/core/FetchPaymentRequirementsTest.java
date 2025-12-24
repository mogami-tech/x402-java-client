package tech.mogami.java.client.test.v2.core;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.mogami.commons.exception.X402Exception;
import tech.mogami.commons.test.BaseTest;
import tech.mogami.java.client.X402V2Client;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_REQUIRED_HEADER;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_RESPONSE_HEADER;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_SIGNATURE_HEADER;
import static tech.mogami.commons.constant.network.Networks.BASE_SEPOLIA;
import static tech.mogami.commons.constant.network.contract.BaseContracts.BASE_SEPOLIA_USDC_CONTRACT;
import static tech.mogami.commons.payment.schemes.Schemes.EXACT_SCHEME;
import static tech.mogami.commons.payment.schemes.exact.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_NAME;
import static tech.mogami.commons.payment.schemes.exact.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_VERSION;

@DisplayName("X402Client fetchPaymentRequirements() tests")
public class FetchPaymentRequirementsTest extends BaseTest {

    @Test
    @DisplayName("Method execution")
    void execute() {
        // No headers ==================================================================================================
        Map<String, String> headers = Map.of();
        Assertions.assertThat(X402V2Client.fetchPaymentRequirements(headers)).isEmpty();

        // Some headers but without PAYMENT-REQUIRED ===================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_SIGNATURE_HEADER, "Some-Signature",
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        assertThat(X402V2Client.fetchPaymentRequirements(headers)).isEmpty();

        // With PAYMENT-REQUIRED but the encoded value is invalid ======================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, "Invalid-Encoded-Value",
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        Map<String, String> finalHeaders1 = headers;
        assertThatThrownBy(() -> X402V2Client.fetchPaymentRequirements(finalHeaders1))
                .isInstanceOf(X402Exception.class)
                .hasMessageContaining("Invalid base64 payment-required header");

        // With PAYMENT-REQUIRED but empty JSON ========================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, getEmptyJson(),
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        Map<String, String> finalHeaders2 = headers;
        assertThatThrownBy(() -> X402V2Client.fetchPaymentRequirements(finalHeaders2))
                .isInstanceOf(X402Exception.class)
                .hasMessageContaining("Invalid x402 payment requirements");

        // With valid PAYMENT-REQUIRED but invalid version =============================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, getSampleEncodedPaymentRequiredV1(), // V1 is unsupported
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        Map<String, String> finalHeaders3 = headers;
        assertThatThrownBy(() -> X402V2Client.fetchPaymentRequirements(finalHeaders3))
                .isInstanceOf(X402Exception.class)
                .hasMessageContaining("Unsupported x402 version: 1");

        // With PAYMENT-REQUIRED but no accepts ========================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, getSampleEncodedPaymentPayloadWithoutAccepts(), // V2 without accepts
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        Map<String, String> finalHeaders4 = headers;
        assertThatThrownBy(() -> X402V2Client.fetchPaymentRequirements(finalHeaders4))
                .isInstanceOf(X402Exception.class)
                .hasMessageContaining("Invalid x402 payment requirements");

        // With valid PAYMENT-REQUIRED =================================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_REQUIRED_HEADER, getSampleEncodedPaymentRequired(),
                X402_PAYMENT_RESPONSE_HEADER, "Some-Response"
        ));
        assertThat(X402V2Client.fetchPaymentRequirements(headers))
                .hasSize(1)
                .first()
                .satisfies(paymentRequirements -> {
                    assertThat(paymentRequirements.scheme()).isEqualTo(EXACT_SCHEME.name());
                    assertThat(paymentRequirements.network()).isEqualTo(BASE_SEPOLIA.networkId());
                    assertThat(paymentRequirements.amount()).isEqualTo("10000");
                    assertThat(paymentRequirements.asset()).isEqualTo(BASE_SEPOLIA_USDC_CONTRACT);
                    assertThat(paymentRequirements.payTo()).isEqualTo("0x209693Bc6afc0C5328bA36FaF03C514EF312287C");
                    assertThat(paymentRequirements.maxTimeoutSeconds()).isEqualTo(60);
                    assertThat(paymentRequirements.getExtra(EXACT_SCHEME_PARAMETER_NAME)).isPresent();
                    assertThat(paymentRequirements.getExtra(EXACT_SCHEME_PARAMETER_NAME)).get().isEqualTo("USDC");
                    assertThat(paymentRequirements.getExtra(EXACT_SCHEME_PARAMETER_VERSION)).isPresent();
                    assertThat(paymentRequirements.getExtra(EXACT_SCHEME_PARAMETER_VERSION)).get().isEqualTo("2");
                });

        // TODO Add test for invalid payment requirements (e.g., negative amount)
    }

}
