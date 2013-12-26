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

package com.google.gerrit.server.change;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.PatchSetInfo.ParentInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ListRevisionEdits implements RestReadView<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(ChangeJson.class);

  private final RevisionEditReader editReader;
  private final Provider<CurrentUser> userProvider;
  private final Revisions revisions;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final Provider<ReviewDb> db;

  @Inject
  ListRevisionEdits(RevisionEditReader editReader,
      Provider<CurrentUser> userProvider,
      Revisions revisions,
      PatchSetInfoFactory patchSetInfoFactory,
      Provider<ReviewDb> db) {
    this.editReader = editReader;
    this.userProvider = userProvider;
    this.revisions = revisions;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.db = db;
  }

  @Override
  public Map<String, RevisionInfo> apply(ChangeResource rsrc)
      throws OrmException, AuthException, InvalidChangeOperationException,
      NoSuchChangeException, IOException {
    Map<String, RevisionInfo> res = new HashMap<>();
    Map<PatchSet.Id, PatchSet> map = editReader.read(rsrc.getChange());
    for (Map.Entry<PatchSet.Id, PatchSet> e : map.entrySet()) {
      res.put(e.getValue().getRevision().get(),
          toRevisionInfo(rsrc, e.getValue()));
    }
    return res;
  }

  private RevisionInfo toRevisionInfo(ChangeResource rsrc, PatchSet ps) {
    RevisionInfo out = new RevisionInfo();
    out._number = ps.getId().get();
    out.edit = true;
    out.actions = Maps.newTreeMap();
    for (UiAction.Description d : UiActions.from(
        revisions,
        new RevisionResource(rsrc, ps),
        userProvider)) {
      out.actions.put(d.getId(), new ActionInfo(d));
    }
    try {
      out.commit = toCommit(ps);
    } catch (PatchSetInfoNotAvailableException e) {
      log.warn("Cannot load PatchSetInfo " + ps.getId(), e);
    }
    return out;
  }

  // TODO(davido): Read commit from git and not from
  // the parent patch set of revision edit
  CommitInfo toCommit(PatchSet in)
      throws PatchSetInfoNotAvailableException {
    PatchSetInfo info = patchSetInfoFactory.get(db.get(), in.getId());
    CommitInfo commit = new CommitInfo();
    commit.parents = Lists.newArrayListWithCapacity(info.getParents().size());
    commit.author = ChangeJson.toGitPerson(info.getAuthor());
    commit.committer = ChangeJson.toGitPerson(info.getCommitter());
    commit.subject = info.getSubject();
    commit.message = info.getMessage();

    for (ParentInfo parent : info.getParents()) {
      CommitInfo i = new CommitInfo();
      i.commit = parent.id.get();
      i.subject = parent.shortMessage;
      commit.parents.add(i);
    }
    return commit;
  }
}
