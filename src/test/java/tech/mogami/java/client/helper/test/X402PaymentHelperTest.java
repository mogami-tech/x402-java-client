package tech.mogami.java.client.helper.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.header.payment.PaymentPayload;
import tech.mogami.commons.header.payment.PaymentRequirements;
import tech.mogami.commons.header.payment.schemes.ExactSchemePayload;
import tech.mogami.java.client.helper.X402PaymentHelper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.mogami.commons.constant.BlockchainConstants.BLOCKCHAIN_ADDRESS_PREFIX;
import static tech.mogami.commons.constant.StablecoinConstants.USDC;
import static tech.mogami.commons.constant.X402Constants.X402_SUPPORTED_VERSION;
import static tech.mogami.commons.constant.networks.Networks.BASE_SEPOLIA;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_NAME;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_NAME;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_VERSION;
import static tech.mogami.commons.test.BaseTestData.TEST_ASSET_CONTRACT_ADDRESS;
import static tech.mogami.commons.test.BaseTestData.TEST_CLIENT_WALLET_ADDRESS_1;
import static tech.mogami.commons.test.BaseTestData.TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY;
import static tech.mogami.commons.test.BaseTestData.TEST_PAYMENT_REQUIREMENTS_HEADER;
import static tech.mogami.commons.test.BaseTestData.TEST_SERVER_WALLET_ADDRESS_1;
import static tech.mogami.commons.test.BaseTestData.TEST_SERVER_WALLET_ADDRESS_2;

@DisplayName("X402PaymentHelper Tests")
public class X402PaymentHelperTest {

    @Test
    @DisplayName("getPaymentRequiredFromHeader()")
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
                                assertThat(paymentRequirements.network()).isEqualTo(BASE_SEPOLIA.name());
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
                                assertThat(pr.network()).isEqualTo(BASE_SEPOLIA.name());
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

    @Test
    @DisplayName("getPayloadFromPaymentRequirements()")
    public void testGetPayloadFromPaymentRequirements() {
        // Getting a specific payment requirements payload.
        var paymentRequirements1 = X402PaymentHelper
                .getPaymentRequiredFromHeader(TEST_PAYMENT_REQUIREMENTS_HEADER)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No payment requirements found"));

        assertThat(X402PaymentHelper.getPayloadFromPaymentRequirements(
                "INVALID_SIGNATURE",
                TEST_CLIENT_WALLET_ADDRESS_1,
                paymentRequirements1.accepts().getFirst()
        )).satisfies(paymentPayload -> {
            assertThat(paymentPayload.x402Version()).isEqualTo(X402_SUPPORTED_VERSION);
            assertThat(paymentPayload.scheme()).isEqualTo(EXACT_SCHEME_NAME);
            assertThat(paymentPayload.network()).isEqualTo(BASE_SEPOLIA.name());
            assertThat(paymentPayload.payload())
                    .isInstanceOfSatisfying(tech.mogami.commons.header.payment.schemes.ExactSchemePayload.class, exactSchemePayload -> {
                        assertThat(exactSchemePayload.signature()).isEqualTo("INVALID_SIGNATURE");
                        assertThat(exactSchemePayload.authorization().from()).isEqualTo(TEST_CLIENT_WALLET_ADDRESS_1);
                        assertThat(exactSchemePayload.authorization().to()).isEqualTo(TEST_SERVER_WALLET_ADDRESS_1);
                        assertThat(exactSchemePayload.authorization().value()).isEqualTo("1000");
                        // TODO Test validAfter & validBefore on values.
                        assertThat(exactSchemePayload.authorization().validAfter()).isNotEmpty();
                        assertThat(exactSchemePayload.authorization().validBefore()).isNotEmpty();
                        assertThat(exactSchemePayload.authorization().nonce()).isNotEmpty();
                        assertThat(exactSchemePayload.authorization().nonce()).startsWith(BLOCKCHAIN_ADDRESS_PREFIX);
                    });
        });
    }

    @Test
    @DisplayName("getSignedPayload()")
    public void testGetSignedPayload() {
        var expectedSignature = "0xde533856d81c76984a8dbc8d563bbb6d6d4ca36ce6c4d6e8cf315de3bfc14ab26d6bcdc37549aeed78bf92e39d5180268f8f399a4ffb816cfbf500823882b6001c";
        var credentials = Credentials.create(TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY);
        var paymentRequirements = PaymentRequirements.builder()
                .scheme(EXACT_SCHEME_NAME)
                .network(BASE_SEPOLIA.name())
                .maxAmountRequired("10000")
                .resource("http://localhost/weather")
                .payTo(TEST_SERVER_WALLET_ADDRESS_1)
                .asset("0x036CbD53842c5426634e7929541eC2318f3dCF7e")
                .extra(EXACT_SCHEME_PARAMETER_NAME, "USDC")
                .extra(EXACT_SCHEME_PARAMETER_VERSION, "2")
                .build();
        var paymentPayload = PaymentPayload.builder()
                .x402Version(X402_SUPPORTED_VERSION)
                .scheme(EXACT_SCHEME_NAME)
                .network(BASE_SEPOLIA.name())
                .payload(ExactSchemePayload.builder()
                        .authorization(ExactSchemePayload.Authorization.builder()
                                .from(TEST_CLIENT_WALLET_ADDRESS_1)
                                .to(TEST_SERVER_WALLET_ADDRESS_1)
                                .value("10000")
                                .validAfter("1748534647")
                                .validBefore("1748534767")
                                .nonce("0x9b750f5097972d82c02ac371278b83ecf3ca3be8387db59e664eb38c98f97a3d")
                                .build()
                        )
                        .build()
                )
                .build();

        // We test the signing of the payment payload.
        assertThat(X402PaymentHelper.getSignedPayload(credentials, paymentRequirements, paymentPayload))
                .satisfies(signedPayload -> {
                    assertThat(signedPayload.x402Version()).isEqualTo(X402_SUPPORTED_VERSION);
                    assertThat(signedPayload.scheme()).isEqualTo(EXACT_SCHEME_NAME);
                    assertThat(signedPayload.network()).isEqualTo(BASE_SEPOLIA.name());
                    assertThat(signedPayload.payload())
                            .isInstanceOfSatisfying(ExactSchemePayload.class, exactSchemePayload -> {
                                assertThat(exactSchemePayload.signature()).isEqualTo(expectedSignature);
                                assertThat(exactSchemePayload.authorization().from()).isEqualTo(TEST_CLIENT_WALLET_ADDRESS_1);
                                assertThat(exactSchemePayload.authorization().to()).isEqualTo(TEST_SERVER_WALLET_ADDRESS_1);
                                assertThat(exactSchemePayload.authorization().value()).isEqualTo("10000");
                                // TODO Test validAfter & validBefore on values.
                                assertThat(exactSchemePayload.authorization().validAfter()).isNotEmpty();
                                assertThat(exactSchemePayload.authorization().validBefore()).isNotEmpty();
                                assertThat(exactSchemePayload.authorization().nonce()).isNotEmpty();
                                assertThat(exactSchemePayload.authorization().nonce()).startsWith(BLOCKCHAIN_ADDRESS_PREFIX);
                            });
                });
    }

}
