<p align="center">
    <a href="https://mogami.gitbook.io/mogami/java-client/get-started">Quick Start</a> | 
    <a href="https://mogami.gitbook.io/mogami">Documentation</a> | 
    <a href="https://github.com/mogami-tech/x402-examples">Examples</a> | 
    <a href="https://x.com/mogami_tech">Twitter</a>
</p>

<p align="center">
    <a href="https://mogami.gitbook.io/mogami/java-client/get-started">
        <img    src="https://mogami.tech/images/logo/logo_mogami_vertical_small.png"
                alt="Mogami logo"/>
    </a>
</p>

<hr>

<h3 align="center">Mogami Java x402 client - Retrieve x402 payment information and pay!</h2>
<br>

X402 java client provides methods to retrieve payment requirements from an X402 URL, generate a signed payload, and 
send it to the server.
It also allows you to collect the settlement information returned by the server.

Here is how to use it:

```java
// Retrieve the payment required from the response body.
var paymentRequired = X402PaymentHelper.getPaymentRequiredFromBody(result.getResponse().getContentAsString())
        .orElseThrow(() -> new IllegalStateException("Payment requirements not found in response"));

// Generate a payment payload (without a signature) from the payment requirements.
var paymentPayload = X402PaymentHelper.getPayloadFromPaymentRequirements(
        null,
        TEST_CLIENT_WALLET_ADDRESS_1,
        paymentRequired.accepts().getFirst());

// Sign the payment payload.
var signedPayload = X402PaymentHelper.getSignedPayload(
        Credentials.create(TEST_CLIENT_WALLET_ADDRESS_1_PRIVATE_KEY),
                paymentRequired.accepts().getFirst(),
                paymentPayload);

// Get the header to add.
var headerToUse = X402PaymentHelper.getPayloadHeader(signedPayload);
```

<p align="center">
    <a href="https://mogami.gitbook.io/mogami/java-client/get-started">Quick Start</a> | 
    <a href="https://mogami.gitbook.io/mogami">Documentation</a> | 
    <a href="https://github.com/mogami-tech/x402-examples">Examples</a> | 
    <a href="https://x.com/mogami_tech">Twitter</a>
</p>