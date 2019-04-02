#!/bin/bash

TESTS="t1 t2 t3"

# Note that both t1.pl and t2.pl test code in rules.pl.
# Unit tests are usually longer than the tested code.
# So it is common to test one source file with multiple
# unit test files.

for T in $TESTS
do
  # Unit tests do not need to define clauses in packages.
  # Use one prolog-shell per unit test, to avoid name collision.
  echo "[$T]." | java -jar ../../bazel-bin/gerrit.war prolog-shell -q -s load.pl

  # java -jar ../../bazel-bin/gerrit.war prolog-shell -s $T < /dev/null
  # Calling prolog-shell with -s flag works for small files,
  # but got run-time exception with t3.pl.
  #   com.googlecode.prolog_cafe.exceptions.ReductionLimitException:
  #   exceeded reduction limit of 1048576
done
