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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ACCOUNT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_VOTE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.Map;

/**
 * A single delta to apply atomically to a change.
 * <p>
 * This delta becomes a single commit on the notes branch, so there are
 * limitations on the set of modifications that can be handled in a single
 * update. In particular, there is a single author and timestamp for each
 * update.
 */
public class ChangeUpdate extends VersionedMetaData {
  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;
    private final MetaDataUpdate.User updateFactory;
    private final ProjectCache projectCache;
    private final Provider<IdentifiedUser> user;

    @Inject
    Factory(GitRepositoryManager repoManager,
        MetaDataUpdate.User updateFactory,
        ProjectCache projectCache,
        Provider<IdentifiedUser> user) {
      this.repoManager = repoManager;
      this.updateFactory = updateFactory;
      this.projectCache = projectCache;
      this.user = user;
    }

    public ChangeUpdate load(Change change)
        throws NoSuchProjectException, ConfigInvalidException, IOException {
      return load(change, TimeUtil.nowTs());
    }

    public ChangeUpdate load(Change change, Timestamp when)
        throws NoSuchProjectException, ConfigInvalidException, IOException {
      Repository repo;
      try {
        repo = repoManager.openRepository(change.getProject());
      } catch (RepositoryNotFoundException e) {
        throw new NoSuchProjectException(change.getProject(), e);
      }
      try {
        return new ChangeUpdate(
            updateFactory, repo, change, user.get().getAccountId(), when,
            projectCache.get(change.getProject()).getLabelTypes().nameComparator());
      } finally {
        repo.close();
      }
    }
  }

  private final MetaDataUpdate.User updateFactory;
  private final Change change;
  private final Account.Id accountId;
  private final Timestamp when;
  private final Map<String, Short> approvals;

  @VisibleForTesting
  ChangeUpdate(Repository repo, Change change, Account.Id accountId,
      @Nullable Timestamp when)
      throws ConfigInvalidException, IOException {
    this(null, repo, change, accountId, when, Ordering.natural());
  }

  private ChangeUpdate(@Nullable MetaDataUpdate.User updateFactory,
      Repository repo, Change change, Account.Id accountId,
      @Nullable Timestamp when, Comparator<? super String> approvalsComparator)
      throws ConfigInvalidException, IOException {
    this.updateFactory = updateFactory;
    this.change = change;
    this.accountId = accountId;
    this.when = when;
    this.approvals = Maps.newTreeMap(approvalsComparator);
    load(repo);
  }

  public void putApproval(String label, short value) {
    approvals.put(label.toLowerCase(), value);
  }

  public RevCommit commit() throws IOException {
    return commit(checkNotNull(updateFactory, "MetaDataUpdate.Factory")
        .create(change.getProject()));
  }

  @Override
  public RevCommit commit(MetaDataUpdate md) throws IOException {
    md.setAllowEmpty(true);
    if (when != null) {
      md.getCommitBuilder().setAuthor(new PersonIdent(
          md.getCommitBuilder().getAuthor(), when));
      md.getCommitBuilder().setCommitter(new PersonIdent(
          md.getCommitBuilder().getCommitter(), when));
    }
    return super.commit(md);
  }

  @Override
  protected String getRefName() {
    return ChangeNoteUtil.changeRefName(change.getId());
  }

  @Override
  protected void onSave(CommitBuilder commit) {
    if (approvals.isEmpty()) {
      return;
    }
    int psId = change.currentPatchSetId().get();
    StringBuilder msg = new StringBuilder()
        .append("Update patch set ").append(psId)
        .append("\n\n")
        .append(FOOTER_PATCH_SET).append(": ").append(psId)
        .append('\n')
        .append(FOOTER_ACCOUNT).append(": ").append(accountId)
        .append('\n');
    for (Map.Entry<String, Short> e : approvals.entrySet()) {
      msg.append(FOOTER_VOTE).append(": ").append(e.getKey())
          .append('=').append(LabelValue.formatValue(e.getValue()))
          .append('\n');
    }
    commit.setMessage(msg.toString());
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    // Do nothing; just reads current revision.
  }
}
