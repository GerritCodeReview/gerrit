// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.git.gpg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Result of checking an object like a key or signature. */
public class CheckResult {
  private final List<String> problems;

  CheckResult(String... problems) {
    this(Arrays.asList(problems));
  }

  CheckResult(List<String> problems) {
    this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
  }

  /**
   * @return whether the result is entirely ok, i.e. has passed any verification
   *     or validation checks.
   */
  public boolean isOk() {
    return problems.isEmpty();
  }

  /** @return any problems encountered during checking. */
  public List<String> getProblems() {
    return problems;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(getClass().getSimpleName())
        .append('[');
    for (int i = 0; i < problems.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(problems.get(i));
    }
    return sb.append(']').toString();
  }
}
