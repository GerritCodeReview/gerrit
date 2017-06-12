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

import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.notedb.ChangeUpdate;
import java.sql.Timestamp;

class ApprovalEvent extends Event {
  private PatchSetApproval psa;

  ApprovalEvent(PatchSetApproval psa, Timestamp changeCreatedOn) {
    super(
        psa.getPatchSetId(),
        psa.getAccountId(),
        psa.getRealAccountId(),
        psa.getGranted(),
        changeCreatedOn,
        psa.getTag());
    this.psa = psa;
  }

  @Override
  boolean uniquePerUpdate() {
    return false;
  }

  @Override
  void apply(ChangeUpdate update) {
    checkUpdate(update);
    update.putApproval(psa.getLabel(), psa.getValue());
  }

  @Override
  protected boolean isPostSubmitApproval() {
    return psa.isPostSubmit();
  }
}
