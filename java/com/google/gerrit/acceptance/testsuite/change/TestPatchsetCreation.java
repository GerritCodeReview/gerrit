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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.edit.tree.TreeModification;

/** Initial attributes of the patchset. If not provided, arbitrary values will be used. */
@AutoValue
public abstract class TestPatchsetCreation {

  public abstract ImmutableList<TreeModification> treeModifications();

  abstract ThrowingFunction<TestPatchsetCreation, PatchSet.Id> patchsetCreator();

  public static TestPatchsetCreation.Builder builder(
      ThrowingFunction<TestPatchsetCreation, PatchSet.Id> patchsetCreator) {
    return new AutoValue_TestPatchsetCreation.Builder().patchsetCreator(patchsetCreator);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    /** Modified file of the patchset. The file content is specified via the returned builder. */
    public FileContentBuilder<Builder> file(String filePath) {
      return new FileContentBuilder<>(this, filePath, treeModificationsBuilder()::add);
    }

    abstract ImmutableList.Builder<TreeModification> treeModificationsBuilder();

    abstract TestPatchsetCreation.Builder patchsetCreator(
        ThrowingFunction<TestPatchsetCreation, PatchSet.Id> patchsetCreator);

    abstract TestPatchsetCreation autoBuild();

    /**
     * Creates the patchset.
     *
     * @return the {@code PatchSet.Id} of the created patchset
     */
    public PatchSet.Id create() {
      TestPatchsetCreation patchsetCreation = autoBuild();
      return patchsetCreation.patchsetCreator().applyAndThrowSilently(patchsetCreation);
    }
  }
}
