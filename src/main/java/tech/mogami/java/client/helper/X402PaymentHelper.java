package tech.mogami.java.client.helper;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.api.facilitator.settle.SettleResponse;
import tech.mogami.commons.crypto.signature.EIP712Helper;
import tech.mogami.commons.header.payment.PaymentPayload;
import tech.mogami.commons.header.payment.PaymentRequired;
import tech.mogami.commons.header.payment.PaymentRequirements;
import tech.mogami.commons.header.payment.schemes.exact.ExactSchemePayload;
import tech.mogami.commons.util.Base64Util;
import tech.mogami.commons.util.JsonUtil;
import tech.mogami.commons.util.NonceUtil;

import java.time.Instant;
import java.util.Optional;

import static tech.mogami.commons.constant.version.X402Versions.X402_SUPPORTED_VERSION_BY_MOGAMI;
import static tech.mogami.commons.header.payment.schemes.Schemes.EXACT_SCHEME;

/**
 * This class provides helper methods for handling X402 payments.
 */
@UtilityClass
@SuppressWarnings({"HideUtilityClassConstructor", "unused"})
public class X402PaymentHelper {

    /**
     * Parses the X-Payment body and returns a PaymentRequired object.
     *
     * @param xPaymentHeader The value of the X-Payment header.
     * @return A PaymentRequired object containing the parsed payment requirements.
     */
    public static Optional<PaymentRequired> getPaymentRequiredFromBody(final String xPaymentHeader) {
        return Optional.ofNullable(xPaymentHeader)
                .filter(StringUtils::isNotEmpty)
                .map(header -> JsonUtil.fromJson(header, PaymentRequired.class));
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
            final String signature,
            @NonNull final String fromAddress,
            @NonNull final PaymentRequirements paymentRequirements
    ) {
        if (paymentRequirements.scheme().equals(EXACT_SCHEME.name())) {
            return PaymentPayload.builder()
                    .x402Version(X402_SUPPORTED_VERSION_BY_MOGAMI.version())
                    .scheme(EXACT_SCHEME.name())
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
        // We change the signature field in the payload with the one signed by the user.
        ExactSchemePayload payload = ((ExactSchemePayload) paymentPayload.payload()).toBuilder()
                .signature(EIP712Helper.sign(credentials, paymentsRequirements, paymentPayload))
                .build();

        // We return the payment payload with the new payload.
        return paymentPayload.toBuilder()
                .payload(payload)
                .build();
    }

    /**
     * Encodes the PaymentPayload into a Base64 string to be used as X-PAYMENT header.
     *
     * @param paymentPayload The PaymentPayload to encode.
     * @return A Base64 encoded string representation of the PaymentPayload.
     */
    public static String getPayloadHeader(@NonNull final PaymentPayload paymentPayload) {
        return Base64Util.encode(JsonUtil.toJson(paymentPayload));
    }

    /**
     * Decodes the X-PAYMENT-RESPONSE header and returns a SettleResponse object.
     *
     * @param xPaymentResponseHeader The Base64 encoded X-PAYMENT-RESPONSE header.
     * @return A SettleResponse object if the header is not empty, otherwise null.
     */
    public static Optional<SettleResponse> getSettleResponseFromHeader(final String xPaymentResponseHeader) {
        return Optional.ofNullable(xPaymentResponseHeader)
                .filter(StringUtils::isNotEmpty)
                .map(Base64Util::decode)
                .map(decoded -> JsonUtil.fromJson(decoded, SettleResponse.class));
    }

}
