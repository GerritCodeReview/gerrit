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

import com.google.common.collect.Maps;
import com.google.gerrit.common.changes.RevisionId;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.IdentifiedUser;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** A single user's edits on top of an existing patch set. */
public class RevisionEdit {
  private static final String REFS_EDITS = "refs/edits/";

  public static Map<PatchSet.Id, RevisionEdit> forChange(Repository repo,
      Change.Id changeId, IdentifiedUser user) throws IOException {
    Set<String> names = repo.getRefDatabase().getRefs(
        refPrefix(user, changeId).toString()).keySet();
    Map<PatchSet.Id, RevisionEdit> result =
        Maps.newHashMapWithExpectedSize(names.size());
    for (String name : names) {
      PatchSet.Id psid = new PatchSet.Id(changeId, Integer.valueOf(name));
      result.put(psid, new RevisionEdit(user, new RevisionId(psid, true)));
    }
    return Collections.unmodifiableMap(result);
  }

  private static StringBuilder refPrefix(IdentifiedUser user,
      Change.Id changeId) {
    StringBuilder r = new StringBuilder();
    r.append(REFS_EDITS);
    appendSharded(r, user.getAccountId().get());
    appendSharded(r, changeId.get());
    return r.append('/');
  }

  private static void appendSharded(StringBuilder sb, int n) {
    int m = n % 100;
    if (m < 10) {
      sb.append('0');
    }
    sb.append(m).append('/').append(n).append('/');
  }

  private final IdentifiedUser user;
  private final PatchSet.Id psid;

  public RevisionEdit(IdentifiedUser user, RevisionId revId) {
    checkArgument(revId.isEdit(), "not an edit: %s", revId.getPatchSetId());
    this.user = user;
    this.psid = revId.getPatchSetId();
  }

  public String toRefName() {
    return refPrefix(user, psid.getParentKey()).append(psid.get()).toString();
  }

  public RevCommit get(Repository repo, RevWalk rw) throws IOException {
    Ref ref = repo.getRef(toRefName());
    if (ref == null) {
      return null;
    }
    return rw.parseCommit(ref.getObjectId());
  }
}
