package tech.mogami.java.client.v2.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.mogami.commons.payment.schemes.exact.ExactSchemePayload;
import tech.mogami.commons.test.BaseTest;
import tech.mogami.java.client.v2.X402Client;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.mogami.commons.constant.BlockchainConstants.BLOCKCHAIN_ADDRESS_PREFIX;
import static tech.mogami.commons.constant.X402Constants.X402_DEFAULT_PAYMENT_TIMEOUT_SECONDS;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_REQUIRED_HEADER;
import static tech.mogami.commons.constant.network.Networks.BASE_SEPOLIA;
import static tech.mogami.commons.constant.network.contract.BaseContracts.BASE_SEPOLIA_USDC_CONTRACT;
import static tech.mogami.commons.constant.version.X402Versions.X402_SUPPORTED_VERSION_BY_MOGAMI;
import static tech.mogami.commons.payment.schemes.Schemes.EXACT_SCHEME;
import static tech.mogami.commons.payment.schemes.exact.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_NAME;
import static tech.mogami.commons.payment.schemes.exact.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_VERSION;

@DisplayName("X402Client createPaymentPayload() tests")
public class CreatePaymentPayloadTest extends BaseTest {

    @Test
    @DisplayName("Method execution")
    public void execute() {
        // Getting payments requirements ===============================================================================
        var paymentRequirementsList = X402Client.fetchPaymentRequirements(Map.of(X402_PAYMENT_REQUIRED_HEADER, getSampleEncodedPaymentRequired()));
        assertThat(paymentRequirementsList)
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

        // Creating payment payload ====================================================================================
        var now = Instant.now();
        var paymentPayload = X402Client.createPaymentPayload(paymentRequirementsList.getFirst(), TEST_CLIENT_WALLET_ADDRESS_1);
        assertThat(paymentPayload)
                .isNotNull()
                .satisfies(payload -> {
                    assertThat(payload.x402Version().equals(X402_SUPPORTED_VERSION_BY_MOGAMI.version()));
                    assertThat(payload.resource()).isNull();
                    assertThat(payload.accepted()).satisfies(p -> {
                        assertThat(p.scheme()).isEqualTo(EXACT_SCHEME.name());
                        assertThat(p.network()).isEqualTo(BASE_SEPOLIA.networkId());
                        assertThat(p.amount()).isEqualTo("10000");
                        assertThat(p.asset()).isEqualTo(BASE_SEPOLIA_USDC_CONTRACT);
                        assertThat(p.payTo()).isEqualTo("0x209693Bc6afc0C5328bA36FaF03C514EF312287C");
                        assertThat(p.maxTimeoutSeconds()).isEqualTo(60);
                        assertThat(p.getExtra(EXACT_SCHEME_PARAMETER_NAME)).isPresent();
                        assertThat(p.getExtra(EXACT_SCHEME_PARAMETER_NAME)).get().isEqualTo("USDC");
                        assertThat(p.getExtra(EXACT_SCHEME_PARAMETER_VERSION)).isPresent();
                        assertThat(p.getExtra(EXACT_SCHEME_PARAMETER_VERSION)).get().isEqualTo("2");
                    });
                    assertThat(paymentPayload.payload())
                            .isInstanceOfSatisfying(ExactSchemePayload.class, exactSchemePayload -> {
                                assertThat(exactSchemePayload.signature()).isNull();
                                assertThat(exactSchemePayload.authorization().from()).isEqualTo(TEST_CLIENT_WALLET_ADDRESS_1);
                                assertThat(exactSchemePayload.authorization().to()).isEqualTo("0x209693Bc6afc0C5328bA36FaF03C514EF312287C");
                                assertThat(exactSchemePayload.authorization().value()).isEqualTo("10000");
                                assertThat(exactSchemePayload.authorization().nonce()).isNotEmpty();
                                assertThat(exactSchemePayload.authorization().nonce()).startsWith(BLOCKCHAIN_ADDRESS_PREFIX);

                                // Time and date tests.
                                // Now          : 2025-10-09T20:54:38
                                // validAfter   : 2025-10-09T20:54:38
                                // validBefore  : 2025-10-09T20:55:38
                                assertThat(exactSchemePayload.authorization().validAfter()).isNotEmpty();
                                assertThat(exactSchemePayload.authorization().validBefore()).isNotEmpty();
                                var validAfterEpochSeconds = Long.parseLong(exactSchemePayload.authorization().validAfter());
                                var validBeforeEpochSeconds = Long.parseLong(exactSchemePayload.authorization().validBefore());
                                assertThat(validAfterEpochSeconds).isGreaterThanOrEqualTo(now.getEpochSecond());
                                assertThat(validBeforeEpochSeconds).isLessThanOrEqualTo(now.plusSeconds(X402_DEFAULT_PAYMENT_TIMEOUT_SECONDS).getEpochSecond());
                                assertThat(validBeforeEpochSeconds - validAfterEpochSeconds).isBetween(59L, 61L);
                            });
                    assertThat(paymentPayload.extensions()).isNotNull();
                    assertThat(paymentPayload.extensions()).isEmpty();
                });
    }
}
