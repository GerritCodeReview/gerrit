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
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.IdentifiedUser;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A single user's edit for a change.
 * <p>
 * There is max. one edit per user per change. Edits are stored on refs:
 * refs/users/UU/UUUU/edit-CCCC where UU/UUUU is sharded representation
 * of user account and CCCC is change number.
 */
public class RevisionEdit {
  private final IdentifiedUser user;
  private final Change change;
  private final Ref ref;

  public RevisionEdit(IdentifiedUser user, Change change, Ref ref) {
    checkNotNull(user);
    checkNotNull(change);
    checkNotNull(ref);
    this.user = user;
    this.change = change;
    this.ref = ref;
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
    return String.format("%s/edit-%d",
        RefNames.refsUsers(user.getAccountId()),
        change.getId().get());
  }
}
