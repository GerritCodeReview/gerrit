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

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Target of the modification of a commit.
 *
 * <p>This is currently used in the context of change edits which involves both direct actions on
 * change edits (e.g. creating a change edit; modifying a file of a change edit) as well as indirect
 * creation/modification of them (e.g. via applying a suggested fix of a robot comment.)
 *
 * <p>Depending on the situation and exact action, either an existing {@link ChangeEdit} (-> {@link
 * EditCommit} or a specific patchset commit (-> {@link PatchsetCommit}) is the target of a
 * modification.
 */
public interface ModificationTarget {

  void ensureNewEditMayBeBasedOnTarget(Change change) throws InvalidChangeOperationException;

  void ensureTargetMayBeModifiedDespiteExistingEdit(ChangeEdit changeEdit)
      throws InvalidChangeOperationException;

  /** Commit to modify. */
  RevCommit getCommit(Repository repository) throws IOException;

  /**
   * Patchset within whose context the modification happens. This also applies to change edits as
   * each change edit is based on a specific patchset.
   */
  PatchSet getBasePatchset();

  /** A specific patchset commit is the target of the modification. */
  class PatchsetCommit implements ModificationTarget {

    private final PatchSet patchSet;

    PatchsetCommit(PatchSet patchSet) {
      this.patchSet = patchSet;
    }

    @Override
    public void ensureTargetMayBeModifiedDespiteExistingEdit(ChangeEdit changeEdit)
        throws InvalidChangeOperationException {
      if (!isBasedOn(changeEdit, patchSet)) {
        throw new InvalidChangeOperationException(
            String.format(
                "Only the patch set %s on which the existing change edit is based may be modified "
                    + "(specified patch set: %s)",
                changeEdit.getBasePatchSet().id(), patchSet.id()));
      }
    }

    private static boolean isBasedOn(ChangeEdit changeEdit, PatchSet patchSet) {
      PatchSet editBasePatchSet = changeEdit.getBasePatchSet();
      return editBasePatchSet.id().equals(patchSet.id());
    }

    @Override
    public void ensureNewEditMayBeBasedOnTarget(Change change)
        throws InvalidChangeOperationException {
      PatchSet.Id patchSetId = patchSet.id();
      PatchSet.Id currentPatchSetId = change.currentPatchSetId();
      if (!patchSetId.equals(currentPatchSetId)) {
        throw new InvalidChangeOperationException(
            String.format(
                "A change edit may only be created for the current patch set %s (and not for %s)",
                currentPatchSetId, patchSetId));
      }
    }

    @Override
    public RevCommit getCommit(Repository repository) throws IOException {
      try (RevWalk revWalk = new RevWalk(repository)) {
        return revWalk.parseCommit(patchSet.commitId());
      }
    }

    @Override
    public PatchSet getBasePatchset() {
      return patchSet;
    }
  }

  /** An existing {@link ChangeEdit} commit is the target of the modification. */
  class EditCommit implements ModificationTarget {

    private final ChangeEdit changeEdit;

    EditCommit(ChangeEdit changeEdit) {
      this.changeEdit = changeEdit;
    }

    @Override
    public void ensureNewEditMayBeBasedOnTarget(Change change) {
      // The current code will never create a new edit if one already exists. It would be a
      // programmer error if this changes in the future (without adjusting the storage of change
      // edits).
      throw new IllegalStateException(
          String.format(
              "Change %d already has a change edit for the calling user. A new change edit can't"
                  + " be created.",
              changeEdit.getChange().getChangeId()));
    }

    @Override
    public void ensureTargetMayBeModifiedDespiteExistingEdit(ChangeEdit changeEdit) {
      // The target is the change edit and hence can be modified.
    }

    @Override
    public RevCommit getCommit(Repository repository) throws IOException {
      return changeEdit.getEditCommit();
    }

    @Override
    public PatchSet getBasePatchset() {
      return changeEdit.getBasePatchSet();
    }
  }
}
