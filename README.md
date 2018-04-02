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

The following chart shows the memory used to store sets of various sizes. They are taken with compressed pointers (i.e. <32GB memory); with uncompressed 64-bit pointers the numbers would be more strongly in favour of ArraySet, as it stores fewer pointers. Also shown is Koloboke's [ObjSet], an unsorted hashmap that is a drop-in replacement for HashSet, and ArrayList for a lower-bound on array sizing.

| Collection size | ArraySet |    HashSet    | LinkedHashSet | ArrayList |   [ObjSet]    |
| --------------- | -------- | ------------- | ------------- | --------- | ------------- |
|       0–1       |    32B   |    64B—176B   |    72B—192B   |  24B—80B  |      112B     |
|       2–8       |   120B   |   208B—400B   |   232B—472B   |    80B    |      112B     |
|       9–10      |   136B   |   432B—464B   |   512B—552B   |    80B    |      112B     |
|      11–15      |   160B   |   496B—688B   |   592B—816B   |    104B   |      176B     |
|        16       |   184B   |      720B     |      856B     |    128B   |      176B     |
|      17–22      |   216B   |   752B—912B   |  896B—1.10kB  |    128B   |   176B—304B   |
|      23–32      |   264B   |  944B—1.36kB  | 1.14kB—1.62kB |    176B   |      304B     |
|        33       |   328B   |     1.39kB    |     1.66kB    |    176B   |      304B     |
|      34–49      |   392B   | 1.42kB—2.16kB | 1.70kB—2.56kB |    240B   |   304B—560B   |
|      50–64      |   488B   | 2.19kB—2.64kB | 2.60kB—3.16kB |    336B   |      560B     |
|      65–73      |   616B   | 2.67kB—2.93kB | 3.20kB—3.52kB |    336B   |      560B     |
|      74–109     |   760B   | 2.96kB—4.59kB | 3.56kB—5.47kB |    480B   |  560B—1.07kB  |
|     110–128     |   976B   | 4.62kB—5.20kB | 5.51kB—6.23kB |    696B   |     1.07kB    |
|     129–163     |  1.23kB  | 5.23kB—6.32kB | 6.27kB—7.63kB |    696B   |     1.07kB    |
|     164–244     |  1.55kB  | 6.35kB—9.94kB | 7.67kB—11.9kB |   1.02kB  | 1.07kB—2.10kB |
|     245–255     |  2.04kB  | 9.97kB—10.3kB | 11.9kB—12.3kB |   1.50kB  |     2.10kB    |
|     256–366     |  3.58kB  | 10.3kB—13.8kB | 12.4kB—16.8kB |   1.50kB  | 2.10kB—4.14kB |
|     367–512     |  4.31kB  | 13.9kB—20.6kB | 16.8kB—24.7kB |   2.24kB  |     4.14kB    |
|     513–549     |  6.36kB  | 20.6kB—21.7kB | 24.7kB—26.1kB |   2.24kB  |     4.14kB    |
|     550–823     |  7.46kB  | 21.8kB—34.6kB | 26.2kB—41.2kB |   3.34kB  | 4.14kB—8.24kB |
|      824–1k     |  9.10kB  | 34.6kB—40.3kB | 41.2kB—48.3kB |   4.98kB  |     8.24kB    |
|       10k       |   121kB  |     385kB     |     465kB     |   56.3kB  |     65.6kB    |
|       100k      |  1.48MB  |     4.25MB    |     5.05MB    |   426kB   |     1.05MB    |

[ObjSet]: https://github.com/leventov/Koloboke
