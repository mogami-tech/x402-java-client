package tech.mogami.java.client.v2;

import lombok.experimental.UtilityClass;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.payment.PaymentPayload;
import tech.mogami.commons.payment.PaymentRequired;
import tech.mogami.commons.payment.PaymentRequirements;
import tech.mogami.commons.util.Base64Util;
import tech.mogami.commons.util.JsonUtil;
import tech.mogami.commons.util.ValidationUtil;

import java.util.List;
import java.util.Map;

import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_REQUIRED_HEADER;

/**
 * Version 2 of the Mogami Java client.
 * All public APIs in this package are non-null by default.
 */
@UtilityClass
@SuppressWarnings({"HideUtilityClassConstructor", "unused"})
public class X402Client {

    /**
     * Fetches payment requirements from the given headers.
     *
     * @param headers The headers to fetch payment requirements from.
     * @return A list of PaymentRequirements.
     */
    public List<PaymentRequirements> fetchPaymentRequirements(final Map<String, String> headers) {
        // We look for the PAYMENT-REQUIRED header and retrieve the payment requirements encoded there.
        final String encodedPaymentRequired = headers.get(X402_PAYMENT_REQUIRED_HEADER);
        if (encodedPaymentRequired == null) {
            return List.of();
        }

        // We decode it.
        final String decodedPaymentRequired;
        try {
            decodedPaymentRequired = Base64Util.decode(encodedPaymentRequired);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Error during base64 decode for " + X402_PAYMENT_REQUIRED_HEADER + " header", e);
        }

        // We transform the encoded value into a PaymentRequired object.
        final PaymentRequired paymentRequired;
        try {
            paymentRequired = JsonUtil.fromJson(decodedPaymentRequired, PaymentRequired.class);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to decode " + X402_PAYMENT_REQUIRED_HEADER + " header", e);
        }

        // We check if we are working on the right release.
        if (!paymentRequired.isSupportedVersion()) {
            throw new IllegalArgumentException("Unsupported x402 version: " + paymentRequired.x402Version());
        }

        // We check if the JSON data is valid.
        ValidationUtil.findViolations(paymentRequired).stream().findFirst().ifPresent(v -> {
            throw new IllegalArgumentException("Invalid PaymentRequired: " + v.getPropertyPath() + " " + v.getMessage());
        });

        // We return the payment requirements.
        return paymentRequired.accepts();
    }

    /**
     * Creates a PaymentPayload based on the given PaymentRequirements and fromAddress.
     *
     * @param requirements The payment requirements.
     * @param fromAddress  The address from which the payment is made.
     * @return A PaymentPayload object.
     */
    public PaymentPayload createPaymentPayload(final PaymentRequirements requirements,
                                               final String fromAddress) {
        // Implementation goes here
        return null;
    }

    /**
     * Signs the given PaymentPayload using the provided credentials.
     *
     * @param paymentsRequirements The payment requirements.
     * @param paymentPayload       The payment payload to sign.
     * @param credentials          The credentials to use for signing.
     * @return A signed PaymentPayload.
     */
    public PaymentPayload signPaymentPayload(final PaymentRequirements paymentsRequirements,
                                             final PaymentPayload paymentPayload,
                                             final Credentials credentials) {
        // Implementation goes here
        return null;
    }

    /**
     * Builds payment headers from the given signed PaymentPayload.
     *
     * @param signedPaymentPayload The signed payment payload.
     * @return A map of payment headers.
     */
    public Map<String, String> buildPaymentHeaders(final PaymentPayload signedPaymentPayload) {
        // Implementation goes here
        return null;
    }

}
