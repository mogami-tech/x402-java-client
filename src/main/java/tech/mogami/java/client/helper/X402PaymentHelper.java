package tech.mogami.java.client.helper;

import org.apache.commons.lang3.StringUtils;
import tech.mogami.commons.header.payment.PaymentRequired;
import tech.mogami.commons.util.JsonUtil;

import java.util.Optional;

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

}
