# Why use AutoValue?


In versions of Java preceding
[records](https://docs.oracle.com/en/java/javase/16/language/records.html),
AutoValue is the only solution to the value class problem having all of the
following characteristics:

*   **API-invisible** (callers cannot become dependent on your choice to use it)
*   No runtime dependencies
*   Negligible cost to performance
*   Very few limitations on what your class can do
*   Extralinguistic "magic" kept to an absolute minimum (uses only standard Java
    platform technologies, in the manner they were intended)

This
[slide presentation] compares AutoValue to numerous alternatives and explains
why we think it is better.

[slide presentation]: https://docs.google.com/presentation/d/14u_h-lMn7f1rXE1nDiLX0azS3IkgjGl5uxp5jGJ75RE/edit
