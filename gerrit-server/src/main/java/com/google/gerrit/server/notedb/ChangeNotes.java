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

import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.GERRIT_PLACEHOLDER_HOST;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.util.LabelVote;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** View of a single {@link Change} based on the log of its notes branch. */
public class ChangeNotes extends VersionedMetaData {
  private static final Ordering<PatchSetApproval> PSA_BY_TIME =
      Ordering.natural().onResultOf(
        new Function<PatchSetApproval, Timestamp>() {
          @Override
          public Timestamp apply(PatchSetApproval input) {
            return input.getGranted();
          }
        });

  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;

    @Inject
    Factory(GitRepositoryManager repoManager) {
      this.repoManager = repoManager;
    }

    // TODO(dborowitz): Wrap fewer exceptions if/when we kill gwtorm.
    public ChangeNotes load(Change change) throws OrmException {
      Repository repo;
      try {
        repo = repoManager.openRepository(change.getProject());
      } catch (IOException e) {
        throw new OrmException(e);
      }
      try {
        return new ChangeNotes(repo, change);
      } catch (ConfigInvalidException | IOException e) {
        throw new OrmException(e);
      } finally {
        repo.close();
      }
    }
  }

  private final Change change;
  private ListMultimap<PatchSet.Id, PatchSetApproval> approvals;

  @VisibleForTesting
  ChangeNotes(Repository repo, Change change)
      throws ConfigInvalidException, IOException {
    this.change = change;
    load(repo);
  }

  public ListMultimap<PatchSet.Id, PatchSetApproval> getApprovals() {
    return Multimaps.unmodifiableListMultimap(approvals);
  }

  @Override
  protected String getRefName() {
    return ChangeNoteUtil.changeRefName(change.getId());
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    ObjectId rev = getRevision();
    if (rev == null) {
      return;
    }
    approvals = ArrayListMultimap.create();
    RevWalk walk = new RevWalk(reader);
    walk.markStart(walk.parseCommit(rev));
    for (RevCommit commit : walk) {
      parse(commit);
    }
    for (Collection<PatchSetApproval> v : approvals.asMap().values()) {
      Collections.sort((List<PatchSetApproval>) v, PSA_BY_TIME);
    }
  }

  private void parse(RevCommit commit) throws ConfigInvalidException {
    PatchSet.Id psId = parsePatchSetId(commit);
    Account.Id accountId = parseIdent(commit);
    List<PatchSetApproval> psas = approvals.get(psId);

    Map<String, PatchSetApproval> curr =
        Maps.newHashMapWithExpectedSize(psas.size());
    for (PatchSetApproval psa : psas) {
      if (psa.getAccountId().equals(accountId)) {
        curr.put(psa.getLabel(), psa);
      }
    }

    for (String line : commit.getFooterLines(FOOTER_LABEL)) {
      PatchSetApproval psa = parseApproval(psId, accountId, commit, line);
      if (!curr.containsKey(psa.getLabel())) {
        curr.put(psa.getLabel(), psa);
        psas.add(psa);
      }
    }
  }

  private PatchSet.Id parsePatchSetId(RevCommit commit)
      throws ConfigInvalidException {
    List<String> psIdLines = commit.getFooterLines(FOOTER_PATCH_SET);
    if (psIdLines.size() != 1) {
      throw parseException("missing or multiple %s: %s",
          FOOTER_PATCH_SET, psIdLines);
    }
    Integer psId = Ints.tryParse(psIdLines.get(0));
    if (psId == null) {
      throw parseException("invalid %s: %s",
          FOOTER_PATCH_SET, psIdLines.get(0));
    }
    return new PatchSet.Id(change.getId(), psId);
  }

  private PatchSetApproval parseApproval(PatchSet.Id psId, Account.Id accountId,
      RevCommit commit, String line) throws ConfigInvalidException {
    try {
      LabelVote l = LabelVote.parseWithEquals(line);
      return new PatchSetApproval(
          new PatchSetApproval.Key(
            psId, parseIdent(commit), new LabelId(l.getLabel())),
          l.getValue(),
          new Timestamp(commit.getCommitterIdent().getWhen().getTime()));
    } catch (IllegalArgumentException e) {
      ConfigInvalidException pe =
          parseException("invalid %s: %s", FOOTER_LABEL, line);
      pe.initCause(e);
      throw pe;
    }
  }

  private Account.Id parseIdent(RevCommit commit)
      throws ConfigInvalidException {
    String email = commit.getCommitterIdent().getEmailAddress();
    int at = email.indexOf('@');
    if (at >= 0) {
      String host = email.substring(at + 1, email.length());
      Integer id = Ints.tryParse(email.substring(0, at));
      if (id != null && host.equals(GERRIT_PLACEHOLDER_HOST)) {
        return new Account.Id(id);
      }
    }
    throw parseException("invalid committer, expected <id>@%s: %s",
      GERRIT_PLACEHOLDER_HOST, email);
  }

  private ConfigInvalidException parseException(String fmt, Object... args) {
    return new ConfigInvalidException("Change " + change.getId() + ": "
        + String.format(fmt, args));
  }

  @Override
  protected void onSave(CommitBuilder commit) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " is read-only");
  }
}
