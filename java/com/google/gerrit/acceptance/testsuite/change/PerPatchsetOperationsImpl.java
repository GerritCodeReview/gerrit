// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import static com.google.gerrit.server.CommentsUtil.setCommentCommitId;

import com.google.gerrit.acceptance.testsuite.change.TestCommentCreation.CommentSide;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * The implementation of {@link PerPatchsetOperations}.
 *
 * <p>There is only one implementation of {@link PerPatchsetOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
public class PerPatchsetOperationsImpl implements PerPatchsetOperations {
  private final GitRepositoryManager repositoryManager;
  private final IdentifiedUser.GenericFactory userFactory;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final CommentsUtil commentsUtil;
  private final PatchListCache patchListCache;

  private final ChangeNotes changeNotes;
  private final PatchSet.Id patchsetId;

  public interface Factory {
    PerPatchsetOperationsImpl create(ChangeNotes changeNotes, PatchSet.Id patchsetId);
  }

  @Inject
  private PerPatchsetOperationsImpl(
      GitRepositoryManager repositoryManager,
      GenericFactory userFactory,
      BatchUpdate.Factory batchUpdateFactory,
      CommentsUtil commentsUtil,
      PatchListCache patchListCache,
      @Assisted ChangeNotes changeNotes,
      @Assisted PatchSet.Id patchsetId) {
    this.repositoryManager = repositoryManager;
    this.userFactory = userFactory;
    this.batchUpdateFactory = batchUpdateFactory;
    this.commentsUtil = commentsUtil;
    this.patchListCache = patchListCache;
    this.changeNotes = changeNotes;
    this.patchsetId = patchsetId;
  }

  @Override
  public TestPatchset get() {
    PatchSet patchset = changeNotes.getPatchSets().get(patchsetId);
    return TestPatchset.builder().patchsetId(patchsetId).commitId(patchset.commitId()).build();
  }

  @Override
  public TestCommentCreation.Builder newComment() {
    return TestCommentCreation.builder(this::createComment);
  }

  private String createComment(TestCommentCreation commentCreation)
      throws IOException, RestApiException, UpdateException {
    Project.NameKey project = changeNotes.getProjectName();

    try (Repository repository = repositoryManager.openRepository(project);
        ObjectInserter objectInserter = repository.newObjectInserter();
        RevWalk revWalk = new RevWalk(objectInserter.newReader())) {
      Timestamp now = TimeUtil.nowTs();

      // Use identity of change owner until the API allows to specify the commenter.
      IdentifiedUser changeOwner = userFactory.create(changeNotes.getChange().getOwner());
      CommentAdditionOp commentAdditionOp = new CommentAdditionOp(commentCreation);
      try (BatchUpdate batchUpdate = batchUpdateFactory.create(project, changeOwner, now)) {
        batchUpdate.setRepository(repository, revWalk, objectInserter);
        batchUpdate.addOp(changeNotes.getChangeId(), commentAdditionOp);
        batchUpdate.execute();
      }
      return commentAdditionOp.createdCommentUuid;
    }
  }

  private class CommentAdditionOp implements BatchUpdateOp {
    private String createdCommentUuid;
    private final TestCommentCreation commentCreation;

    public CommentAdditionOp(TestCommentCreation commentCreation) {
      this.commentCreation = commentCreation;
    }

    @Override
    public boolean updateChange(ChangeContext context) throws Exception {
      HumanComment comment = toNewComment(context, commentCreation);

      context
          .getUpdate(context.getChange().currentPatchSetId())
          .putComment(HumanComment.Status.PUBLISHED, comment);

      createdCommentUuid = comment.key.uuid;
      return true;
    }

    private HumanComment toNewComment(ChangeContext context, TestCommentCreation commentCreation)
        throws UnprocessableEntityException, PatchListNotAvailableException {
      String message = commentCreation.message().orElse("The text of a test comment.");

      String filePath = commentCreation.file().orElse(Patch.PATCHSET_LEVEL);
      short side = commentCreation.side().orElse(CommentSide.PATCHSET_COMMIT).getNumericSide();
      Boolean unresolved = commentCreation.unresolved().orElse(null);
      String parentUuid = commentCreation.parentUuid().orElse(null);
      HumanComment newComment =
          commentsUtil.newHumanComment(
              context, filePath, patchsetId, side, message, unresolved, parentUuid);

      commentCreation.line().ifPresent(line -> newComment.setLineNbrAndRange(line, null));
      // Specification of range trumps explicit line specification.
      commentCreation
          .range()
          .map(this::toCommentRange)
          .ifPresent(range -> newComment.setLineNbrAndRange(null, range));

      setCommentCommitId(
          newComment,
          patchListCache,
          context.getChange(),
          changeNotes.getPatchSets().get(patchsetId));
      return newComment;
    }

    private Comment.Range toCommentRange(TestRange range) {
      Comment.Range commentRange = new Range();
      commentRange.startLine = range.start().line();
      commentRange.startCharacter = range.start().charOffset();
      commentRange.endLine = range.end().line();
      commentRange.endCharacter = range.end().charOffset();
      return commentRange;
    }
  }
}
