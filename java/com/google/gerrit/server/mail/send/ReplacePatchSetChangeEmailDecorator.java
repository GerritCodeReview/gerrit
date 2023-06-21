// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.server.mail.send.ChangeEmail.ChangeEmailDecorator;
import java.util.Collection;

/** Send notice of new patch sets for reviewers. */
public interface ReplacePatchSetChangeEmailDecorator extends ChangeEmailDecorator {
  /** Add reviewers that should be notified. */
  void addReviewers(Collection<Account.Id> cc);

  /** Add non-reviewer targets to be notified. */
  void addExtraCC(Collection<Account.Id> cc);

  /** Provide set of approvals that are now outdated. */
  void addOutdatedApproval(@Nullable Collection<PatchSetApproval> outdatedApprovals);
}
