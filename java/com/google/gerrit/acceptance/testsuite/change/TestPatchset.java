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
import com.google.gerrit.entities.PatchSet;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Representation of a patchset used for testing purposes.
 *
 * @param patchsetId The numeric patchset ID.
 * @param commitId The commit SHA-1 of the patchset.
 */
public record TestPatchset(PatchSet.Id patchsetId, ObjectId commitId) {
  public TestPatchset {
    requireNonNull(patchsetId, "patchsetId");
    requireNonNull(commitId, "commitId");
  }

  static Builder builder() {
    return new AutoBuilder_TestPatchset_Builder();
  }

  @AutoBuilder
  abstract static class Builder {
    abstract Builder patchsetId(PatchSet.Id patchsetId);

    abstract Builder commitId(ObjectId commitId);

    abstract TestPatchset build();
  }
}
