#!/bin/sh
set -eu

mvn -q -pl backend/gateway -Dtest=OpenApiContractValidationTests test
