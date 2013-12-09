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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_REVIEWER;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.GERRIT_PLACEHOLDER_HOST;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;

/**
 * A single delta to apply atomically to a change.
 * <p>
 * This delta becomes a single commit on the notes branch, so there are
 * limitations on the set of modifications that can be handled in a single
 * update. In particular, there is a single author and timestamp for each
 * update.
 * <p>
 * This class is not thread-safe.
 */
public class ChangeUpdate extends VersionedMetaData {
  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;
    private final MetaDataUpdate.User updateFactory;
    private final ProjectCache projectCache;
    private final Provider<IdentifiedUser> user;

    @Inject
    Factory(
        GitRepositoryManager repoManager,
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
            updateFactory, repo,
            projectCache.get(change.getProject()).getLabelTypes(),
            change, user.get().getAccountId(), when);
      } finally {
        repo.close();
      }
    }
  }

  private final MetaDataUpdate.User updateFactory;
  private final LabelTypes labelTypes;
  private final Change change;
  private final Account.Id accountId;
  private final Timestamp when;
  private final Map<String, Short> approvals;
  private final Map<Account.Id, ReviewerState> reviewers;
  private String subject;

  @VisibleForTesting
  ChangeUpdate(Repository repo, LabelTypes labelTypes, Change change,
      Account.Id accountId, @Nullable Timestamp when)
      throws ConfigInvalidException, IOException {
    this(null, repo, labelTypes, change, accountId, when);
  }

  private ChangeUpdate(@Nullable MetaDataUpdate.User updateFactory,
      Repository repo, LabelTypes labelTypes, Change change,
      Account.Id accountId, @Nullable Timestamp when)
      throws ConfigInvalidException, IOException {
    this.updateFactory = updateFactory;
    this.labelTypes = labelTypes;
    this.change = change;
    this.accountId = accountId;
    this.when = when;
    this.approvals = Maps.newTreeMap(labelTypes.nameComparator());
    this.reviewers = Maps.newLinkedHashMap();
    load(repo);
  }

  public void putApproval(String label, short value) {
    approvals.put(label.toLowerCase(), value);
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public void putReviewer(Account.Id reviewer, ReviewerState type) {
    checkArgument(type != ReviewerState.REMOVED, "invalid ReviewerType");
    reviewers.put(reviewer, type);
  }

  public void removeReviewer(Account.Id reviewer) {
    reviewers.put(reviewer, ReviewerState.REMOVED);
  }

  public RevCommit commit() throws IOException {
    return commit(checkNotNull(updateFactory, "MetaDataUpdate.Factory")
        .create(change.getProject()));
  }

  @Override
  public RevCommit commit(MetaDataUpdate md) throws IOException {
    md.setAllowEmpty(true);
    CommitBuilder cb = md.getCommitBuilder();
    cb.setCommitter(new PersonIdent(
        cb.getAuthor().getName(),
        accountId.get() + "@" + GERRIT_PLACEHOLDER_HOST,
        when != null ? when : cb.getCommitter().getWhen(),
        cb.getCommitter().getTimeZone()));
    if (when != null) {
      md.getCommitBuilder().setAuthor(new PersonIdent(
          md.getCommitBuilder().getAuthor(), when));
    }
    return super.commit(md);
  }

  @Override
  protected String getRefName() {
    return ChangeNoteUtil.changeRefName(change.getId());
  }

  @Override
  protected void onSave(CommitBuilder commit) {
    if (approvals.isEmpty() && reviewers.isEmpty()) {
      return;
    }
    int psId = change.currentPatchSetId().get();
    StringBuilder msg = new StringBuilder();
    if (subject != null) {
      msg.append(subject);
    } else {
      msg.append("Update patch set ").append(psId);
    }
    msg.append("\n\n");
    addFooter(msg, FOOTER_PATCH_SET, psId);
    for (Map.Entry<Account.Id, ReviewerState> e : reviewers.entrySet()) {
      addFooter(msg, FOOTER_REVIEWER, e.getKey(), e.getValue());
    }
    for (Map.Entry<String, Short> e : approvals.entrySet()) {
      LabelType lt = labelTypes.byLabel(e.getKey());
      if (lt != null) {
        addFooter(msg, FOOTER_LABEL,
            new LabelVote(lt.getName(), e.getValue()).formatWithEquals());
      }
    }
    commit.setMessage(msg.toString());
  }

  private static void addFooter(StringBuilder sb, FooterKey footer,
      Object value) {
    sb.append(footer.getName()).append(": ").append(value).append('\n');
  }

  private static void addFooter(StringBuilder sb, FooterKey footer,
      Object key, Object value) {
    sb.append(footer.getName()).append(": ").append(key).append('=')
        .append(value).append('\n');
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    // Do nothing; just reads current revision.
  }
}
