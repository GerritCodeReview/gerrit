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

import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ACCOUNT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_VOTE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
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

    public ChangeNotes load(Change change)
        throws NoSuchProjectException, ConfigInvalidException, IOException {
      Repository repo;
      try {
        repo = repoManager.openRepository(change.getProject());
      } catch (RepositoryNotFoundException e) {
        throw new NoSuchProjectException(change.getProject(), e);
      }
      try {
        return new ChangeNotes(repo, change);
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
      walk.parseBody(commit);
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

    for (String line : commit.getFooterLines(FOOTER_VOTE)) {
      PatchSetApproval psa = parseVote(psId, accountId, commit, line);
      PatchSetApproval old = curr.get(psa.getLabel());
      if (old == null || old.getGranted().compareTo(psa.getGranted()) < 0) {
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

  private PatchSetApproval parseVote(PatchSet.Id psId, Account.Id accountId,
      RevCommit commit, String line) throws ConfigInvalidException {
    Throwable cause = null;
    List<String> parts = ChangeNoteUtil.EQUALS.splitToList(line);
    try {
      if (parts.size() == 2) {
        LabelType.checkName(parts.get(0));
        return new PatchSetApproval(
            new PatchSetApproval.Key(
              psId, parseIdent(commit), new LabelId(parts.get(0))),
            Shorts.checkedCast(Integer.parseInt(parts.get(1))),
            new Timestamp(commit.getAuthorIdent().getWhen().getTime()));
      }
      throw parseException("invalid %s: %s", FOOTER_VOTE, line);
    } catch (IllegalArgumentException e) {
      cause = e;
    }
    ConfigInvalidException pe =
        parseException("invalid %s: %s", FOOTER_VOTE, line);
    pe.initCause(cause);
    throw pe;
  }

  private Account.Id parseIdent(RevCommit commit)
      throws ConfigInvalidException {
    List<String> ids = commit.getFooterLines(FOOTER_ACCOUNT);
    if (ids.size() != 1) {
      throw parseException("missing or multiple %s: %s", FOOTER_ACCOUNT, ids);
    }
    Integer id = Ints.tryParse(ids.get(0));
    if (id == null) {
      throw parseException("invalid %s: %s", FOOTER_ACCOUNT, ids.get(0));
    }
    return new Account.Id(id);
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
