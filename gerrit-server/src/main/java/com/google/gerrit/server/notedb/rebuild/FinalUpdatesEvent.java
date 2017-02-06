// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.gerrit.reviewdb.server.ReviewDbUtil.intKeyOrdering;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableCollection;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;
import java.util.Objects;

class FinalUpdatesEvent extends Event {
  private final Change change;
  private final Change noteDbChange;
  private final ImmutableCollection<PatchSet> patchSets;

  FinalUpdatesEvent(Change change, Change noteDbChange, ImmutableCollection<PatchSet> patchSets) {
    super(
        change.currentPatchSetId(),
        change.getOwner(),
        change.getOwner(),
        change.getLastUpdatedOn(),
        change.getCreatedOn(),
        null);
    this.change = change;
    this.noteDbChange = noteDbChange;
    this.patchSets = patchSets;
  }

  @Override
  boolean uniquePerUpdate() {
    return true;
  }

  @SuppressWarnings("deprecation")
  @Override
  void apply(ChangeUpdate update) throws OrmException {
    if (!Objects.equals(change.getTopic(), noteDbChange.getTopic())) {
      update.setTopic(change.getTopic());
    }
    if (!statusMatches()) {
      // TODO(dborowitz): Stamp approximate approvals at this time.
      update.fixStatus(change.getStatus());
    }
    if (change.getSubmissionId() != null && noteDbChange.getSubmissionId() == null) {
      update.setSubmissionId(change.getSubmissionId());
    }
    if (!Objects.equals(change.getAssignee(), noteDbChange.getAssignee())) {
      // TODO(dborowitz): Parse intermediate values out from messages.
      update.setAssignee(change.getAssignee());
    }
    if (!patchSets.isEmpty() && !highestNumberedPatchSetIsCurrent()) {
      update.setCurrentPatchSet();
    }
    if (!update.isEmpty()) {
      update.setSubjectForCommit("Final NoteDb migration updates");
    }
  }

  private boolean statusMatches() {
    return Objects.equals(change.getStatus(), noteDbChange.getStatus());
  }

  private boolean highestNumberedPatchSetIsCurrent() {
    PatchSet.Id max = patchSets.stream().map(PatchSet::getId).max(intKeyOrdering()).get();
    return max.equals(change.currentPatchSetId());
  }

  @Override
  protected boolean isSubmit() {
    return change.getStatus() == Change.Status.MERGED;
  }

  @Override
  protected void addToString(ToStringHelper helper) {
    if (!statusMatches()) {
      helper.add("status", change.getStatus());
    }
  }
}
