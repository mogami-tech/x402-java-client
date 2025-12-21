package tech.mogami.java.client;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.Strings;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.crypto.signature.EIP712Helper;
import tech.mogami.commons.payment.PaymentPayload;
import tech.mogami.commons.payment.PaymentRequired;
import tech.mogami.commons.payment.PaymentRequirements;
import tech.mogami.commons.payment.SettlementResponse;
import tech.mogami.commons.payment.schemes.exact.ExactSchemePayload;
import tech.mogami.commons.util.Base64Util;
import tech.mogami.commons.util.JsonUtil;
import tech.mogami.commons.util.NonceUtil;
import tech.mogami.commons.util.ValidationUtil;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_REQUIRED_HEADER;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_RESPONSE_HEADER;
import static tech.mogami.commons.constant.X402Constants.X402_PAYMENT_SIGNATURE_HEADER;
import static tech.mogami.commons.constant.version.X402Versions.X402_SUPPORTED_VERSION_BY_MOGAMI;
import static tech.mogami.commons.payment.schemes.Schemes.EXACT_SCHEME;

/**
 * Mogami Java client X402 V2.
 * All public APIs in this package are non-null by default.
 */
@UtilityClass
@SuppressWarnings({"HideUtilityClassConstructor", "unused"})
public class X402V2Client {

    /**
     * Fetches payment requirements from the given headers.
     *
     * @param headers The headers to fetch payment requirements from.
     * @return A list of PaymentRequirements.
     */
    public List<PaymentRequirements> fetchPaymentRequirements(final Map<String, String> headers) {
        // We look for the PAYMENT-REQUIRED header and retrieve the payment requirements encoded there.
        final String encodedPaymentRequired = new CaseInsensitiveMap<>(headers).get(X402_PAYMENT_REQUIRED_HEADER);
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
     * @param paymentRequirements The payment requirements.
     * @param fromAddress         The address from which the payment is made.
     * @return A PaymentPayload object.
     */
    public PaymentPayload createPaymentPayload(final PaymentRequirements paymentRequirements,
                                               final String fromAddress) {
        if (Strings.CI.equals(paymentRequirements.scheme(), EXACT_SCHEME.name())) {
            long now = Instant.now().getEpochSecond();
            return PaymentPayload.builder()
                    .x402Version(X402_SUPPORTED_VERSION_BY_MOGAMI.version())
                    .resource(null)
                    .accepted(paymentRequirements)
                    .payload(ExactSchemePayload.builder()
                            .signature(null)
                            .authorization(ExactSchemePayload.Authorization.builder()
                                    .from(fromAddress)
                                    .to(paymentRequirements.payTo())
                                    .value(paymentRequirements.amount())
                                    .validAfter(Long.toString(now))
                                    .validBefore(Long.toString(now + paymentRequirements.maxTimeoutSeconds()))
                                    .nonce(NonceUtil.generateNonce())
                                    .build())
                            .build())
                    .extensions(Map.of())
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported payment scheme: " + paymentRequirements.scheme());
        }
    }

    /**
     * Signs the given PaymentPayload using the provided credentials.
     *
     * @param paymentsRequirements The payment requirements.
     * @param paymentPayload       The payment payload to sign.
     * @param credentials          The credentials to use for signing.
     * @return A signed PaymentPayload.
     */
    @SneakyThrows
    public PaymentPayload signPaymentPayload(final PaymentRequirements paymentsRequirements,
                                             final PaymentPayload paymentPayload,
                                             final Credentials credentials) {
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
     * Builds payment headers from the given signed PaymentPayload.
     *
     * @param signedPaymentPayload The signed payment payload.
     * @return A map of payment headers.
     */
    public Map<String, String> buildPaymentHeaders(final PaymentPayload signedPaymentPayload) {
        return Map.of(
                X402_PAYMENT_SIGNATURE_HEADER,
                Base64Util.encode(JsonUtil.toJson(signedPaymentPayload))
        );
    }

    /**
     * Retrieves the SettlementResponse from the given headers.
     *
     * @param headers The headers to retrieve the SettlementResponse from.
     * @return An Optional containing the SettlementResponse if present.
     */
    public Optional<SettlementResponse> fetchSettlementResponse(final Map<String, String> headers) {
        // We look for the PAYMENT-RESPONSE header and retrieve the settlement response encoded there.
        final String encodedPaymentResponse = new CaseInsensitiveMap<>(headers).get(X402_PAYMENT_RESPONSE_HEADER);
        if (encodedPaymentResponse == null) {
            return Optional.empty();
        }

        // We decode it.
        final String decodedPaymentResponse;
        try {
            decodedPaymentResponse = Base64Util.decode(encodedPaymentResponse);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Error during base64 decode for payment-response header", e);
        }

        // We transform the encoded value into a SettlementResponse object.
        final SettlementResponse settlementResponse;
        try {
            settlementResponse = JsonUtil.fromJson(decodedPaymentResponse, SettlementResponse.class);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to decode payment-response header", e);
        }

        return Optional.of(settlementResponse);
    }

}
