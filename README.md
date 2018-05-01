# Compact Java Collections

Compact Java collection implementations based on the work
[originally pioneered by PyPy](https://morepypy.blogspot.co.uk/2015/01/faster-more-memory-efficient-and-more.html)
and subsequently [adopted in Python 3.6](https://docs.python.org/3.6/whatsnew/3.6.html#whatsnew36-compactdict).
`ArraySet` and `ArrayMap` are drop-in replacements for the standard
library's `LinkedHashSet` and `LinkedHashMap`, respectively, with comparable
performance at around a quarter of the memory overhead.

[![Maven Central](https://img.shields.io/maven-central/v/org.alicep/collect.svg)](http://mvnrepository.com/artifact/org.alicep/collect)
[![Travis CI](https://travis-ci.org/alicep-org/collect.svg?branch=master)](https://travis-ci.org/alicep-org/collect)

## Memory usage

### Compressed OOPs

The following chart shows the memory used to store sets of various sizes, in a JVM with compressed pointers (i.e. <32GB memory). Also shown is Koloboke's [ObjSet], an unsorted hashmap that is a drop-in replacement for HashSet, and ArrayList for a lower-bound on array sizing.

| Collection size | ArraySet |    HashSet    | LinkedHashSet | ArrayList |   [ObjSet]    |
| --------------- | -------- | ------------- | ------------- | --------- | ------------- |
|       0–1       |    32B   |    64B–176B   |    72B–192B   |  24B–80B  |      112B     |
|       2–8       |   120B   |   208B–400B   |   232B–472B   |    80B    |      112B     |
|       9–10      |   136B   |   432B–464B   |   512B–552B   |    80B    |      112B     |
|      11–15      |   160B   |   496B–688B   |   592B–816B   |    104B   |      176B     |
|        16       |   184B   |      720B     |      856B     |    128B   |      176B     |
|      17–22      |   216B   |   752B–912B   |  896B–1.10kB  |    128B   |   176B–304B   |
|      23–32      |   264B   |  944B–1.36kB  | 1.14kB–1.62kB |    176B   |      304B     |
|        33       |   328B   |     1.39kB    |     1.66kB    |    176B   |      304B     |
|      34–49      |   392B   | 1.42kB–2.16kB | 1.70kB–2.56kB |    240B   |   304B–560B   |
|      50–64      |   488B   | 2.19kB–2.64kB | 2.60kB–3.16kB |    336B   |      560B     |
|      65–73      |   616B   | 2.67kB–2.93kB | 3.20kB–3.52kB |    336B   |      560B     |
|      74–109     |   760B   | 2.96kB–4.59kB | 3.56kB–5.47kB |    480B   |  560B–1.07kB  |
|     110–128     |   976B   | 4.62kB–5.20kB | 5.51kB–6.23kB |    696B   |     1.07kB    |
|     129–163     |  1.23kB  | 5.23kB–6.32kB | 6.27kB–7.63kB |    696B   |     1.07kB    |
|     164–244     |  1.55kB  | 6.35kB–9.94kB | 7.67kB–11.9kB |   1.02kB  | 1.07kB–2.10kB |
|     245–255     |  2.04kB  | 9.97kB–10.3kB | 11.9kB–12.3kB |   1.50kB  |     2.10kB    |
|     256–366     |  3.58kB  | 10.3kB–13.8kB | 12.4kB–16.8kB |   1.50kB  | 2.10kB–4.14kB |
|     367–512     |  4.31kB  | 13.9kB–20.6kB | 16.8kB–24.7kB |   2.24kB  |     4.14kB    |
|     513–549     |  6.36kB  | 20.6kB–21.7kB | 24.7kB–26.1kB |   2.24kB  |     4.14kB    |
|     550–823     |  7.46kB  | 21.8kB–34.6kB | 26.2kB–41.2kB |   3.34kB  | 4.14kB–8.24kB |
|      824–1k     |  9.10kB  | 34.6kB–40.3kB | 41.2kB–48.3kB |   4.98kB  |     8.24kB    |
|       10k       |   121kB  |     385kB     |     465kB     |   56.3kB  |     65.6kB    |
|       100k      |  1.48MB  |     4.25MB    |     5.05MB    |   426kB   |     1.05MB    |

### 64-bit OOPs

On 32GB+ heaps, pointers consume more space, and so ArraySet, which uses a single, small object array, does not grow in space use as badly as the other set implementations.

| Collection size | ArraySet |    HashSet    | LinkedHashSet | ArrayList |   [ObjSet]    |
| --------------- | -------- | ------------- | ------------- | --------- | ------------- |
|       0–1       |    40B   |    88B–288B   |   112B–328B   |  40B–144B |      200B     |
|       2–8       |   184B   |   336B–624B   |   392B–776B   |    144B   |      200B     |
|       9–10      |   200B   |   672B–720B   |   840B–904B   |    144B   |      200B     |
|      11–15      |   240B   |  768B–1.09kB  |  968B–1.35kB  |    184B   |      328B     |
|        16       |   296B   |     1.14kB    |     1.42kB    |    240B   |      328B     |
|      17–22      |   328B   | 1.18kB–1.42kB | 1.48kB–1.80kB |    240B   |   328B–584B   |
|      23–32      |   416B   | 1.47kB–2.16kB | 1.86kB–2.70kB |    328B   |      584B     |
|        33       |   480B   |     2.21kB    |     2.76kB    |    328B   |      584B     |
|      34–49      |   608B   | 2.26kB–3.49kB | 2.82kB–4.30kB |    456B   |  584B–1.10kB  |
|      50–64      |   800B   | 3.54kB–4.21kB | 4.36kB–5.26kB |    648B   |     1.10kB    |
|      65–73      |   928B   | 4.26kB–4.64kB | 5.32kB–5.83kB |    648B   |     1.10kB    |
|      74–109     |  1.22kB  | 4.69kB–7.39kB | 5.90kB–9.16kB |    936B   | 1.10kB–2.12kB |
|     110–128     |  1.65kB  | 7.44kB–8.30kB | 9.22kB–10.4kB |   1.37kB  |     2.12kB    |
|     129–163     |  1.90kB  | 8.35kB–9.98kB | 10.4kB–12.6kB |   1.37kB  |     2.12kB    |
|     164–244     |  2.55kB  | 10.0kB–15.9kB | 12.7kB–19.8kB |   2.02kB  | 2.12kB–4.17kB |
|     245–255     |  3.53kB  | 16.0kB–16.4kB | 19.9kB–20.6kB |   2.99kB  |     4.17kB    |
|     256–366     |  5.06kB  | 16.5kB–21.8kB | 20.6kB–27.7kB |   2.99kB  | 4.17kB–8.26kB |
|     367–512     |  6.53kB  | 21.8kB–32.9kB | 27.7kB–41.1kB |   4.46kB  |     8.26kB    |
|     513–549     |  8.58kB  | 32.9kB–34.7kB | 41.2kB–43.5kB |   4.46kB  |     8.26kB    |
|     550–823     |  10.8kB  | 34.7kB–56.0kB | 43.5kB–69.2kB |   6.65kB  | 8.26kB–16.5kB |
|      824–1k     |  14.1kB  | 56.0kB–64.5kB | 69.3kB–80.5kB |   9.94kB  |     16.5kB    |
|       10k       |   178kB  |     611kB     |     771kB     |   112kB   |     131kB     |
|       100k      |  1.90MB  |     6.90MB    |     8.50MB    |   853kB   |     2.10MB    |

[ObjSet]: https://github.com/leventov/Koloboke
