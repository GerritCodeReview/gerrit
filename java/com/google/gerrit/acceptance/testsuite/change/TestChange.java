// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Change;

/** Representation of a change used for testing purposes. */
@AutoValue
public abstract class TestChange {

  /**
   * The numeric change ID, sometimes also called change number or legacy change ID. Unique per
   * host.
   */
  public abstract Change.Id numericChangeId();

  /**
   * The Change-Id as specified in the commit message. Consists of an {@code I} followed by a 40-hex
   * string. Only unique per project-branch.
   */
  public abstract String changeId();

  static Builder builder() {
    return new AutoValue_TestChange.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder numericChangeId(Change.Id numericChangeId);

    abstract Builder changeId(String changeId);

    abstract TestChange build();
  }
}
