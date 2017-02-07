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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.List;

/**
 * Normalizes votes on labels according to project config and permissions.
 *
 * <p>Votes are recorded in the database for a user based on the state of the project at that time:
 * what labels are defined for the project, and what the user is allowed to vote on. Both of those
 * can change between the time a vote is originally made and a later point, for example when a
 * change is submitted. This class normalizes old votes against current project configuration.
 */
@Singleton
public class LabelNormalizer {
  @AutoValue
  public abstract static class Result {
    @VisibleForTesting
    static Result create(
        List<PatchSetApproval> unchanged,
        List<PatchSetApproval> updated,
        List<PatchSetApproval> deleted) {
      return new AutoValue_LabelNormalizer_Result(
          ImmutableList.copyOf(unchanged),
          ImmutableList.copyOf(updated),
          ImmutableList.copyOf(deleted));
    }

    public abstract ImmutableList<PatchSetApproval> unchanged();

    public abstract ImmutableList<PatchSetApproval> updated();

    public abstract ImmutableList<PatchSetApproval> deleted();

    public Iterable<PatchSetApproval> getNormalized() {
      return Iterables.concat(unchanged(), updated());
    }
  }

  private final Provider<ReviewDb> db;
  private final ChangeControl.GenericFactory changeFactory;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  LabelNormalizer(
      Provider<ReviewDb> db,
      ChangeControl.GenericFactory changeFactory,
      IdentifiedUser.GenericFactory userFactory) {
    this.db = db;
    this.changeFactory = changeFactory;
    this.userFactory = userFactory;
  }

  /**
   * @param change change containing the given approvals.
   * @param approvals list of approvals.
   * @return copies of approvals normalized to the defined ranges for the label type and permissions
   *     for the user. Approvals for unknown labels are not included in the output, nor are
   *     approvals where the user has no permissions for that label.
   * @throws NoSuchChangeException
   * @throws OrmException
   */
  public Result normalize(Change change, Collection<PatchSetApproval> approvals)
      throws NoSuchChangeException, OrmException {
    IdentifiedUser user = userFactory.create(change.getOwner());
    return normalize(changeFactory.controlFor(db.get(), change, user), approvals);
  }

  /**
   * @param ctl change control containing the given approvals.
   * @param approvals list of approvals.
   * @return copies of approvals normalized to the defined ranges for the label type and permissions
   *     for the user. Approvals for unknown labels are not included in the output, nor are
   *     approvals where the user has no permissions for that label.
   */
  public Result normalize(ChangeControl ctl, Collection<PatchSetApproval> approvals) {
    List<PatchSetApproval> unchanged = Lists.newArrayListWithCapacity(approvals.size());
    List<PatchSetApproval> updated = Lists.newArrayListWithCapacity(approvals.size());
    List<PatchSetApproval> deleted = Lists.newArrayListWithCapacity(approvals.size());
    LabelTypes labelTypes = ctl.getLabelTypes();
    for (PatchSetApproval psa : approvals) {
      Change.Id changeId = psa.getKey().getParentKey().getParentKey();
      checkArgument(
          changeId.equals(ctl.getId()),
          "Approval %s does not match change %s",
          psa.getKey(),
          ctl.getChange().getKey());
      if (psa.isLegacySubmit()) {
        unchanged.add(psa);
        continue;
      }
      LabelType label = labelTypes.byLabel(psa.getLabelId());
      if (label == null) {
        deleted.add(psa);
        continue;
      }
      PatchSetApproval copy = copy(psa);
      applyTypeFloor(label, copy);
      if (!applyRightFloor(ctl, label, copy)) {
        deleted.add(psa);
      } else if (copy.getValue() != psa.getValue()) {
        updated.add(copy);
      } else {
        unchanged.add(psa);
      }
    }
    return Result.create(unchanged, updated, deleted);
  }

  /**
   * @param ctl change control (for any user).
   * @param lt label type.
   * @param id account ID.
   * @return whether the given account ID has any permissions to vote on this label for this change.
   */
  public boolean canVote(ChangeControl ctl, LabelType lt, Account.Id id) {
    return !getRange(ctl, lt, id).isEmpty();
  }

  private PatchSetApproval copy(PatchSetApproval src) {
    return new PatchSetApproval(src.getPatchSetId(), src);
  }

  private PermissionRange getRange(ChangeControl ctl, LabelType lt, Account.Id id) {
    String permission = Permission.forLabel(lt.getName());
    IdentifiedUser user = userFactory.create(id);
    return ctl.forUser(user).getRange(permission);
  }

  private boolean applyRightFloor(ChangeControl ctl, LabelType lt, PatchSetApproval a) {
    PermissionRange range = getRange(ctl, lt, a.getAccountId());
    if (range.isEmpty()) {
      return false;
    }
    a.setValue((short) range.squash(a.getValue()));
    return true;
  }

  private void applyTypeFloor(LabelType lt, PatchSetApproval a) {
    LabelValue atMin = lt.getMin();
    if (atMin != null && a.getValue() < atMin.getValue()) {
      a.setValue(atMin.getValue());
    }
    LabelValue atMax = lt.getMax();
    if (atMax != null && a.getValue() > atMax.getValue()) {
      a.setValue(atMax.getValue());
    }
  }
}
