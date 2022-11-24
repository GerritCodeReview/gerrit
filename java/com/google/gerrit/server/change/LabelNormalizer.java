// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Normalizes votes on labels according to project config.
 *
 * <p>Votes are recorded in the database for a user based on the state of the project at that time:
 * what labels are defined for the project. The label definition can change between the time a vote
 * is originally made and a later point, for example when a change is submitted. This class
 * normalizes old votes against current project configuration.
 *
 * <p>Normalizing a vote means making it compliant with the current label definition:
 *
 * <ul>
 *   <li>If the voting value is greater than the max allowed value according to the label
 *       definition, the voting value is changed to the max allowed value.
 *   <li>If the voting value is lower than the min allowed value according to the label definition,
 *       the voting value is changed to the min allowed value.
 *   <li>If the label definition for a vote is missing, the vote is deleted.
 * </ul>
 */
@Singleton
public class LabelNormalizer {
  @AutoValue
  public abstract static class Result {
    @VisibleForTesting
    static Result create(
        Set<PatchSetApproval> unchanged,
        Set<PatchSetApproval> updated,
        Set<PatchSetApproval> deleted) {
      return new AutoValue_LabelNormalizer_Result(
          ImmutableSet.copyOf(unchanged),
          ImmutableSet.copyOf(updated),
          ImmutableSet.copyOf(deleted));
    }

    public abstract ImmutableSet<PatchSetApproval> unchanged();

    public abstract ImmutableSet<PatchSetApproval> updated();

    public abstract ImmutableSet<PatchSetApproval> deleted();

    public ImmutableSet<PatchSetApproval> getNormalized() {
      return Streams.concat(unchanged().stream(), updated().stream())
          .distinct()
          .collect(toImmutableSet());
    }
  }

  private final ProjectCache projectCache;

  @Inject
  LabelNormalizer(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  /**
   * Returns copies of approvals normalized to the defined ranges for the label type. Approvals for
   * unknown labels are not included in the output
   *
   * @param notes change notes containing the given approvals.
   * @param approvals list of approvals.
   */
  public Result normalize(ChangeNotes notes, Collection<PatchSetApproval> approvals) {
    Set<PatchSetApproval> unchanged = new HashSet<>(approvals.size());
    Set<PatchSetApproval> updated = new HashSet<>(approvals.size());
    Set<PatchSetApproval> deleted = new HashSet<>(approvals.size());
    LabelTypes labelTypes =
        projectCache
            .get(notes.getProjectName())
            .orElseThrow(illegalState(notes.getProjectName()))
            .getLabelTypes(notes);
    for (PatchSetApproval psa : approvals) {
      Change.Id changeId = psa.key().patchSetId().changeId();
      checkArgument(
          changeId.equals(notes.getChangeId()),
          "Approval %s does not match change %s",
          psa.key(),
          notes.getChange().getKey());
      if (psa.isLegacySubmit()) {
        unchanged.add(psa);
        continue;
      }
      Optional<LabelType> label = labelTypes.byLabel(psa.labelId());
      if (!label.isPresent()) {
        deleted.add(psa);
        continue;
      }
      PatchSetApproval copy = applyTypeFloor(label.get(), psa);
      if (copy.value() != psa.value()) {
        updated.add(copy);
      } else {
        unchanged.add(psa);
      }
    }
    return Result.create(unchanged, updated, deleted);
  }

  private PatchSetApproval applyTypeFloor(LabelType lt, PatchSetApproval a) {
    PatchSetApproval.Builder b = a.toBuilder();
    LabelValue atMin = lt.getMin();
    if (atMin != null && a.value() < atMin.getValue()) {
      b.value(atMin.getValue());
    }
    LabelValue atMax = lt.getMax();
    if (atMax != null && a.value() > atMax.getValue()) {
      b.value(atMax.getValue());
    }
    return b.build();
  }
}
