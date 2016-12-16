# Compact Java Collections

Compact Java collection implementations based on the work
[originally pioneered by PyPy](https://morepypy.blogspot.co.uk/2015/01/faster-more-memory-efficient-and-more.html)
and subsequently [adopted in Python 3.6](https://docs.python.org/3.6/whatsnew/3.6.html#whatsnew36-compactdict).
`CompactSet` and `CompactMap` are drop-in replacements for the standard
library's `LinkedHashSet` and `LinkedHashMap`, respectively, performing
within 5% of the original with only a quarter or a third of the memory
overhead, respectively.
