# Compact Java Collections

Compact Java collection implementations based on the work
[originally pioneered by PyPy](https://morepypy.blogspot.co.uk/2015/01/faster-more-memory-efficient-and-more.html)
and subsequently [adopted in Python 3.6](https://docs.python.org/3.6/whatsnew/3.6.html#whatsnew36-compactdict).
`ArraySet` and `ArrayMap` are drop-in replacements for the standard
library's `LinkedHashSet` and `LinkedHashMap`, respectively, with comparable
performance at around a quarter of the memory overhead.

## Memory usage

The following chart shows the memory used to store sets of various sizes. They are taken with compressed pointers (i.e. <32GB memory); with uncompressed 64-bit pointers the numbers would be more strongly in favour of ArraySet, as it stores fewer pointers.

| Array size | ArraySet | HashSet | LinkedHashSet |
| --- | --- | --- | --- |
| 0 | 32B | 64B | 72B |
| 1 | 32B | 176B | 192B |
| 2–10 | 136B | 208B–464B | 232B–552B |
| 11–15 | 160B | 496B–688B | 592B–816B |
| 16–22 | 216B | 720B–912B | 856B–1.10kB |
| 23–33 | 328B | 944B–1.39kB | 1.14kB–1.66kB |
| 34–49 | 392B | 1.42kB–2.16kB | 1.70kB–2.56kB |
| 50–73 | 616B | 2.19kB–2.93kB | 2.60kB–3.52kB |
| 74–109 | 760B | 2.96kB–4.59kB | 3.56kB–5.47kB |
| 110–163 | 1.23kB | 4.62kB–6.32kB | 5.51kB–7.63kB |
| 164–244 | 1.55kB | 6.35kB–9.94kB | 7.67kB–11.9kB |
| 245–366 | 3.58kB | 9.97kB–13.8kB | 11.9kB–16.8kB |
| 367-549 | 6.36kB | 13.9kB–21.7kB | 16.8kB–26.1kB |
| 550–823 | 7.46kB | 21.8kB–34.6kB | 26.2kB–41.2kB |
| 1K | 13.2kB | 40.3kB | 48.3kB |
| 10K | 121kB | 385kB | 465kB |
| 100K | 1.48MB | 4.25MB | 5.05MB |

[![Maven Central](https://img.shields.io/maven-central/v/org.alicep/collect.svg)](http://mvnrepository.com/artifact/org.alicep/collect)
[![Travis CI](https://travis-ci.org/alicep-org/collect.svg?branch=master)](https://travis-ci.org/alicep-org/collect)
