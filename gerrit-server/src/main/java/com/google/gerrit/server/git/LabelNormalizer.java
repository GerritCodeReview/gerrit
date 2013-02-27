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

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.List;

public class LabelNormalizer {
  private final ChangeControl.GenericFactory changeFactory;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  LabelNormalizer(ChangeControl.GenericFactory changeFactory,
      IdentifiedUser.GenericFactory userFactory) {
    this.changeFactory = changeFactory;
    this.userFactory = userFactory;
  }

  /**
   * @param change change containing the given approvals.
   * @param approvals list of approvals.
   * @return copies of approvals normalized to the defined ranges for the label
   *     type and permissions for the user. Approvals for unknown labels are not
   *     included in the output.
   * @throws NoSuchChangeException
   */
  public List<PatchSetApproval> normalize(Change change,
      Collection<PatchSetApproval> approvals) throws NoSuchChangeException {
    return normalize(
        changeFactory.controlFor(change, userFactory.create(change.getOwner())),
        approvals);
  }

  /**
   * @param change change control containing the given approvals.
   * @param approvals list of approvals.
   * @return copies of approvals normalized to the defined ranges for the label
   *     type and permissions for the user. Approvals for unknown labels are not
   *     included in the output.
   */
  public List<PatchSetApproval> normalize(ChangeControl ctl,
      Collection<PatchSetApproval> approvals) {
    List<PatchSetApproval> result =
        Lists.newArrayListWithCapacity(approvals.size());
    LabelTypes labelTypes = ctl.getLabelTypes();
    for (PatchSetApproval psa : approvals) {
      checkArgument(psa.getKey().getParentKey().getParentKey()
          .equals(ctl.getChange().getId()),
          "Approval %s does not match change %s",
          psa.getKey(), ctl.getChange().getKey());
      LabelType label = labelTypes.byLabel(psa.getLabelId());
      boolean isSubmit = psa.isSubmit();
      if (label != null || isSubmit) {
        psa = copy(psa, ctl);
        result.add(psa);
        if (!isSubmit) {
          applyTypeFloor(label, psa);
          applyRightFloor(ctl, label, psa);
        }
      }
    }
    return result;
  }

  private PatchSetApproval copy(PatchSetApproval src, ChangeControl ctl) {
    PatchSetApproval dest = new PatchSetApproval(src.getPatchSetId(), src);
    dest.cache(ctl.getChange());
    dest.setLabel(src.getLabel());
    return dest;
  }

  private void applyRightFloor(ChangeControl ctl, LabelType lt,
      PatchSetApproval a) {
    String permission = Permission.forLabel(lt.getName());
    IdentifiedUser user = userFactory.create(a.getAccountId());
    PermissionRange range = ctl.forUser(user).getRange(permission);
    a.setValue((short) range.squash(a.getValue()));
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
