package tech.mogami.java.client.helper.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.header.payment.PaymentPayload;
import tech.mogami.commons.header.payment.PaymentRequirements;
import tech.mogami.commons.header.payment.schemes.exact.ExactSchemePayload;
import tech.mogami.java.client.helper.X402PaymentHelper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.mogami.commons.constant.BlockchainConstants.BLOCKCHAIN_ADDRESS_PREFIX;
import static tech.mogami.commons.constant.network.Networks.BASE_SEPOLIA;
import static tech.mogami.commons.constant.stablecoin.Stablecoins.USDC;
import static tech.mogami.commons.constant.version.X402Versions.X402_SUPPORTED_VERSION_BY_MOGAMI;
import static tech.mogami.commons.header.payment.schemes.Schemes.EXACT_SCHEME;
import static tech.mogami.commons.header.payment.schemes.exact.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_NAME;
import static tech.mogami.commons.header.payment.schemes.exact.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_VERSION;
import static tech.mogami.commons.test.BaseTestData.TEST_ASSET_CONTRACT_ADDRESS;
import static tech.mogami.commons.test.BaseTestData.TEST_CLIENT_WALLET_ADDRESS_1;
import static tech.mogami.commons.test.BaseTestData.TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY;
import static tech.mogami.commons.test.BaseTestData.TEST_PAYMENT_REQUIREMENTS_HEADER;
import static tech.mogami.commons.test.BaseTestData.TEST_SERVER_WALLET_ADDRESS_1;
import static tech.mogami.commons.test.BaseTestData.TEST_SERVER_WALLET_ADDRESS_2;

@DisplayName("X402PaymentHelper Tests")
public class X402PaymentHelperTest {

    @Test
    @DisplayName("getPaymentRequiredFromBody()")
    public void testSetPaymentRequiredFromHeader() {
        // Test when the header is null or empty.
        assertTrue(X402PaymentHelper.getPaymentRequiredFromBody(null).isEmpty());
        assertTrue(X402PaymentHelper.getPaymentRequiredFromBody("").isEmpty());

        // Test with a valid JSON string from the X-Payment header.
        assertThat(X402PaymentHelper.getPaymentRequiredFromBody(TEST_PAYMENT_REQUIREMENTS_HEADER))
                .isPresent()
                .get()
                .satisfies(payment -> {
                    assertThat(payment.x402Version()).isEqualTo(X402_SUPPORTED_VERSION_BY_MOGAMI.version());
                    assertThat(payment.accepts().size()).isEqualTo(2);
                    // First payment requirements.
                    assertThat(payment.accepts().getFirst())
                            .satisfies(paymentRequirements -> {
                                assertThat(paymentRequirements.scheme()).isEqualTo(EXACT_SCHEME.name());
                                assertThat(paymentRequirements.network()).isEqualTo(BASE_SEPOLIA.name());
                                assertThat(paymentRequirements.maxAmountRequired()).isEqualTo("1000");
                                assertThat(paymentRequirements.resource()).isEqualTo("http://localhost/weather");
                                assertThat(paymentRequirements.description()).isEmpty();
                                assertThat(paymentRequirements.mimeType()).isEmpty();
                                assertThat(paymentRequirements.payTo()).isEqualTo(TEST_SERVER_WALLET_ADDRESS_1);
                                assertThat(paymentRequirements.maxTimeoutSeconds()).isEqualTo(60);
                                assertThat(paymentRequirements.asset()).isEqualTo(TEST_ASSET_CONTRACT_ADDRESS);
                                assertThat(paymentRequirements.extra().get(EXACT_SCHEME_PARAMETER_NAME)).isEqualTo(USDC.name());
                                assertThat(paymentRequirements.extra().get(EXACT_SCHEME_PARAMETER_VERSION)).isEqualTo("2");
                            });
                    // Second payment requirements.
                    assertThat(payment.accepts().getLast())
                            .satisfies(pr -> {
                                assertThat(pr.scheme()).isEqualTo(EXACT_SCHEME.name());
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
                .getPaymentRequiredFromBody(TEST_PAYMENT_REQUIREMENTS_HEADER)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No payment requirements found"));

        assertThat(X402PaymentHelper.getPayloadFromPaymentRequirements(
                "INVALID_SIGNATURE",
                TEST_CLIENT_WALLET_ADDRESS_1,
                paymentRequirements1.accepts().getFirst()
        )).satisfies(paymentPayload -> {
            assertThat(paymentPayload.x402Version()).isEqualTo(X402_SUPPORTED_VERSION_BY_MOGAMI.version());
            assertThat(paymentPayload.scheme()).isEqualTo(EXACT_SCHEME.name());
            assertThat(paymentPayload.network()).isEqualTo(BASE_SEPOLIA.name());
            assertThat(paymentPayload.payload())
                    .isInstanceOfSatisfying(ExactSchemePayload.class, exactSchemePayload -> {
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
                .scheme(EXACT_SCHEME.name())
                .network(BASE_SEPOLIA.name())
                .maxAmountRequired("10000")
                .resource("http://localhost/weather")
                .payTo(TEST_SERVER_WALLET_ADDRESS_1)
                .asset("0x036CbD53842c5426634e7929541eC2318f3dCF7e")
                .extra(EXACT_SCHEME_PARAMETER_NAME, "USDC")
                .extra(EXACT_SCHEME_PARAMETER_VERSION, "2")
                .build();
        var paymentPayload = PaymentPayload.builder()
                .x402Version(X402_SUPPORTED_VERSION_BY_MOGAMI.version())
                .scheme(EXACT_SCHEME.name())
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
                    assertThat(signedPayload.x402Version()).isEqualTo(X402_SUPPORTED_VERSION_BY_MOGAMI.version());
                    assertThat(signedPayload.scheme()).isEqualTo(EXACT_SCHEME.name());
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

    @Test
    @DisplayName("getPayloadHeader()")
    public void testGetPayloadHeader() {
        var expectedXPaymentHeader = "eyJ4NDAyVmVyc2lvbiI6MSwic2NoZW1lIjoiZXhhY3QiLCJuZXR3b3JrIjoiYmFzZS1zZXBvbGlhIiwicGF5bG9hZCI6eyJzaWduYXR1cmUiOiIweGRkY2Y4N2JiYjg3ZTRmMDU5Zjg4M2Y2YzFlNzZlOTg0OWQzNzNlMDlhNzM0NTgwY2U5MmY1YTA2ODIxYTJiOTk1YzdkMGQ2NzhkODI0MDY4NjAxMWJhNTc0MWNiZjU5ZDMzM2UyYWQ2ZjI1NTk3MWUyYjI0ZWIxMDdhY2E3OWE3MWMiLCJhdXRob3JpemF0aW9uIjp7ImZyb20iOiIweDI5ODBiYzI0YkJGQjM0REUxQkJDOTE0NzlDYjcxMmZmYkNFMDJGNzMiLCJ0byI6IjB4NzU1M0Y2RkE0RmI2Mjk4NmI2NGY3OWFFRmExZkI5M2VhNjRBMjJiMSIsInZhbHVlIjoiMTAwMCIsInZhbGlkQWZ0ZXIiOiIxNzQ4NTU0NjI5IiwidmFsaWRCZWZvcmUiOiIxNzQ4NTU0NzQ5Iiwibm9uY2UiOiIweDE3NjgwNTgxMzQ4ZmRmZjllOWM5ZDc1MTI0ZDJmMjdkZjgwNTAyZWRmYzFlNTAyYzNiMTRhODk2MTVkY2VmNDYifX19";
        var paymentPayload = PaymentPayload.builder()
                .x402Version(X402_SUPPORTED_VERSION_BY_MOGAMI.version())
                .scheme(EXACT_SCHEME.name())
                .network(BASE_SEPOLIA.name())
                .payload(ExactSchemePayload.builder()
                        .signature("0xddcf87bbb87e4f059f883f6c1e76e9849d373e09a734580ce92f5a06821a2b995c7d0d678d8240686011ba5741cbf59d333e2ad6f255971e2b24eb107aca79a71c")
                        .authorization(ExactSchemePayload.Authorization.builder()
                                .from("0x2980bc24bBFB34DE1BBC91479Cb712ffbCE02F73")
                                .to("0x7553F6FA4Fb62986b64f79aEFa1fB93ea64A22b1")
                                .value("1000")
                                .validAfter("1748554629")
                                .validBefore("1748554749")
                                .nonce("0x17680581348fdff9e9c9d75124d2f27df80502edfc1e502c3b14a89615dcef46")
                                .build()
                        )
                        .build()
                )
                .build();

        // We test base64 result.
        assertThat(X402PaymentHelper.getPayloadHeader(paymentPayload))
                .isEqualTo(expectedXPaymentHeader);
    }

    @Test
    @DisplayName("getSettleResponseFromHeader() with null payload")
    public void testGetSettleResponseFromHeader() {
        // Test with empty values.
        assertTrue(X402PaymentHelper.getSettleResponseFromHeader(null).isEmpty());
        assertTrue(X402PaymentHelper.getSettleResponseFromHeader("").isEmpty());

        var encodedSettleResponse = "eyJzdWNjZXNzIjp0cnVlLCJuZXR3b3JrIjoiYmFzZS1zZXBvbGlhIiwidHJhbnNhY3Rpb24iOiIweDI5YWEzYzdhMDgyNzRlNmRmZjY2Yzc5YjFiMDg2ZDQzM2MyYWI5Yzg1MDUxZWNlZTAyNGIwNTMxYjIyOTQ0ZGUiLCJlcnJvclJlYXNvbiI6bnVsbCwicGF5ZXIiOiIweDI5ODBiYzI0YkJGQjM0REUxQkJDOTE0NzlDYjcxMmZmYkNFMDJGNzMifQ";

        // We test the settle response header.
        assertThat(X402PaymentHelper.getSettleResponseFromHeader(encodedSettleResponse))
                .isPresent()
                .get()
                .satisfies(settleResponse -> {
                    assertThat(settleResponse.success()).isTrue();
                    assertThat(settleResponse.network()).isEqualTo(BASE_SEPOLIA.name());
                    assertThat(settleResponse.transaction()).isEqualTo("0x29aa3c7a08274e6dff66c79b1b086d433c2ab9c85051ecee024b0531b22944de");
                    assertThat(settleResponse.errorReason()).isNull();
                    assertThat(settleResponse.payer()).isEqualTo(TEST_CLIENT_WALLET_ADDRESS_1);
                });
    }

}
