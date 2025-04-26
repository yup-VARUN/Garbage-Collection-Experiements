#!/usr/bin/env bash
set -euo pipefail  # fail on error, undefined vars, or pipeline errors

# 1) Configure a larger heap to avoid OOME
HEAP="-Xms2g -Xmx2g"

# 2) Where to store results
LOGDIR="gc-logs"
mkdir -p "$LOGDIR"

# 3) Unified GC logging flags: write to file (%s placeholder)
LOGFLAGS="-Xlog:gc*,gc+heap=debug:file=%s:time,uptime,level,tags"

# 4) Compile Java benchmark if needed
javac GcBenchmark.java  # Compile Always
# Compile if not compiled:
# if [[ ! -f GcBenchmark.class ]]; then
#   echo "Compiling GcBenchmark.java..."
#   javac GcBenchmark.java
# fi

# 5) Test runner: name + JVM GC flag
run_test() {
  local name="$1"    # e.g. "parallel" or "g1"
  local gcflag="$2"  # e.g. "-XX:+UseParallelGC"

  # 5a) Prepare logfile path via printf
  local logfile
  logfile=$(printf -- "$LOGFLAGS" "$LOGDIR/gc-$name.log")

  echo "=== Running $name GC ==="

  # 5b) Launch JVM, point to current dir (-cp .), enable GC logging
  java -cp . $HEAP $gcflag $logfile GcBenchmark \
    > "$LOGDIR/result-$name.txt" 2>&1

  echo "-> Output: $LOGDIR/result-$name.txt"
  echo "-> GC log:  $LOGDIR/gc-$name.log"
  echo
}

# 6) Run with Parallel GC
run_test "parallel" "-XX:+UseParallelGC"

# 7) Run with G1 GC
run_test "g1" "-XX:+UseG1GC -XX:MaxGCPauseMillis=30"

echo "All GC tests complete."
