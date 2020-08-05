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

import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * The implementation of {@link PerPatchsetOperations}.
 *
 * <p>There is only one implementation of {@link PerPatchsetOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
public class PerPatchsetOperationsImpl implements PerPatchsetOperations {
  private final ChangeNotes changeNotes;
  private final PatchSet.Id patchsetId;

  public interface Factory {
    PerPatchsetOperationsImpl create(ChangeNotes changeNotes, PatchSet.Id patchsetId);
  }

  @Inject
  private PerPatchsetOperationsImpl(
      @Assisted ChangeNotes changeNotes, @Assisted PatchSet.Id patchsetId) {
    this.changeNotes = changeNotes;
    this.patchsetId = patchsetId;
  }

  @Override
  public TestPatchset get() {
    PatchSet patchset = changeNotes.getPatchSets().get(patchsetId);
    return TestPatchset.builder().patchsetId(patchsetId).commitId(patchset.commitId()).build();
  }
}
