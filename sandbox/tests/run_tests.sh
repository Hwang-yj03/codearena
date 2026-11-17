#!/usr/bin/env bash
# runner가 OK / TLE / MLE / RE 를 올바르게 판정하는지 확인
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
g++ -O2 -Wall -Wextra -std=c++17 src/runner.cpp -o build/runner

pass=0; fail=0
check() {
  local name=$1 expected=$2 mem=${3:-256}
  g++ -O2 -std=c++17 "tests/$name.cpp" -o "build/tests/$name"
  echo "3 4" > build/tests/in.txt
  local out
  out=$(build/runner --time 1000 --memory "$mem" --input build/tests/in.txt \
        --output build/tests/out.txt -- "build/tests/$name")
  if [[ "$out" == *"\"status\":\"$expected\""* ]]; then
    echo "PASS  $name -> $out"; pass=$((pass+1))
  else
    echo "FAIL  $name (expected $expected) -> $out"; fail=$((fail+1))
  fi
}

check ok    OK
check tle   TLE
check mle   MLE 256
check re    RE
check sleep TLE

echo "---"
echo "passed=$pass failed=$fail"
[[ $fail -eq 0 ]]
