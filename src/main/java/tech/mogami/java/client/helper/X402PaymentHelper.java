package tech.mogami.java.client.helper;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import tech.mogami.commons.constant.X402Constants;
import tech.mogami.commons.header.payment.PaymentPayload;
import tech.mogami.commons.header.payment.PaymentRequired;
import tech.mogami.commons.header.payment.PaymentRequirements;
import tech.mogami.commons.header.payment.schemes.ExactSchemePayload;
import tech.mogami.commons.util.JsonUtil;
import tech.mogami.commons.util.NonceUtil;
import tech.mogami.commons.validator.BlockchainAddress;

import java.time.Instant;
import java.util.Optional;

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
     * Generate a PaymentPayload from a specific PaymentRequirements.
     * Supports only the "exact" scheme at the moment.
     *
     * @param fromAddress The address from which the payment is made.
     * @param paymentRequirements The payment requirements to convert.
     * @return A PaymentPayload object containing the payment details.
     */
    public static PaymentPayload getPayloadFromPaymentRequirements(
            @NonNull String fromAddress,
            @NonNull final PaymentRequirements paymentRequirements
            ) {
        if (paymentRequirements.scheme().equals(EXACT_SCHEME_NAME)) {
            return PaymentPayload.builder()
                    .x402Version(X402Constants.X402_SUPPORTED_VERSION)
                    .scheme(EXACT_SCHEME_NAME)
                    .network(paymentRequirements.network())
                    .payload(ExactSchemePayload.builder()
                            .signature(null)
                            .authorization(ExactSchemePayload.Authorization.builder()
                                    .from(fromAddress)
                                    .to(paymentRequirements.payTo())
                                    .value(paymentRequirements.maxAmountRequired())
                                    .validAfter(Long.toString(Instant.now().getEpochSecond()))
                                    .validBefore(Long.toString(Instant.now().getEpochSecond() + paymentRequirements.maxTimeoutSeconds()))
                                    .nonce(NonceUtil.generateNonce())
                                    .build())
                            .build()
                    ).build();
        } else {
            throw new IllegalArgumentException("Unsupported payment scheme: " + paymentRequirements.scheme());
        }
    }

}
