package tech.mogami.java.client.helper.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.header.payment.PaymentPayload;
import tech.mogami.commons.header.payment.schemes.ExactSchemePayload;
import tech.mogami.java.client.helper.EIP712Helper;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.mogami.commons.constant.X402Constants.X402_SUPPORTED_VERSION;
import static tech.mogami.commons.constant.networks.BaseNetworks.BASE_SEPOLIA;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_NAME;
import static tech.mogami.commons.test.BaseTestData.TEST_CLIENT_WALLET_ADDRESS_1;
import static tech.mogami.commons.test.BaseTestData.TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY;
import static tech.mogami.commons.test.BaseTestData.TEST_SERVER_WALLET_ADDRESS_1;

@DisplayName("EIP-712 helper Tests")
public class EIP712HelperTest {

    @Test
    @DisplayName("EIP-712 sign Test")
    public void sign() throws Exception {
        /*
        {
          "x402Version": 1,
          "scheme": "exact",
          "network": "base-sepolia",
          "payload": {
            "signature": "0xde533856d81c76984a8dbc8d563bbb6d6d4ca36ce6c4d6e8cf315de3bfc14ab26d6bcdc37549aeed78bf92e39d5180268f8f399a4ffb816cfbf500823882b6001c",
            "authorization": {
              "from": "0x2980bc24bBFB34DE1BBC91479Cb712ffbCE02F73",
              "to": "0x7553F6FA4Fb62986b64f79aEFa1fB93ea64A22b1",
              "value": "10000",
              "validAfter": "1748534647",
              "validBefore": "1748534767",
              "nonce": "0x9b750f5097972d82c02ac371278b83ecf3ca3be8387db59e664eb38c98f97a3d"
            }
          }
        }
        */
        var expectedSignature = "0xde533856d81c76984a8dbc8d563bbb6d6d4ca36ce6c4d6e8cf315de3bfc14ab26d6bcdc37549aeed78bf92e39d5180268f8f399a4ffb816cfbf500823882b6001c";
        var credentials = Credentials.create(TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY);
        var paymentPayload = PaymentPayload.builder()
                .x402Version(X402_SUPPORTED_VERSION)
                .scheme(EXACT_SCHEME_NAME)
                .network(BASE_SEPOLIA)
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
        assertThat(EIP712Helper.sign(credentials, paymentPayload))
                .isEqualTo(expectedSignature);
    }

}
