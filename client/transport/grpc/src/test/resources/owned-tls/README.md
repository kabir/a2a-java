# SDK-owned gRPC TLS test fixture

These public test-only credentials are for a local Netty peer. Never use the private key outside tests.

The self-signed certificate lasts ten years and includes `localhost` and `127.0.0.1` in its subject alternative names. Tests create a temporary PKCS12 truststore from this certificate and install it through the normal `javax.net.ssl.trustStore` system properties, restoring the previous values after closing the channels. Production trust configuration and hostname verification remain active.

Regenerate both files together using OpenSSL:

```sh
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 3650 \
  -subj '/CN=localhost/O=A2A SDK test fixture' \
  -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,digitalSignature,keyEncipherment,keyCertSign' \
  -addext 'extendedKeyUsage=serverAuth' \
  -keyout localhost-key.pem -out localhost-cert.pem
```
