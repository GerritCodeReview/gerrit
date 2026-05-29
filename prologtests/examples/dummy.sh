#!/bin/bash

# Skip all prolog tests for newer Java versions.
# See https://github.com/bazelbuild/bazel/issues/9391
# for more details why we cannot support running tests
# on newer Java versions for now.
