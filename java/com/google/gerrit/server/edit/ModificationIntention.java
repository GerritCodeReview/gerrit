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

package com.google.gerrit.server.edit;

import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.notedb.ChangeNotes;

/**
 * Intended modification target.
 *
 * <p>See also {@link ModificationTarget}. Some modifications may have a fixed target. For other
 * modifications, the presence of a change edit influences their target. The latter comes from the
 * REST endpoints of change edits which work no matter whether a change edit is present or not. If
 * it's not present, a new change edit is created based on the current patchset. As we don't want to
 * create an "empty" commit for the new change edit first, we need this class/interface for the
 * flexible handling.
 */
interface ModificationIntention {

  ModificationTarget getTargetWhenEditExists(ChangeEdit changeEdit);

  ModificationTarget getTargetWhenNoEdit(ChangeNotes notes);

  /** A specific patchset is the modification target. */
  class PatchsetCommit implements ModificationIntention {

    private final PatchSet patchSet;

    PatchsetCommit(PatchSet patchSet) {
      this.patchSet = patchSet;
    }

    @Override
    public ModificationTarget getTargetWhenEditExists(ChangeEdit changeEdit) {
      return new ModificationTarget.PatchsetCommit(patchSet);
    }

    @Override
    public ModificationTarget getTargetWhenNoEdit(ChangeNotes notes) {
      return new ModificationTarget.PatchsetCommit(patchSet);
    }
  }

  /**
   * The latest commit should be the modification target. If a change edit exists, it's considered
   * to be the latest commit. Otherwise, defer to the latest patchset commit.
   */
  class LatestCommit implements ModificationIntention {

    @Override
    public ModificationTarget getTargetWhenEditExists(ChangeEdit changeEdit) {
      return new ModificationTarget.EditCommit(changeEdit);
    }

    @Override
    public ModificationTarget getTargetWhenNoEdit(ChangeNotes notes) {
      return new ModificationTarget.PatchsetCommit(notes.getCurrentPatchSet());
    }
  }
}
