#!/bin/bash

./jextract \
  --header-class-name nng_h \
  -I "C:/temp/nng-headers" \
  --target-package com.nz.jnng \
  --output "C:/Users/fabocc/Documents/dossier_perso/workspace/J-ngg/src/generated" \
  "C:/temp/nng-headers/nng.h" \
  "C:/temp/nng-headers/protocol/pair0/pair.h" \
  "C:/temp/nng-headers/protocol/pair1/pair.h" \
  "C:/temp/nng-headers/protocol/pubsub0/pub.h" \
  "C:/temp/nng-headers/protocol/pubsub0/sub.h" \
  "C:/temp/nng-headers/protocol/pipeline0/push.h" \
  "C:/temp/nng-headers/protocol/pipeline0/pull.h" \
  "C:/temp/nng-headers/protocol/reqrep0/req.h" \
  "C:/temp/nng-headers/protocol/reqrep0/rep.h"
