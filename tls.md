# Running with TLS

MongoDB Compass can use `tlsCAFile` and `tlsCertificateKeyFile` directly in a connection string. The Java MongoDB driver does not load those PEM files from the URI. For this app, use the existing PEM files to create Java-compatible PKCS12 stores, then pass those stores to the JVM.

Keep the MongoDB connection string simple:

```text
mongodb://localhost:27017/?directConnection=true&tls=true
```

Do not add `tlsCAFile` or `tlsCertificateKeyFile` to the Java connection string.

## 1. Package the app

```bash
mvn package
```

If you want to run the packaged classes directly with `java`, also write the Maven dependency classpath:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

## 2. Convert the existing PEM files

Set `TLS_DIR` to the directory containing the existing TLS files:

```bash
TLS_DIR=/path/to/community-quick-start/tls
```

The directory should already contain files like:

```text
ca.pem
client-combined.pem
```

Create a Java truststore from the CA certificate:

```bash
keytool -importcert -noprompt \
  -alias local-mongod-ca \
  -file "$TLS_DIR/ca.pem" \
  -keystore "$TLS_DIR/mongo-truststore.p12" \
  -storetype PKCS12 \
  -storepass changeit
```

Create a Java keystore from the client certificate/key bundle:

```bash
openssl pkcs12 -export \
  -in "$TLS_DIR/client-combined.pem" \
  -out "$TLS_DIR/mongo-client.p12" \
  -name mongodb-client \
  -passout pass:changeit
```

## 3. Run the app with TLS

```bash
TLS_DIR=/path/to/community-quick-start/tls
APP_CP="target/classes:$(cat target/classpath.txt)"

CONNECTION_STRING='mongodb://localhost:27017/?directConnection=true&tls=true' \
java \
  -Djavax.net.ssl.trustStore="$TLS_DIR/mongo-truststore.p12" \
  -Djavax.net.ssl.trustStorePassword=changeit \
  -Djavax.net.ssl.trustStoreType=PKCS12 \
  -Djavax.net.ssl.keyStore="$TLS_DIR/mongo-client.p12" \
  -Djavax.net.ssl.keyStorePassword=changeit \
  -Djavax.net.ssl.keyStoreType=PKCS12 \
  -cp "$APP_CP" \
  com.mycodefu.Main --loadDataFromDirectory ./Data
```

To use a different startup mode, replace the final program arguments. For example:

```bash
com.mycodefu.Main --enableUploadsWithLMStudioCaptioning
```

## Troubleshooting

If Java fails with `PKIX path building failed`, it is not using the truststore or the truststore does not contain the CA that signed the MongoDB server certificate.

Check the truststore:

```bash
keytool -list \
  -keystore "$TLS_DIR/mongo-truststore.p12" \
  -storepass changeit \
  -storetype PKCS12
```

Check the client keystore:

```bash
keytool -list \
  -keystore "$TLS_DIR/mongo-client.p12" \
  -storepass changeit \
  -storetype PKCS12
```
