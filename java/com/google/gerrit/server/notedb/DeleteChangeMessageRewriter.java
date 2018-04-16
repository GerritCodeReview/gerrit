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
import static org.eclipse.jgit.util.RawParseUtils.decode;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
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
  public interface Factory {
    DeleteChangeMessageRewriter create(
        Change.Id id,
        @Assisted("uuid") String uuid,
        @Assisted("newChangeMessage") String newMessage);
  }

  private final Change.Id changeId;
  private final String uuid;
  private final String newChangeMessage;

  @Inject
  DeleteChangeMessageRewriter(
      @Assisted Change.Id changeId,
      @Assisted("uuid") String uuid,
      @Assisted("newChangeMessage") String newChangeMessage) {
    this.changeId = changeId;
    this.uuid = uuid;
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
    revWalk.sort(RevSort.REVERSE);

    ObjectId newTipId = null;
    boolean rewrite = false;
    boolean rewriteMessage = false;
    RevCommit originalCommit;
    while ((originalCommit = revWalk.next()) != null) {
      if (!rewrite && uuid.equals(originalCommit.getName())) {
        rewrite = true;
        rewriteMessage = true;
      }

      if (!rewrite) {
        newTipId = originalCommit;
        continue;
      }

      if (rewriteMessage) {
        String newCommitMessage = createNewCommitMessage(originalCommit);
        newTipId = rewriteOneCommit(originalCommit, newTipId, newCommitMessage, inserter);
        rewriteMessage = false;
      } else {
        newTipId =
            rewriteOneCommit(originalCommit, newTipId, originalCommit.getFullMessage(), inserter);
      }
    }
    return newTipId;
  }

  private String createNewCommitMessage(RevCommit commit) {
    byte[] raw = commit.getRawBuffer();

    Optional<ChangeNotesParser.Range> range = ChangeNotesParser.parseCommitMessageRange(commit);
    if (!range.isPresent()) {
      throw new IllegalStateException("fail to parse commit message");
    }

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
   * @param parentCommitId the parent of the new commit.
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
