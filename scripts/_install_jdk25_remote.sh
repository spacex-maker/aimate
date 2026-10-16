#!/usr/bin/env bash
set -euo pipefail
URL="https://repo.huaweicloud.com/openjdk/25/openjdk-25_linux-x64_bin.tar.gz"
if [ -x /home/ubuntu/jdk25/bin/java ]; then
  /home/ubuntu/jdk25/bin/java -version
  exit 0
fi
echo "DOWNLOADING $URL"
curl -L --retry 3 "$URL" -o /tmp/jdk25.tar.gz
ls -lh /tmp/jdk25.tar.gz
rm -rf /home/ubuntu/jdk25
mkdir -p /home/ubuntu/jdk25
tar -xzf /tmp/jdk25.tar.gz -C /home/ubuntu/jdk25 --strip-components=1
/home/ubuntu/jdk25/bin/java -version
rm -f /tmp/jdk25.tar.gz
echo JDK_OK
