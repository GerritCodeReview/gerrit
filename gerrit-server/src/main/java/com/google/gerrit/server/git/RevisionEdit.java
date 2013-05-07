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

import com.google.gerrit.common.changes.RevisionId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.IdentifiedUser;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/** A single user's edits on top of an existing patch set. */
public class RevisionEdit {
  private static final String REFS_EDITS = "refs/edits/";

  private final IdentifiedUser user;
  private final PatchSet.Id psid;

  public RevisionEdit(IdentifiedUser user, RevisionId revId) {
    checkArgument(revId.isEdit(), "not an edit: %s", revId.getPatchSetId());
    this.user = user;
    this.psid = revId.getPatchSetId();
  }

  public String toRefName() {
    StringBuilder r = new StringBuilder();
    r.append(REFS_EDITS);
    appendSharded(r, user.getAccountId().get());
    appendSharded(r, psid.getParentKey().get());
    return r.append(psid.get()).toString();
  }

  private static void appendSharded(StringBuilder sb, int n) {
    int m = n % 100;
    if (m < 10) {
      sb.append('0');
    }
    sb.append(m).append('/').append(n).append('/');
  }

  public RevCommit get(Repository repo, RevWalk rw) throws IOException {
    Ref ref = repo.getRef(toRefName());
    if (ref == null) {
      return null;
    }
    return rw.parseCommit(ref.getObjectId());
  }
}
