package tech.mogami.java.client.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import tech.mogami.commons.header.payment.PaymentPayload;
import tech.mogami.commons.header.payment.schemes.ExactSchemePayload;

import java.math.BigInteger;

/**
 * Utility class for EIP-712 related operations.
 * <p>
 * This class provides methods to handle EIP-712 structured data signing and verification.
 * </p>
 */
@UtilityClass
@SuppressWarnings("unused")
public class EIP712Helper {

    /** ObjectMapper instance for JSON operations. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** EIP-712 domain. */
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
     * @param credentials the credentials of the signer
     * @param paymentPayload the payment payload containing authorization details
     * @return the signature in hexadecimal format
     * @throws Exception if an error occurs during signing
     */
    public static String sign(@NonNull final Credentials credentials,
                              @NonNull final PaymentPayload paymentPayload) throws Exception {

        /* ---------- domain ---------- */
        ObjectNode domain = MAPPER.createObjectNode();
        domain.put("name", "USDC");
        domain.put("version", "2");
        domain.put("chainId", 84532);   // TODO Change depending on the chosen network
        domain.put("verifyingContract", "0x036CbD53842c5426634e7929541eC2318f3dCF7e");

        /* ---------- message ---------- */
        ObjectNode msg = MAPPER.createObjectNode();
        ExactSchemePayload exactSchemePayload = (ExactSchemePayload) paymentPayload.payload();
        msg.put("from", exactSchemePayload.authorization().from());
        msg.put("to", exactSchemePayload.authorization().to());

        //msg.put("value", exactSchemePayload.authorization().value());
        //msg.put("validAfter", exactSchemePayload.authorization().validAfter());
        //msg.put("validBefore", exactSchemePayload.authorization().validBefore());
        //msg.put("nonce", exactSchemePayload.authorization().nonce());

        msg.put("value",        new BigInteger(exactSchemePayload.authorization().value()));              // <- nombre
        msg.put("validAfter",   new BigInteger(exactSchemePayload.authorization().validAfter()));         // <- nombre
        msg.put("validBefore",  new BigInteger(exactSchemePayload.authorization().validBefore()));        // <- nombre
        msg.put("nonce",        exactSchemePayload.authorization().nonce());                              // bytes32 reste une chaîne hexa


        /* ---------- typed-data ---------- */
        ObjectNode root = MAPPER.createObjectNode();
        root.put("primaryType", "TransferWithAuthorization");
        root.set("types",  MAPPER.readTree(TYPES));
        root.set("domain", domain);
        root.set("message", msg);

        String json = MAPPER.writeValueAsString(root);
        System.out.println("Generated EIP-712 JSON:");
        System.out.println(json);

        /* ---------- signature ---------- */
        Sign.SignatureData sig = Sign.signTypedData(json, credentials.getEcKeyPair());

        System.out.println("=== Composants de la signature ===");
        System.out.println("R: " + Numeric.toHexString(sig.getR()));
        System.out.println("S: " + Numeric.toHexString(sig.getS()));
        System.out.println("V raw: " + Numeric.toHexString(sig.getV()));

        String result = toHex(sig);
        System.out.println("Signature finale: " + result);
        System.out.println("Signature attendue: 0xde533856d81c76984a8dbc8d563bbb6d6d4ca36ce6c4d6e8cf315de3bfc14ab26d6bcdc37549aeed78bf92e39d5180268f8f399a4ffb816cfbf500823882b6001c");

        return result;
    }

    /**
     * Converts a SignatureData object to a hexadecimal string representation.
     * Correction 4: Gérer correctement la valeur V pour EIP-712
     */
    private static String toHex(Sign.SignatureData sig) {
        // Pour EIP-712, V doit être 27 ou 28 (0x1b ou 0x1c)
        byte[] v = sig.getV();
        int vValue = v[0];

        // Si V est déjà 27 ou 28, l'utiliser tel quel
        // Sinon, ajouter 27 (conversion standard)
        if (vValue != 27 && vValue != 28) {
            vValue = vValue + 27;
        }

        return "0x" +
                Numeric.toHexStringNoPrefixZeroPadded(new BigInteger(1, sig.getR()), 64) +
                Numeric.toHexStringNoPrefixZeroPadded(new BigInteger(1, sig.getS()), 64) +
                String.format("%02x", vValue);
    }

}
