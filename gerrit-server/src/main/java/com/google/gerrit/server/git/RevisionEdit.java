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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
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
  private static final String REFS_EDITS = "refs/edits/";

  private final IdentifiedUser user;
  private final PatchSet.Id psid;
  private Ref ref;

  public RevisionEdit(IdentifiedUser user, PatchSet.Id psid) {
    this(user, psid, null);
  }

  public RevisionEdit(IdentifiedUser user, PatchSet.Id psid, Ref ref) {
    this.user = user;
    this.psid = psid;
    this.ref = ref;
  }

  public String toRefName() {
    return refPrefix(user, psid.getParentKey()).append(psid.get()).toString();
  }

  public RevCommit get(Repository repo, RevWalk rw) throws IOException {
    ObjectId oid = getObjectId(repo);
    if (oid == null) {
      return null;
    }
    return rw.parseCommit(oid);
  }

  private ObjectId getObjectId(Repository repo) throws IOException {
    if (ref == null) {
      ref = repo.getRef(toRefName());
    }
    if (ref == null) {
      return null;
    }
    return ref.getObjectId();
  }

  public PatchSet getPatchSet(Repository repo) throws IOException {
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

  public static StringBuilder refPrefix(IdentifiedUser user,
      Change.Id changeId) {
    StringBuilder r = new StringBuilder();
    r.append(REFS_EDITS);
    appendSharded(r, user.getAccountId().get());
    appendSharded(r, changeId.get());
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
