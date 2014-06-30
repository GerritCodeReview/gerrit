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
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.GERRIT_PLACEHOLDER_HOST;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
  public interface Factory {
    ChangeUpdate create(ChangeControl ctl);
    ChangeUpdate create(ChangeControl ctl, Date when);
    @VisibleForTesting
    ChangeUpdate create(ChangeControl ctl, Date when,
        Comparator<String> labelNameComparator);
  }

  private final NotesMigration migration;
  private final GitRepositoryManager repoManager;
  private final AccountCache accountCache;
  private final MetaDataUpdate.User updateFactory;
  private final ChangeControl ctl;
  private final PersonIdent serverIdent;
  private final Date when;
  private final Map<String, Optional<Short>> approvals;
  private final Map<Account.Id, ReviewerState> reviewers;
  private Change.Status status;
  private String subject;
  private PatchSet.Id psId;
  private List<SubmitRecord> submitRecords;
  private String changeMessage;

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      AccountCache accountCache,
      MetaDataUpdate.User updateFactory,
      ProjectCache projectCache,
      IdentifiedUser user,
      @Assisted ChangeControl ctl) {
    this(serverIdent, repoManager, migration, accountCache, updateFactory,
        projectCache, ctl, serverIdent.getWhen());
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      AccountCache accountCache,
      MetaDataUpdate.User updateFactory,
      ProjectCache projectCache,
      @Assisted ChangeControl ctl,
      @Assisted Date when) {
    this(serverIdent, repoManager, migration, accountCache, updateFactory,
        ctl, when,
        projectCache.get(getProjectName(ctl)).getLabelTypes().nameComparator());
  }

  private static Project.NameKey getProjectName(ChangeControl ctl) {
    return ctl.getChange().getDest().getParentKey();
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      AccountCache accountCache,
      MetaDataUpdate.User updateFactory,
      @Assisted ChangeControl ctl,
      @Assisted Date when,
      @Assisted Comparator<String> labelNameComparator) {
    this.repoManager = repoManager;
    this.migration = migration;
    this.accountCache = accountCache;
    this.updateFactory = updateFactory;
    this.ctl = ctl;
    this.when = when;
    this.serverIdent = serverIdent;
    this.approvals = Maps.newTreeMap(labelNameComparator);
    this.reviewers = Maps.newLinkedHashMap();
  }

  public Change getChange() {
    return ctl.getChange();
  }

  public IdentifiedUser getUser() {
    return (IdentifiedUser) ctl.getCurrentUser();
  }

  public Date getWhen() {
    return when;
  }

  public void setStatus(Change.Status status) {
    checkArgument(status != Change.Status.SUBMITTED,
        "use submit(Iterable<PatchSetApproval>)");
    this.status = status;
  }

  public void putApproval(String label, short value) {
    approvals.put(label, Optional.of(value));
  }

  public void removeApproval(String label) {
    approvals.put(label, Optional.<Short> absent());
  }

  public void submit(Iterable<SubmitRecord> submitRecords) {
    status = Change.Status.SUBMITTED;
    this.submitRecords = ImmutableList.copyOf(submitRecords);
    checkArgument(!this.submitRecords.isEmpty(),
        "no submit records specified at submit time");
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public void setPatchSetId(PatchSet.Id psId) {
    checkArgument(psId == null
        || psId.getParentKey().equals(getChange().getId()));
    this.psId = psId;
  }

  public void setChangeMessage(String changeMessage) {
    this.changeMessage = changeMessage;
  }

  public void putReviewer(Account.Id reviewer, ReviewerState type) {
    checkArgument(type != ReviewerState.REMOVED, "invalid ReviewerType");
    reviewers.put(reviewer, type);
  }

  public void removeReviewer(Account.Id reviewer) {
    reviewers.put(reviewer, ReviewerState.REMOVED);
  }

  private void load() throws IOException {
    if (migration.write() && getRevision() == null) {
      Repository repo = repoManager.openRepository(getChange().getProject());
      try {
        load(repo);
      } catch (ConfigInvalidException e) {
        throw new IOException(e);
      } finally {
        repo.close();
      }
    }
  }

  @Override
  public RevCommit commit(MetaDataUpdate md) throws IOException {
    throw new UnsupportedOperationException("use commit()");
  }

  public RevCommit commit() throws IOException {
    BatchMetaDataUpdate batch = openUpdate();
    try {
      batch.write(new CommitBuilder());
      return batch.commit();
    } finally {
      batch.close();
    }
  }

  private PersonIdent newIdent(Account author, Date when) {
    return new PersonIdent(
        author.getFullName(),
        author.getId().get() + "@" + GERRIT_PLACEHOLDER_HOST,
        when, serverIdent.getTimeZone());
  }

  @Override
  public BatchMetaDataUpdate openUpdate(MetaDataUpdate update) throws IOException {
    throw new UnsupportedOperationException("use openUpdate()");
  }

  public BatchMetaDataUpdate openUpdate() throws IOException {
    if (migration.write()) {
      load();
      MetaDataUpdate md =
          updateFactory.create(getChange().getProject(), getUser());
      md.setAllowEmpty(true);
      return super.openUpdate(md);
    }
    return new BatchMetaDataUpdate() {
      @Override
      public void write(CommitBuilder commit) {
        // Do nothing.
      }

      @Override
      public void write(VersionedMetaData config, CommitBuilder commit) {
        // Do nothing.
      }

      @Override
      public RevCommit createRef(String refName) {
        return null;
      }

      @Override
      public RevCommit commit() {
        return null;
      }

      @Override
      public RevCommit commitAt(ObjectId revision) {
        return null;
      }

      @Override
      public void close() {
        // Do nothing.
      }
    };
  }

  @Override
  protected String getRefName() {
    return ChangeNoteUtil.changeRefName(getChange().getId());
  }

  @Override
  protected boolean onSave(CommitBuilder commit) {
    if (isEmpty()) {
      return false;
    }
    commit.setAuthor(newIdent(getUser().getAccount(), when));
    commit.setCommitter(new PersonIdent(serverIdent, when));

    int ps = psId != null ? psId.get() : getChange().currentPatchSetId().get();
    StringBuilder msg = new StringBuilder();
    if (subject != null) {
      msg.append(subject);
    } else {
      msg.append("Update patch set ").append(ps);
    }
    msg.append("\n\n");

    if (changeMessage != null) {
      msg.append(changeMessage);
      msg.append("\n\n");
    }


    addFooter(msg, FOOTER_PATCH_SET, ps);
    if (status != null) {
      addFooter(msg, FOOTER_STATUS, status.name().toLowerCase());
    }

    for (Map.Entry<Account.Id, ReviewerState> e : reviewers.entrySet()) {
      Account account = accountCache.get(e.getKey()).getAccount();
      PersonIdent ident = newIdent(account, when);
      addFooter(msg, e.getValue().getFooterKey())
          .append(ident.getName())
          .append(" <").append(ident.getEmailAddress()).append(">\n");
    }

    for (Map.Entry<String, Optional<Short>> e : approvals.entrySet()) {
      if (!e.getValue().isPresent()) {
        addFooter(msg, FOOTER_LABEL, '-', e.getKey());
      } else {
        addFooter(msg, FOOTER_LABEL,
            new LabelVote(e.getKey(), e.getValue().get()).formatWithEquals());
      }
    }

    if (submitRecords != null) {
      for (SubmitRecord rec : submitRecords) {
        addFooter(msg, FOOTER_SUBMITTED_WITH)
            .append(rec.status);
        if (rec.errorMessage != null) {
          msg.append(' ').append(sanitizeFooter(rec.errorMessage));
        }
        msg.append('\n');

        if (rec.labels != null) {
          for (SubmitRecord.Label label : rec.labels) {
            addFooter(msg, FOOTER_SUBMITTED_WITH)
                .append(label.status).append(": ").append(label.label);
            if (label.appliedBy != null) {
              PersonIdent ident =
                  newIdent(accountCache.get(label.appliedBy).getAccount(), when);
              msg.append(": ").append(ident.getName())
                  .append(" <").append(ident.getEmailAddress()).append('>');
            }
            msg.append('\n');
          }
        }
      }
    }

    commit.setMessage(msg.toString());
    return true;
  }

  private boolean isEmpty() {
    return approvals.isEmpty()
        && reviewers.isEmpty()
        && status == null
        && submitRecords == null
        && changeMessage == null;
  }

  private static StringBuilder addFooter(StringBuilder sb, FooterKey footer) {
    return sb.append(footer.getName()).append(": ");
  }

  private static void addFooter(StringBuilder sb, FooterKey footer,
      Object... values) {
    addFooter(sb, footer);
    for (Object value : values) {
      sb.append(value);
    }
    sb.append('\n');
  }

  private static String sanitizeFooter(String value) {
    return value.replace('\n', ' ').replace('\0', ' ');
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    // Do nothing; just reads current revision.
  }
}
