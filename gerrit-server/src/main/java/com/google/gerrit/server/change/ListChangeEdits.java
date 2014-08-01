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

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class ListChangeEdits implements RestReadView<ChangeResource> {
  private static final Logger log =
      LoggerFactory.getLogger(ListChangeEdits.class);

  // Factory is needed to break circular dependency between
  // ListChangeEdits and ChangeEdits
  interface Factory {
    ListChangeEdits create();
  }

  private final ChangeEdits changeEdits;
  private final ChangeEditUtil editUtil;
  private final AccountByEmailCache byEmailCache;
  private final Provider<CurrentUser> userProvider;

  @Inject
  ListChangeEdits(ChangeEdits changeEdits,
      ChangeEditUtil editUtil,
      AccountByEmailCache byEmailCache,
      Provider<CurrentUser> userProvider) {
    this.changeEdits = changeEdits;
    this.editUtil = editUtil;
    this.byEmailCache = byEmailCache;
    this.userProvider = userProvider;
  }

  @Override
  public Map<String, EditInfo> apply(ChangeResource rsrc)
      throws AuthException, IOException {
    Map<String, EditInfo> res = new HashMap<>();
    Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
    if (edit.isPresent()) {
      ChangeEdit src = edit.get();
      res.put(src.getRevision().get(), toEditInfo(rsrc, src));
    }
    return res;
  }

  private EditInfo toEditInfo(ChangeResource rsrc, ChangeEdit edit) {
    EditInfo out = new EditInfo();
    // Mark change edit with unique number 0.
    // We can do that, as we have max. 1 change edit
    // per change per user.
    out._number = 0;

    out.actions = Maps.newTreeMap();
    for (UiAction.Description d : UiActions.from(
        changeEdits,
        new ChangeEditResource(rsrc, edit, null),
        userProvider)) {
      out.actions.put(d.getId(), new ActionInfo(d));
    }

    try {
      out.commit = toCommit(edit);
    } catch (NoSuchChangeException | IOException e) {
      log.warn("Cannot load change edit fot change: {}",
          edit.getChange().getChangeId(), e);
    }

    return out;
  }

  private CommitInfo toCommit(ChangeEdit edit)
      throws IOException, NoSuchChangeException {
    RevCommit editCommit = editUtil.getCommit(edit);
    CommitInfo commit = new CommitInfo();
    commit.parents = Lists.newArrayListWithCapacity(1);
    commit.author = ChangeJson.toGitPerson(
        toUserIdentity(editCommit.getAuthorIdent()));
    commit.committer = ChangeJson.toGitPerson(
        toUserIdentity(editCommit.getCommitterIdent()));
    commit.subject = editCommit.getShortMessage();
    commit.message = editCommit.getFullMessage();

    RevCommit parent = editUtil.getParentCommit(edit);
    CommitInfo i = new CommitInfo();
    i.commit = ObjectId.toString(parent.toObjectId());
    i.subject = parent.getShortMessage();
    commit.parents.add(i);

    return commit;
  }

  // TODO(davido): Shamelessly stolen from PatchSetInfoFactory.java
  private UserIdentity toUserIdentity(PersonIdent who) {
    UserIdentity u = new UserIdentity();
    u.setName(who.getName());
    u.setEmail(who.getEmailAddress());
    u.setDate(new Timestamp(who.getWhen().getTime()));
    u.setTimeZone(who.getTimeZoneOffset());

    Set<Account.Id> a = byEmailCache.get(u.getEmail());
    if (a.size() == 1) {
      u.setAccount(Iterables.getOnlyElement(a));
    }

    return u;
  }
}
