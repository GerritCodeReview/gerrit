// Copyright (C) 2018 The Android Open Source Project
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
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.parseCommitMessageRange;
import static org.eclipse.jgit.util.RawParseUtils.decode;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Deletes a change message from NoteDb by rewriting the commit history. After deletion, the whole
 * change message will be replaced by a new message indicating the original change message has been
 * deleted for the given reason.
 */
public class DeleteChangeMessageRewriter implements NoteDbRewriter {

  private final Change.Id changeId;
  private final String targetMessageId;
  private final String newChangeMessage;

  DeleteChangeMessageRewriter(Change.Id changeId, String targetMessageId, String newChangeMessage) {
    this.changeId = changeId;
    this.targetMessageId = checkNotNull(targetMessageId);
    this.newChangeMessage = newChangeMessage;
  }

  @Override
  public String getRefName() {
    return RefNames.changeMetaRef(changeId);
  }

  @Override
  public ObjectId rewriteCommitHistory(RevWalk revWalk, ObjectInserter inserter, ObjectId currTip)
      throws IOException {
    checkArgument(!currTip.equals(ObjectId.zeroId()));

    // Walk from the first commit of the branch.
    revWalk.reset();
    revWalk.markStart(revWalk.parseCommit(currTip));
    revWalk.sort(RevSort.TOPO);
    revWalk.sort(RevSort.REVERSE);

    ObjectId newTipId = null;
    RevCommit originalCommit;
    boolean startRewrite = false;
    while ((originalCommit = revWalk.next()) != null) {
      boolean isTargetCommit = originalCommit.getId().getName().equals(targetMessageId);
      if (!startRewrite && !isTargetCommit) {
        newTipId = originalCommit;
        continue;
      }

      startRewrite = true;
      String newCommitMessage =
          isTargetCommit ? createNewCommitMessage(originalCommit) : originalCommit.getFullMessage();
      newTipId = rewriteOneCommit(originalCommit, newTipId, newCommitMessage, inserter);
    }
    return newTipId;
  }

  private String createNewCommitMessage(RevCommit commit) {
    byte[] raw = commit.getRawBuffer();

    Optional<ChangeNoteUtil.CommitMessageRange> range = parseCommitMessageRange(commit);
    checkState(range.isPresent(), "failed to parse commit message");

    // Only replace the commit message body, which is the user-provided message. The subject and
    // footers are NoteDb metadata.
    Charset encoding = RawParseUtils.parseEncoding(raw);
    String prefix =
        decode(encoding, raw, range.get().subjectStart(), range.get().changeMessageStart());
    String postfix = decode(encoding, raw, range.get().changeMessageEnd() + 1, raw.length);
    return prefix + newChangeMessage + postfix;
  }

  /**
   * Rewrites one commit.
   *
   * @param originalCommit the original commit to be rewritten.
   * @param parentCommitId the parent of the new commit. For the first rewritten commit, it's the
   *     parent of 'originalCommit'. For the latter rewritten commits, it's the commit rewritten
   *     just before it.
   * @param commitMessage the full commit message of the new commit.
   * @param inserter the {@code ObjectInserter} for the rewrite process.
   * @return the {@code objectId} of the new commit.
   * @throws IOException
   */
  private ObjectId rewriteOneCommit(
      RevCommit originalCommit,
      ObjectId parentCommitId,
      String commitMessage,
      ObjectInserter inserter)
      throws IOException {
    CommitBuilder cb = new CommitBuilder();
    if (parentCommitId != null) {
      cb.setParentId(parentCommitId);
    }
    cb.setTreeId(originalCommit.getTree());
    cb.setMessage(commitMessage);
    cb.setCommitter(originalCommit.getCommitterIdent());
    cb.setAuthor(originalCommit.getAuthorIdent());
    cb.setEncoding(originalCommit.getEncoding());
    return inserter.insert(cb);
  }
}
