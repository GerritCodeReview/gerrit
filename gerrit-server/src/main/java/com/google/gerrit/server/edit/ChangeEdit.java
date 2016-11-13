// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.IdentifiedUser;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * A single user's edit for a change.
 *
 * <p>There is max. one edit per user per change. Edits are stored on refs:
 * refs/users/UU/UUUU/edit-CCCC/P where UU/UUUU is sharded representation of user account, CCCC is
 * change number and P is the patch set number it is based on.
 */
public class ChangeEdit {
  private final IdentifiedUser user;
  private final Change change;
  private final Ref ref;
  private final RevCommit editCommit;
  private final PatchSet basePatchSet;

  public ChangeEdit(
      IdentifiedUser user, Change change, Ref ref, RevCommit editCommit, PatchSet basePatchSet) {
    checkNotNull(user);
    checkNotNull(change);
    checkNotNull(ref);
    checkNotNull(editCommit);
    checkNotNull(basePatchSet);
    this.user = user;
    this.change = change;
    this.ref = ref;
    this.editCommit = editCommit;
    this.basePatchSet = basePatchSet;
  }

  public Change getChange() {
    return change;
  }

  public IdentifiedUser getUser() {
    return user;
  }

  public Ref getRef() {
    return ref;
  }

  public RevId getRevision() {
    return new RevId(ObjectId.toString(ref.getObjectId()));
  }

  public String getRefName() {
    return RefNames.refsEdit(user.getAccountId(), change.getId(), basePatchSet.getId());
  }

  public RevCommit getEditCommit() {
    return editCommit;
  }

  public PatchSet getBasePatchSet() {
    return basePatchSet;
  }
}
