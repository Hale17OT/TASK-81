#!/bin/bash
set -e

KEYSTORE_FILE="keystore.p12"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-changeit}"
ALIAS="campusstore"
VALIDITY=3650

if [ -f "$KEYSTORE_FILE" ]; then
    echo "Keystore '$KEYSTORE_FILE' already exists. Skipping generation."
    exit 0
fi

echo "Generating self-signed keystore: $KEYSTORE_FILE"

keytool -genkeypair \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -storetype PKCS12 \
    -keystore "$KEYSTORE_FILE" \
    -storepass "$KEYSTORE_PASSWORD" \
    -validity "$VALIDITY" \
    -dname "CN=localhost, OU=CampusStore, O=CampusStore, L=Unknown, ST=Unknown, C=US"

echo "Keystore generated successfully: $KEYSTORE_FILE"
