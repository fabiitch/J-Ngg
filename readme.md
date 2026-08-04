### init 
git clone https://github.com/nanomsg/nng.git
cd nng
git fetch --tags
git reset --hard v1.12.0

## compile
rm -rf build
mkdir build
cmake -B build -DBUILD_SHARED_LIBS=ON
cmake --build build --config Release
find build -iname "*.dll"


### copy dll and ngg.h in jextract bin folder

New-Item -ItemType Directory -Force -Path C:\temp\nng-headers
Copy-Item `
"C:\Users\fabocc\Documents\dossier_perso\clone-project\nng\include\nng\*" `
"C:\Users\fabocc\Documents\dossier_perso\sdk\jextract-25\bin\nng" `
-Recurse -Force


panama gen

./jextract \
--target-package com.nz.jnng \
nng.h

./jextract -I "C:/temp/nng-headers" --target-package com.nz.jnng --output "C:/Users/fabocc/Documents/dossier_perso/workspace/J-ngg/src/generated" "C:/temp/nng-headers/nng.h" "C:/temp/nng-headers/protocol/pair0/pair.h" "C:/temp/nng-headers/protocol/pair1/pair.h" "C:/temp/nng-headers/protocol/pubsub0/pub.h" "C:/temp/nng-headers/protocol/pubsub0/sub.h" "C:/temp/nng-headers/protocol/pipeline0/push.h" "C:/temp/nng-headers/protocol/pipeline0/pull.h" "C:/temp/nng-headers/protocol/reqrep0/req.h" "C:/temp/nng-headers/protocol/reqrep0/rep.h"
