#!/bin/bash
set -euo pipefail

JEXTRACT_BIN="${JEXTRACT_BIN:-jextract}"
NNG_INCLUDE_DIR="${NNG_INCLUDE_DIR:-third_party/nng/include/nng}"
OUT_DIR="${OUT_DIR:-src/generated}"

"$JEXTRACT_BIN" \
  --header-class-name nng_h \
  -I "$NNG_INCLUDE_DIR" \
  --target-package com.nz.jnng \
  --output "$OUT_DIR" \
  "$NNG_INCLUDE_DIR/nng.h" \
  "$NNG_INCLUDE_DIR/protocol/pair0/pair.h" \
  "$NNG_INCLUDE_DIR/protocol/pair1/pair.h" \
  "$NNG_INCLUDE_DIR/protocol/pubsub0/pub.h" \
  "$NNG_INCLUDE_DIR/protocol/pubsub0/sub.h" \
  "$NNG_INCLUDE_DIR/protocol/pipeline0/push.h" \
  "$NNG_INCLUDE_DIR/protocol/pipeline0/pull.h" \
  "$NNG_INCLUDE_DIR/protocol/reqrep0/req.h" \
  "$NNG_INCLUDE_DIR/protocol/reqrep0/rep.h"
