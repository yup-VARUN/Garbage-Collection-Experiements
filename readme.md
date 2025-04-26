# Garbage First Collection or G1 GC is compared with a simple Parallel GC algorithm in Java:
These are both supported out of the box in Java, so I only had to write a benchmark program for bhoth algorithms to perform in and which also logs the runtime to finish.

**Hence:**
The shell script `run_gc_tests.sh` runs GcBenchmark.java program under both the collectors and we can then compare runtime from both.

## `GcBenchmark.java`:
- ShortLivedThread churns many small objects (stress on young-gen copy).
- LongLivedThread slowly populates a list (old-gen pressure) and rarely prunes it.
- After 30 s warm-up, both run for 120 s and report per-second allocation rates.

## Setup Instructions:
Make sure you've the java extensions package from microsoft, and you run the following commands in the terminal(bash):
1. `chmod +x run_gc_tests.sh` to give the shell script file execution permissions.
2. Done! Now you can run `./run_gc_tests.sh` in your terminal and wait for the results!

----------------------------------------

Feel free to play around