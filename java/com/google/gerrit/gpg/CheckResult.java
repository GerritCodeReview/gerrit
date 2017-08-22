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

package com.google.gerrit.gpg;

import com.google.gerrit.extensions.common.GpgKeyInfo.Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Result of checking an object like a key or signature. */
public class CheckResult {
  static CheckResult ok(String... problems) {
    return create(Status.OK, problems);
  }

  static CheckResult bad(String... problems) {
    return create(Status.BAD, problems);
  }

  static CheckResult trusted() {
    return new CheckResult(Status.TRUSTED, Collections.<String>emptyList());
  }

  static CheckResult create(Status status, String... problems) {
    List<String> problemList =
        problems.length > 0
            ? Collections.unmodifiableList(Arrays.asList(problems))
            : Collections.<String>emptyList();
    return new CheckResult(status, problemList);
  }

  static CheckResult create(Status status, List<String> problems) {
    return new CheckResult(status, Collections.unmodifiableList(new ArrayList<>(problems)));
  }

  static CheckResult create(List<String> problems) {
    return new CheckResult(
        problems.isEmpty() ? Status.OK : Status.BAD, Collections.unmodifiableList(problems));
  }

  private final Status status;
  private final List<String> problems;

  private CheckResult(Status status, List<String> problems) {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    this.status = status;
    this.problems = problems;
  }

  /** @return whether the result has status {@link Status#OK} or better. */
  public boolean isOk() {
    return status.compareTo(Status.OK) >= 0;
  }

  /** @return whether the result has status {@link Status#TRUSTED} or better. */
  public boolean isTrusted() {
    return status.compareTo(Status.TRUSTED) >= 0;
  }

  /** @return the status enum value associated with the object. */
  public Status getStatus() {
    return status;
  }

  /** @return any problems encountered during checking. */
  public List<String> getProblems() {
    return problems;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('[').append(status);
    for (int i = 0; i < problems.size(); i++) {
      sb.append(i == 0 ? ": " : ", ").append(problems.get(i));
    }
    return sb.append(']').toString();
  }
}
