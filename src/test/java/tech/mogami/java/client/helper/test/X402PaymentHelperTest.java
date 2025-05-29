package tech.mogami.java.client.helper.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.mogami.java.client.helper.X402PaymentHelper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.mogami.commons.constant.StablecoinConstants.USDC;
import static tech.mogami.commons.constant.X402Constants.X402_SUPPORTED_VERSION;
import static tech.mogami.commons.constant.networks.BaseNetworks.BASE_SEPOLIA;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_NAME;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_NAME;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_VERSION;
import static tech.mogami.commons.test.BaseTestData.TEST_ASSET_CONTRACT_ADDRESS;
import static tech.mogami.commons.test.BaseTestData.TEST_PAYMENT_REQUIREMENTS_HEADER;
import static tech.mogami.commons.test.BaseTestData.TEST_SERVER_WALLET_ADDRESS_1;
import static tech.mogami.commons.test.BaseTestData.TEST_SERVER_WALLET_ADDRESS_2;

@DisplayName("X402PaymentHelper Tests")
public class X402PaymentHelperTest {

    @Test
    @DisplayName("getPaymentRequiredFromHeader")
    public void testSetPaymentRequiredFromHeader() {
        // Test when the header is null or empty.
        assertTrue(X402PaymentHelper.getPaymentRequiredFromHeader(null).isEmpty());
        assertTrue(X402PaymentHelper.getPaymentRequiredFromHeader("").isEmpty());

        // Test with a valid JSON string from the X-Payment header.
        assertThat(X402PaymentHelper.getPaymentRequiredFromHeader(TEST_PAYMENT_REQUIREMENTS_HEADER))
                .isPresent()
                .get()
                .satisfies(payment -> {
                    assertThat(payment.x402Version()).isEqualTo(X402_SUPPORTED_VERSION);
                    assertThat(payment.accepts().size()).isEqualTo(2);
                    // First payment requirements.
                    assertThat(payment.accepts().getFirst())
                            .satisfies(paymentRequirements -> {
                                assertThat(paymentRequirements.scheme()).isEqualTo(EXACT_SCHEME_NAME);
                                assertThat(paymentRequirements.network()).isEqualTo(BASE_SEPOLIA);
                                assertThat(paymentRequirements.maxAmountRequired()).isEqualTo("1000");
                                assertThat(paymentRequirements.resource()).isEqualTo("http://localhost/weather");
                                assertThat(paymentRequirements.description()).isEmpty();
                                assertThat(paymentRequirements.mimeType()).isEmpty();
                                assertThat(paymentRequirements.payTo()).isEqualTo(TEST_SERVER_WALLET_ADDRESS_1);
                                assertThat(paymentRequirements.maxTimeoutSeconds()).isEqualTo(60);
                                assertThat(paymentRequirements.asset()).isEqualTo(TEST_ASSET_CONTRACT_ADDRESS);
                                assertThat(paymentRequirements.extra().get(EXACT_SCHEME_PARAMETER_NAME)).isEqualTo(USDC);
                                assertThat(paymentRequirements.extra().get(EXACT_SCHEME_PARAMETER_VERSION)).isEqualTo("2");
                            });
                    // Second payment requirements.
                    assertThat(payment.accepts().getLast())
                            .satisfies(pr -> {
                                assertThat(pr.scheme()).isEqualTo(EXACT_SCHEME_NAME);
                                assertThat(pr.network()).isEqualTo(BASE_SEPOLIA);
                                assertThat(pr.maxAmountRequired()).isEqualTo("2000");
                                assertThat(pr.resource()).isEqualTo("http://localhost/weather");
                                assertThat(pr.description()).isEqualTo("Description number 2");
                                assertThat(pr.mimeType()).isEmpty();
                                assertThat(pr.payTo()).isEqualTo(TEST_SERVER_WALLET_ADDRESS_2);
                                assertThat(pr.maxTimeoutSeconds()).isEqualTo(60);
                                assertThat(pr.asset()).isEqualTo(TEST_ASSET_CONTRACT_ADDRESS);
                                assertThat(pr.extra().size()).isEqualTo(0);
                            });
                    assertThat(payment.error()).isEqualTo("Payment required");
                });
    }

}
