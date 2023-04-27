#!/usr/bin/env bash
SWAGGER_CODEGEN_VERSION='2.4.15'

rm -rf ./gateapi
docker run \
    -v "$PWD/../gate/swagger/:/tmp/gate" \
    -v "$PWD/gateapi/:/tmp/go/" \
    "swaggerapi/swagger-codegen-cli:${SWAGGER_CODEGEN_VERSION}" generate -i /tmp/gate/swagger.json -l go -o /tmp/go/
