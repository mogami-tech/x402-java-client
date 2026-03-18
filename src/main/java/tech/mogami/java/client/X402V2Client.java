package tech.mogami.java.client;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.web3j.crypto.Credentials;
import tech.mogami.commons.api.facilitator.settle.SettlementResponse;
import tech.mogami.commons.crypto.signature.EIP712Helper;
import tech.mogami.commons.exception.InvalidX402HeaderException;
import tech.mogami.commons.payment.PaymentPayload;
import tech.mogami.commons.payment.PaymentRequired;
import tech.mogami.commons.payment.PaymentRequirements;
import tech.mogami.commons.payment.extensions.bazaar.BazaarExtension;
import tech.mogami.commons.payment.schemes.exact.ExactSchemePayload;
import tech.mogami.commons.util.JsonUtil;
import tech.mogami.commons.util.NonceUtil;
import tech.mogami.commons.util.X402HeaderUtil;

import java.time.Instant;
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
     * Extract the PaymentRequired from the given headers.
     *
     * @param headers The headers to fetch the PaymentRequired from.
     * @return An Optional containing the PaymentRequired if present.
     * @throws InvalidX402HeaderException if the extracted PaymentRequired has an unsupported x402 version.
     */
    public Optional<PaymentRequired> extractPaymentRequired(final Map<String, String> headers) {
        return Optional.ofNullable(getHeaderIgnoreCase(headers, X402_PAYMENT_REQUIRED_HEADER))
                .map(X402HeaderUtil::decodePaymentRequired)
                .map(paymentRequired -> {
                    if (!paymentRequired.isSupportedVersion()) {
                        throw new InvalidX402HeaderException(
                                "Unsupported x402 version: " + paymentRequired.x402Version()
                        );
                    }
                    return paymentRequired;
                });
    }

    /**
     * Build a signed payment payload based on the given PaymentRequirements and fromAddress.
     *
     * @param paymentRequired             The payment required.
     * @param paymentRequirementsSelected The payment requirements.
     * @param credentials                 The credentials to derive the fromAddress.
     * @return A PaymentPayload object.
     */
    @SneakyThrows
    public PaymentPayload buildPaymentPayload(final PaymentRequired paymentRequired,
                                              final PaymentRequirements paymentRequirementsSelected,
                                              final Credentials credentials) {
        if (StringUtils.equalsIgnoreCase(paymentRequirementsSelected.scheme(), EXACT_SCHEME.name())) {
            // Creates an exactSchemePayloadAuthorization.
            long now = Instant.now().getEpochSecond();
            long validBefore = now + paymentRequirementsSelected.maxTimeoutSeconds();
            ExactSchemePayload.Authorization exactSchemePayloadAuthorization = ExactSchemePayload.Authorization.builder()
                    .from(credentials.getAddress())
                    .to(paymentRequirementsSelected.payTo())
                    .value(paymentRequirementsSelected.amount())
                    .validAfter(Long.toString(now))
                    .validBefore(Long.toString(validBefore))
                    .nonce(NonceUtil.generateNonce())
                    .build();

            // Returns the object.
            return PaymentPayload.builder()
                    .x402Version(X402_SUPPORTED_VERSION_BY_MOGAMI.version())
                    .resource(paymentRequired.resource())
                    .accepted(paymentRequirementsSelected)
                    .payload(ExactSchemePayload.builder()
                            .signature(EIP712Helper.sign(credentials, paymentRequirementsSelected, exactSchemePayloadAuthorization))
                            .authorization(exactSchemePayloadAuthorization)
                            .build())
                    .extensions(ObjectUtils.firstNonNull(paymentRequired.extensions(), Map.of()))
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported payment scheme: " + paymentRequirementsSelected.scheme());
        }
    }

    /**
     * Builds a payment header from the given signed PaymentPayload.
     *
     * @param signedPaymentPayload The signed payment payload.
     * @return The payment header as a String.
     */
    public String buildPaymentHeader(final PaymentPayload signedPaymentPayload) {
        return X402HeaderUtil.encodePaymentPayload(signedPaymentPayload);
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
                buildPaymentHeader(signedPaymentPayload)
        );
    }

    /**
     * Extract the BazaarExtension from the given PaymentRequired.
     *
     * @param paymentRequired The PaymentRequired containing potential bazaar extension data.
     * @return An Optional containing the BazaarExtension if present.
     */
    public Optional<BazaarExtension> extractBazaarExtension(final PaymentRequired paymentRequired) {
        Map<String, Object> extensions = paymentRequired.extensions();
        if (extensions == null || !extensions.containsKey("bazaar")) {
            return Optional.empty();
        }
        return Optional.ofNullable(JsonUtil.convertValue(extensions.get("bazaar"), BazaarExtension.class));
    }

    /**
     * Extract the SettlementResponse from the given headers.
     *
     * @param headers The headers to retrieve the SettlementResponse from.
     * @return An Optional containing the SettlementResponse if present.
     */
    public Optional<SettlementResponse> extractSettlementResponse(final Map<String, String> headers) {
        return Optional.ofNullable(getHeaderIgnoreCase(headers, X402_PAYMENT_RESPONSE_HEADER))
                .map(X402HeaderUtil::decodeSettlementResponse);
    }

    /**
     * Retrieves a header value from the headers map in a case-insensitive manner.
     *
     * @param headers    The map of headers.
     * @param headerName The name of the header to retrieve.
     * @return The header value if found, otherwise null.
     */
    private @Nullable String getHeaderIgnoreCase(@Nullable final Map<String, String> headers,
                                                 @Nullable final String headerName) {
        if (headers == null || headerName == null) {
            return null;
        }
        return new CaseInsensitiveMap<>(headers).get(headerName);
    }

}
