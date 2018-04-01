# Compact Java Collections

Compact Java collection implementations based on the work
[originally pioneered by PyPy](https://morepypy.blogspot.co.uk/2015/01/faster-more-memory-efficient-and-more.html)
and subsequently [adopted in Python 3.6](https://docs.python.org/3.6/whatsnew/3.6.html#whatsnew36-compactdict).
`ArraySet` and `ArrayMap` are drop-in replacements for the standard
library's `LinkedHashSet` and `LinkedHashMap`, respectively, with comparable
performance at under half of the memory overhead.

| Array size | ArraySet | HashSet | LinkedHashSet |
| --- | --- | --- | --- |
| 0 | 32B | 64B | 72B |
| 1 | 32B | 176B | 192B |
| 2–10 | 136B | 208B–464B | 232B–552B |
| 11–15 | 264B | 496B–768B | 592B–976B |
| 16–22 | 448B | 800B–992B | 936B–1.2KB |
| 23–33 | 744B | 1.0KB–1.6KB | 1.2KB–1.9KB |
| 34–49 | 1.1KB | 1.7KB–2.7KB | 1.9KB–3.1KB |
| 50–73 | 1.7KB | 2.7KB–3.4KB | 3.1KB–42.0KB |
| 74–109 | 2.4KB | 3.5KB–5.6KB | 4.1KB–6.5KB |
| 110–163 | 3.6KB | 5.7KB–7.4KB | 6.5KB–8.7KB |
| 164–244 | 5.2KB | 7.4KB–12.0KB | 8.7KB–14KB |
| 245–366 | 10.8KB | 12.0KB–15.9KB | 14.0KB–18.9KB |
| 100K | 5.2MB | 5.6MB | 6.4MB |

[![Maven Central](https://img.shields.io/maven-central/v/org.alicep/collect.svg)](http://mvnrepository.com/artifact/org.alicep/collect)
[![Travis CI](https://travis-ci.org/alicep-org/collect.svg?branch=master)](https://travis-ci.org/alicep-org/collect)
