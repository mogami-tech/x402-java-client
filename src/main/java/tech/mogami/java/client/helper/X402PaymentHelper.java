package tech.mogami.java.client.helper;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.header.payment.PaymentPayload;
import tech.mogami.commons.header.payment.PaymentRequired;
import tech.mogami.commons.header.payment.PaymentRequirements;
import tech.mogami.commons.header.payment.schemes.ExactSchemePayload;
import tech.mogami.commons.util.JsonUtil;
import tech.mogami.commons.util.NonceUtil;

import java.time.Instant;
import java.util.Optional;

import static tech.mogami.commons.constant.X402Constants.X402_SUPPORTED_VERSION;
import static tech.mogami.commons.header.payment.schemes.ExactSchemeConstants.EXACT_SCHEME_NAME;

/**
 * This class provides helper methods for handling X402 payments.
 */
public class X402PaymentHelper {

    /**
     * Parses the X-Payment header and returns a PaymentRequired object.
     *
     * @param xPaymentHeader The value of the X-Payment header.
     * @return A PaymentRequired object containing the parsed payment requirements.
     */
    public static Optional<PaymentRequired> getPaymentRequiredFromHeader(final String xPaymentHeader) {
        if (StringUtils.isEmpty(xPaymentHeader)) {
            // If the header is empty, it means no payment is required.
            return Optional.empty();
        } else {
            return Optional.of(JsonUtil.fromJson(xPaymentHeader, PaymentRequired.class));
        }
    }

    /**
     * Generate a PaymentPayload from a specific PaymentRequirements WITHOUT SIGNATURE.
     *
     * @param signature           The signature of the payment payload.
     * @param fromAddress         The address from which the payment is made.
     * @param paymentRequirements The payment requirements to convert.
     * @return A PaymentPayload object containing the payment details.
     */
    public static PaymentPayload getPayloadFromPaymentRequirements(
            @NonNull String signature,
            @NonNull String fromAddress,
            @NonNull final PaymentRequirements paymentRequirements
    ) {
        if (paymentRequirements.scheme().equals(EXACT_SCHEME_NAME)) {
            return PaymentPayload.builder()
                    .x402Version(X402_SUPPORTED_VERSION)
                    .scheme(EXACT_SCHEME_NAME)
                    .network(paymentRequirements.network())
                    .payload(ExactSchemePayload.builder()
                            .signature(signature)
                            .authorization(ExactSchemePayload.Authorization.builder()
                                    .from(fromAddress)
                                    .to(paymentRequirements.payTo())
                                    .value(paymentRequirements.maxAmountRequired())
                                    .validAfter(Long.toString(Instant.now().getEpochSecond()))
                                    .validBefore(Long.toString(Instant.now().getEpochSecond() + paymentRequirements.maxTimeoutSeconds()))
                                    .nonce(NonceUtil.generateNonce())
                                    .build())
                            .build())
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported payment scheme: " + paymentRequirements.scheme());
        }
    }

    /**
     * Generate a PaymentPayload from a specific PaymentRequirements with signature.
     *
     * @param credentials          the credentials of the user making the payment
     * @param paymentsRequirements the payment requirements to convert
     * @param paymentPayload       the payment payload to sign
     * @return A PaymentPayload object containing the signed payment details.
     */
    @SneakyThrows
    public static PaymentPayload getSignedPayload(
            @NonNull final Credentials credentials,
            @NonNull final PaymentRequirements paymentsRequirements,
            @NonNull final PaymentPayload paymentPayload
    ) {
        // We change the signature in the payload with the one signed by the user.
        ExactSchemePayload payload = ((ExactSchemePayload) paymentPayload.payload()).toBuilder()
                .signature(EIP712Helper.sign(credentials, paymentsRequirements, paymentPayload))
                .build();

        // We return the payment payload with the new payload.
        return paymentPayload.toBuilder()
                .payload(payload)
                .build();
    }

}
