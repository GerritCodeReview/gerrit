// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ASSIGNEE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.notedb.ChangeNoteUtil.CommitMessageRange;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.PackInserter;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.RawParseUtils;

@UsedAt(UsedAt.Project.GOOGLE)
@Singleton
public class CommitRewriter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeNoteUtil changeNoteUtil;

  public static class BackfillResult {
    public boolean ok;
    public List<Change.Id> changesUpdated;
    public List<Change.Id> changesFailedToUpdate;
  }

  @Inject
  CommitRewriter(ChangeNoteUtil changeNoteUtil) {
    this.changeNoteUtil = changeNoteUtil;
  }

  public BackfillResult backfillProject(Repository repo, boolean dryRun) {
    BackfillResult result = new BackfillResult();
    result.ok = true;
    try (RevWalk revWalk = new RevWalk(repo);
        ObjectInserter ins = newPackInserter(repo)) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      bru.setAllowNonFastForwards(true);
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
        Change.Id changeId = Change.Id.fromRef(ref.getName());
        if (changeId == null || !ref.getName().equals(RefNames.changeMetaRef(changeId))) {
          continue;
        }
        ObjectId newTipId = backfillChange(revWalk, ins, ref);
        if (!newTipId.equals(ref.getObjectId())) {
          bru.addCommand(new ReceiveCommand(ref.getObjectId(), newTipId, ref.getName()));
        }
      }

      if (!bru.getCommands().isEmpty()) {
        if (!dryRun) {
          ins.flush();
          RefUpdateUtil.executeChecked(bru, revWalk);
        }
      }
    } catch (IOException e) {
      result.ok = false;
    }

    return result;
  }

  public ObjectId backfillChange(RevWalk revWalk, ObjectInserter inserter, Ref ref)
      throws IOException {
    ObjectId oldTip = ref.getObjectId();
    // Walk from the first commit of the branch.
    revWalk.reset();
    revWalk.markStart(revWalk.parseCommit(oldTip));
    revWalk.sort(RevSort.TOPO);
    revWalk.sort(RevSort.REVERSE);

    ObjectId newTipId = null;
    RevCommit originalCommit;
    boolean rewriteStarted = false;
    while ((originalCommit = revWalk.next()) != null) {
      PersonIdent authorIdent = originalCommit.getAuthorIdent();
      PersonIdent committerIdent = originalCommit.getCommitterIdent();
      Optional<Account.Id> author = NoteDbUtil.parseIdent(authorIdent);
      Optional<Account.Id> committer = NoteDbUtil.parseIdent(committerIdent);

      PersonIdent fixedAuthorIdent =
          changeNoteUtil.newAccountIdIdent(
              author.get(), authorIdent.getWhen(), authorIdent.getTimeZone());
      PersonIdent fixedCommitterIdent =
          changeNoteUtil.newAccountIdIdent(
              committer.get(), committerIdent.getWhen(), committerIdent.getTimeZone());
      String fixedCommitMessage = fixedCommitMessage(originalCommit);
      boolean needsFix =
          !fixedAuthorIdent.equals(authorIdent)
              || !fixedCommitterIdent.equals(committerIdent)
              || !originalCommit.getFullMessage().equals(fixedCommitMessage);
      if (!rewriteStarted && !needsFix) {
        newTipId = originalCommit;
        continue;
      }
      rewriteStarted = true;
      CommitBuilder cb = new CommitBuilder();
      if (newTipId != null) {
        cb.setParentId(newTipId);
      }
      cb.setTreeId(originalCommit.getTree());
      cb.setMessage(fixedCommitMessage);
      cb.setCommitter(fixedCommitterIdent);
      cb.setAuthor(fixedAuthorIdent);
      cb.setEncoding(originalCommit.getEncoding());
      newTipId = inserter.insert(cb);
    }
    return newTipId;
  }

  private String fixedCommitMessage(RevCommit revCommit) {
    StringBuilder msg = new StringBuilder();
    byte[] raw = revCommit.getRawBuffer();
    Charset enc = RawParseUtils.parseEncoding(raw);
    Optional<CommitMessageRange> commitMessageRange =
        ChangeNoteUtil.parseCommitMessageRange(revCommit);
    checkState(commitMessageRange.isPresent(), "failed to parse commit message");
    String changeSubject =
        RawParseUtils.decode(
            enc,
            raw,
            commitMessageRange.get().subjectStart(),
            commitMessageRange.get().subjectEnd() + 1);
    msg.append(changeSubject);
    msg.append("\n\n");
    String changeMessage =
        RawParseUtils.decode(
            enc,
            raw,
            commitMessageRange.get().changeMessageStart(),
            commitMessageRange.get().changeMessageEnd() + 1);
    msg.append(changeMessage);
    msg.append("\n\n");
    List<FooterLine> footerLines = revCommit.getFooterLines();
    for (FooterLine fl : footerLines) {
      if (fl.getKey().toLowerCase().equals(FOOTER_ASSIGNEE) && !fl.getValue().isEmpty()) {
        Optional<Account.Id> assignee =
            NoteDbUtil.parseIdent(RawParseUtils.parsePersonIdent(fl.getValue()));
        addFooter(msg, fl.getKey(), changeNoteUtil.getAccountIdIdentString(assignee.get()));
      }
      addFooter(msg, fl.getKey(), fl.getValue());
    }
    return msg.toString();
  }

  private static StringBuilder addFooter(StringBuilder sb, String footer, String value) {
    return sb.append(footer).append(": ").append(value).append('\n');
  }

  private static ObjectInserter newPackInserter(Repository repo) {
    if (!(repo instanceof FileRepository)) {
      return repo.newObjectInserter();
    }
    PackInserter ins = ((FileRepository) repo).getObjectDatabase().newPackInserter();
    ins.checkExisting(false);
    return ins;
  }
}
