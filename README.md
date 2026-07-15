# C2 branch-to-cmov: bias vs predictability

Benchmarks and disassembly behind the blog post
**[The most expensive instruction might be… cmov](https://questdb.com/blog/cmov-vs-branch-perf/)**.

## Run

Needs a release JDK built with hsdis. Build the jar with any JDK 26+:

```bash
mvn -f pom.xml clean package   # -> target/benchmarks.jar

JDK=/path/to/jdk/bin/java
taskset -c 8 "$JDK" -jar target/benchmarks.jar '.*CMoveBiasBench.*Select' \
  -f 3 -wi 5 -i 8 -w 500ms -r 500ms \
  -prof 'perfnorm:events=cycles,instructions,branches,branch-misses'
```

Force the codegen with `-jvmArgsAppend`:
`-XX:ConditionalMoveLimit=0` (branch) or
`-XX:+UseCMoveUnconditionally -XX:BlockLayoutMinDiamondPercentage=0`.

Source pinned to `openjdk/jdk` @ [`723826295a`](https://github.com/openjdk/jdk/commit/723826295aa50a88cbb702128da79a91e6d87c73).
