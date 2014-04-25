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

import com.google.common.base.Preconditions;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.IdentifiedUser;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/** A single user's edits on top of an existing patch set. */
public class RevisionEdit {
  private final IdentifiedUser user;
  private final Change.Id changeId;
  private Ref ref;

  public RevisionEdit(IdentifiedUser user, Change.Id changeId) {
    this(user, changeId, null);
  }

  public RevisionEdit(IdentifiedUser user, Change.Id changeId, Ref ref) {
    this.user = user;
    this.changeId = changeId;
    this.ref = ref;
  }

  /**
   * Returns reference for this revision edit with sharded user and change id:
   * refs/users/UU/UUUU/edit-CCCC
   * <p>
   * @return reference for this revision edit
   */
  public String getRefName() {
    return getChangeEditsRef(user, changeId).toString();
  }

  public RevCommit getCommit(Repository repo, RevWalk rw) throws IOException {
    ObjectId oid = getObjectId(repo);
    if (oid == null) {
      return null;
    }
    return rw.parseCommit(oid);
  }

  private ObjectId getObjectId(Repository repo) throws IOException {
    if (ref == null) {
      ref = repo.getRef(getRefName());
    }
    if (ref == null) {
      return null;
    }
    return ref.getObjectId();
  }

  public PatchSet getPatchSet(Repository repo, int parentPsNumber)
      throws IOException {
    // TODO(davido): find better distinct patch set number for edit on
    // top of regular patch set id then negate parent ps number.
    // We cannot use the same ps number, it is already used.
    // We shoudn't shove bits of edit into patchset id, so negate it
    // for now.
    // I am open for better suggestions how to solve the coexistence
    // of regular patch set n with edit on top of patch set n and and
    // to transport it to client and have both in the same sorted list.
    int psNumber = -parentPsNumber;
    Preconditions.checkState(psNumber < 0);
    PatchSet.Id psid = new PatchSet.Id(changeId, psNumber);
    PatchSet ps = new PatchSet(psid);
    ps.setUploader(user.getAccountId());
    ps.setRevision(getRevId(repo));
    return ps;
  }

  public RevId getRevId(Repository repo) throws IOException {
    ObjectId oid = getObjectId(repo);
    if (oid == null) {
      return null;
    }
    return new RevId(ObjectId.toString(oid));
  }

  public static Ref getChangeEditRef(Repository repo,
      IdentifiedUser user, Change.Id changeId) throws IOException {
    return repo.getRefDatabase().getRef(
        getChangeEditsRef(user, changeId).toString());
  }

  private static StringBuilder getChangeEditsRef(IdentifiedUser user,
      Change.Id changeId) {
    Preconditions.checkNotNull(user);
    Preconditions.checkNotNull(changeId);
    StringBuilder r = new StringBuilder(RefNames.REFS_USER);
    appendSharded(r, user.getAccountId().get());
    r.append(String.format("edit-%d", changeId.get()));
    return r;
  }

  private static void appendSharded(StringBuilder sb, int n) {
    int m = n % 100;
    if (m < 10) {
      sb.append('0');
    }
    sb.append(m).append('/').append(n).append('/');
  }
}
