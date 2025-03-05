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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.gerrit.entities.Change;

/**
 * Representation of a change used for testing purposes.
 *
 * @param numericChangeId The numeric change ID, sometimes also called change number or legacy
 *     change ID. Unique per host.
 * @param changeId The Change-Id as specified in the commit message. Consists of an {@code I}
 *     followed by a 40-hex string. Only unique per project-branch.
 */
public record TestChange(Change.Id numericChangeId, String changeId) {
  public TestChange {
    requireNonNull(numericChangeId, "numericChangeId");
    requireNonNull(changeId, "changeId");
  }

  static Builder builder() {
    return new AutoBuilder_TestChange_Builder();
  }

  @AutoBuilder
  abstract static class Builder {
    abstract Builder numericChangeId(Change.Id numericChangeId);

    abstract Builder changeId(String changeId);

    abstract TestChange build();
  }
}
