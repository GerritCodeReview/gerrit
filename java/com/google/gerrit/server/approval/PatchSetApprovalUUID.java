// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.approval;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetApproval.UUID;
import java.security.MessageDigest;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;

public class PatchSetApprovalUUID {
  public static UUID make(PatchSetApproval approval, PersonIdent serverIdent) {
    return make(
        approval.patchSetId(),
        approval.accountId(),
        approval.label(),
        approval.value(),
        serverIdent);
  }

  public static UUID make(
      PatchSet.Id patchSetId,
      Account.Id accountId,
      String label,
      short value,
      PersonIdent serverIdent) {
    MessageDigest md = Constants.newMessageDigest();
    md.update(
        Constants.encode("patchSetId " + patchSetId.getCommaSeparatedChangeAndPatchSetId() + "\n"));
    md.update(Constants.encode("accountId " + accountId + "\n"));
    md.update(Constants.encode("label " + label + "\n"));
    md.update(Constants.encode("value " + value + "\n"));
    md.update(Constants.encode("value " + serverIdent.toExternalString() + "\n"));
    md.update(Constants.encode(String.valueOf(Math.random())));
    return PatchSetApproval.uuid(ObjectId.fromRaw(md.digest()).name());
  }

  private PatchSetApprovalUUID() {}
}
