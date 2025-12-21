package tech.mogami.java.client.test.v2.core;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.mogami.commons.test.BaseTest;
import tech.mogami.java.client.X402V2Client;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_RESPONSE_HEADER;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_SIGNATURE_HEADER;
import static tech.mogami.commons.constant.X402Error.INSUFFICIENT_FUNDS;
import static tech.mogami.commons.constant.network.Networks.BASE_SEPOLIA;

@DisplayName("X402Client fetchSettlementResponseTest() tests")
public class FetchSettlementResponseTest extends BaseTest {

    @Test
    @DisplayName("Method execution")
    void execute() {
        // No headers ==================================================================================================
        Map<String, String> headers = Map.of();
        Assertions.assertThat(X402V2Client.fetchSettlementResponse(headers)).isEmpty();

        // Some headers but without PAYMENT-RESPONSE ===================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_SIGNATURE_HEADER, "Some-Signature"
        ));
        assertThat(X402V2Client.fetchSettlementResponse(headers)).isEmpty();

        // With PAYMENT-RESPONSE but the encoded value is invalid ======================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_RESPONSE_HEADER, "Invalid-Encoded-Value"
        ));
        Map<String, String> finalHeaders1 = headers;
        assertThatThrownBy(() -> X402V2Client.fetchSettlementResponse(finalHeaders1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error during base64 decode for payment-response header");

        // With PAYMENT-RESPONSE but empty JSON ========================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_RESPONSE_HEADER, getEmptyJson()
        ));
        Map<String, String> finalHeaders2 = headers;
        // No error.
        X402V2Client.fetchSettlementResponse(finalHeaders2);

        // With PAYMENT-RESPONSE with valid data =======================================================================
        headers = new HashMap<>(Map.of(
                "Some-Header", "Some-Value",
                X402_PAYMENT_RESPONSE_HEADER, getSampleEncodedPaymentResponse()
        ));
        Map<String, String> finalHeaders3 = headers;
        assertThat(X402V2Client.fetchSettlementResponse(finalHeaders3))
                .isPresent()
                .get()
                .satisfies(paymentResponse -> {
                    assertThat(paymentResponse.success()).isFalse();
                    assertThat(paymentResponse.errorReason()).isEqualTo(INSUFFICIENT_FUNDS.getCode());
                    assertThat(paymentResponse.transaction()).isEmpty();
                    assertThat(paymentResponse.network()).isEqualTo(BASE_SEPOLIA.networkId());
                    assertThat(paymentResponse.payer()).isEqualTo("0x857b06519E91e3A54538791bDbb0E22373e36b66");
                });

    }

}
