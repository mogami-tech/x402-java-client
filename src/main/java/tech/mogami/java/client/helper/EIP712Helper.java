package tech.mogami.java.client.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;
import tech.mogami.commons.constant.networks.Network;
import tech.mogami.commons.constant.networks.Networks;
import tech.mogami.commons.header.payment.PaymentPayload;
import tech.mogami.commons.header.payment.PaymentRequirements;
import tech.mogami.commons.header.payment.schemes.ExactSchemePayload;

import java.math.BigInteger;
import java.util.Arrays;

import static tech.mogami.commons.constant.BlockchainConstants.BLOCKCHAIN_ADDRESS_PREFIX;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_NAME;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_PARAMETER_VERSION;

/**
 * Utility class for EIP-712 related operations.
 */
@UtilityClass
@SuppressWarnings({"magicnumber", "HideUtilityClassConstructor", "unused"})
public class EIP712Helper {

    /** ObjectMapper instance for JSON operations. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** EIP-712 JSON. */
    private static final String TYPES = """
            {
              "EIP712Domain":[
                {"name":"name","type":"string"},
                {"name":"version","type":"string"},
                {"name":"chainId","type":"uint256"},
                {"name":"verifyingContract","type":"address"}
              ],
              "TransferWithAuthorization":[
                {"name":"from","type":"address"},
                {"name":"to","type":"address"},
                {"name":"value","type":"uint256"},
                {"name":"validAfter","type":"uint256"},
                {"name":"validBefore","type":"uint256"},
                {"name":"nonce","type":"bytes32"}
              ]
            }
            """;

    /**
     * Signs an authorization message using EIP-712 structured data signing.
     *
     * @param credentials          the credentials of the signer
     * @param paymentsRequirements the payment requirements containing network and scheme information
     * @param paymentPayload       the payment payload containing authorization details
     * @return the signature in hexadecimal format
     * @throws Exception if an error occurs during signing
     */
    public static String sign(@NonNull final Credentials credentials,
                              @NonNull final PaymentRequirements paymentsRequirements,
                              @NonNull final PaymentPayload paymentPayload) throws Exception {
        // Build the EIP-712 typed-data JSON (domain + message) exactly once
        String typedDataJson = buildTypedDataJson(paymentsRequirements, paymentPayload);

        // Sign and hex-encode
        return toHex(Sign.signTypedData(typedDataJson, credentials.getEcKeyPair()));
    }

    /**
     * Verifies a signature against the expected signer using EIP-712 structured data signing.
     *
     * @param signatureHex         the signature in hexadecimal format
     * @param paymentsRequirements the payment requirements containing network and scheme information
     * @param paymentPayload       the payment payload containing authorization details
     * @param expectedSigner       the expected signer's address
     * @return true if the signature is valid for the expected signer, false otherwise
     * @throws Exception if an error occurs during verification
     */
    public static boolean verify(@NonNull final String signatureHex,
                                 @NonNull final PaymentRequirements paymentsRequirements,
                                 @NonNull final PaymentPayload paymentPayload,
                                 @NonNull final String expectedSigner) throws Exception {

        // Re-create the typed-data JSON exactly as in sign()
        String typedDataJson = buildTypedDataJson(paymentsRequirements, paymentPayload);

        // Hash according to EIP-712
        byte[] dataHash = new StructuredDataEncoder(typedDataJson).hashStructuredData();

        Sign.SignatureData sig = signatureDataFromHex(signatureHex);
        BigInteger recoveredKey = Sign.signedMessageHashToKey(dataHash, sig);
        String     recoveredAddress = BLOCKCHAIN_ADDRESS_PREFIX + Keys.getAddress(recoveredKey);

        // Compare
        return recoveredAddress.equalsIgnoreCase(Keys.toChecksumAddress(expectedSigner));
    }


    /**
     * Builds the JSON representation of the typed data for EIP-712 signing.
     * TODO Remove this class as it's nos part of x402 commons.
     *
     * @param paymentsRequirements the payment requirements containing network and scheme information
     * @param paymentPayload       the payment payload containing authorization details
     * @return the JSON string representation of the typed data
     * @throws Exception if an error occurs during JSON creation
     */
    private static String buildTypedDataJson(final PaymentRequirements paymentsRequirements,
                                             final PaymentPayload paymentPayload) throws Exception {

        // Validate inputs =============================================================================================
        // TODO Add validations on scheme name, parameter version; extra parameters...
        Network network = Networks.findByName(paymentsRequirements.network())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported network: " + paymentsRequirements.network()));

        // Build the EIP-712 typed-data JSON (domain + message) ========================================================
        ObjectNode domain = MAPPER.createObjectNode();
        domain.put("name",    paymentsRequirements.extra().get(EXACT_SCHEME_PARAMETER_NAME));
        domain.put("version", paymentsRequirements.extra().get(EXACT_SCHEME_PARAMETER_VERSION));
        domain.put("chainId", network.chainId());
        domain.put("verifyingContract", paymentsRequirements.asset());

        ExactSchemePayload exactSchemePayload = (ExactSchemePayload) paymentPayload.payload();

        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("from",        exactSchemePayload.authorization().from());
        msg.put("to",          exactSchemePayload.authorization().to());
        msg.put("value",       new BigInteger(exactSchemePayload.authorization().value()));
        msg.put("validAfter",  new BigInteger(exactSchemePayload.authorization().validAfter()));
        msg.put("validBefore", new BigInteger(exactSchemePayload.authorization().validBefore()));
        msg.put("nonce",       exactSchemePayload.authorization().nonce());

        ObjectNode root = MAPPER.createObjectNode();
        root.put("primaryType", "TransferWithAuthorization");
        root.set("types",  MAPPER.readTree(TYPES));
        root.set("domain", domain);
        root.set("message", msg);

        return MAPPER.writeValueAsString(root);
    }

    /**
     * Converts a SignatureData object to a hexadecimal string representation.
     *
     * @param sig the SignatureData object to convert
     * @return the hexadecimal string representation of the signature
     */
    private static String toHex(final Sign.SignatureData sig) {
        byte[] v = sig.getV();
        int vValue = v[0];
        if (vValue != 27 && vValue != 28) {
            vValue = vValue + 27;
        }
        return BLOCKCHAIN_ADDRESS_PREFIX
                + Numeric.toHexStringNoPrefixZeroPadded(new BigInteger(1, sig.getR()), 64)
                + Numeric.toHexStringNoPrefixZeroPadded(new BigInteger(1, sig.getS()), 64)
                + String.format("%02x", vValue);
    }

    /**
     * Converts a hexadecimal signature string to a SignatureData object.
     *
     * @param signatureHex the hexadecimal signature string
     * @return the SignatureData object
     */
    private static Sign.SignatureData signatureDataFromHex(final String signatureHex) {
        byte[] sigBytes = Numeric.hexStringToByteArray(signatureHex);

        byte   v = sigBytes[64];
        if (v < 27) {
            v += 27; // align with Ethereum yellow-paper values
        }

        byte[] r = Arrays.copyOfRange(sigBytes, 0, 32);
        byte[] s = Arrays.copyOfRange(sigBytes, 32, 64);

        return new Sign.SignatureData(v, r, s);
    }

}
