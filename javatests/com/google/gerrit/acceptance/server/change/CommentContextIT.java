// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.entities.Patch.COMMIT_MSG;
import static com.google.gerrit.entities.Patch.MERGE_LIST;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MoreCollectors;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.ContextLineInfo;
import com.google.gerrit.server.comment.CommentContextCacheImpl;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CommentContextIT extends AbstractDaemonTest {
  /** The commit message of a single commit. */
  private static final String SUBJECT =
      String.join(
          "\n",
          "Commit Header",
          "",
          "This commit is doing something extremely important",
          "",
          "Footer: value");

  private static final String FILE_CONTENT =
      String.join("\n", "Line 1 of file", "", "Line 3 of file", "", "", "Line 6 of file");

  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setUp() {
    requestScopeOperations.setApiUser(user.id());
  }

  @Test
  public void commentContextForCommitMessageForLineComment() throws Exception {
    PushOneCommit.Result result =
        createChange(testRepo, "master", SUBJECT, FILE_NAME, FILE_CONTENT, "topic");
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput comment = CommentsUtil.newComment(COMMIT_MSG, Side.REVISION, 7, "comment", false);
    CommentsUtil.addComments(gApi, changeId, ps1, comment);

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(1);

    // The first few lines of the commit message are the headers, e.g.
    // Parent: ...
    // Author: ...
    // AuthorDate: ...
    // etc...
    assertThat(comments.get(0).contextLines)
        .containsExactlyElementsIn(createContextLines("7", "Commit Header"));
  }

  @Test
  public void commentContextForMergeList() throws Exception {
    PushOneCommit.Result result = createMergeCommitChange("refs/for/master");
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput comment = CommentsUtil.newComment(MERGE_LIST, Side.REVISION, 1, "comment", false);
    CommentsUtil.addComments(gApi, changeId, ps1, comment);

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).contextLines)
        .containsExactlyElementsIn(createContextLines("1", "Merge List:"));
  }

  @Test
  public void commentContextForCommitMessageForRangeComment() throws Exception {
    PushOneCommit.Result result =
        createChange(testRepo, "master", SUBJECT, FILE_NAME, FILE_CONTENT, "topic");
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput comment =
        CommentsUtil.newComment(
            COMMIT_MSG, Side.REVISION, createCommentRange(7, 9), "comment", false);
    CommentsUtil.addComments(gApi, changeId, ps1, comment);

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(1);

    // The first few lines of the commit message are the headers, e.g.
    // Parent: ...
    // Author: ...
    // AuthorDate: ...
    // etc...
    assertThat(comments.get(0).contextLines)
        .containsExactlyElementsIn(
            createContextLines(
                "7",
                "Commit Header",
                "8",
                "",
                "9",
                "This commit is doing something extremely important"));
  }

  @Test
  public void commentContextForCommitMessageInvalidLine() throws Exception {
    PushOneCommit.Result result =
        createChange(testRepo, "master", SUBJECT, FILE_NAME, FILE_CONTENT, "topic");
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput comment =
        CommentsUtil.newComment(COMMIT_MSG, Side.REVISION, 100, "comment", false);
    CommentsUtil.addComments(gApi, changeId, ps1, comment);

    Throwable thrown =
        assertThrows(
            UncheckedExecutionException.class,
            () -> gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList());

    assertThat(thrown).hasCauseThat().hasMessageThat().contains("Invalid comment range");
  }

  @Test
  public void listChangeCommentsWithContextEnabled() throws Exception {
    PushOneCommit.Result r1 = createChange();

    ImmutableList.Builder<String> content = ImmutableList.builder();
    for (int i = 1; i <= 10; i++) {
      content.add("line_" + i);
    }

    PushOneCommit.Result r2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                PushOneCommit.SUBJECT,
                FILE_NAME,
                content.build().stream().collect(Collectors.joining("\n")),
                r1.getChangeId())
            .to("refs/for/master");

    CommentsUtil.addCommentOnLine(gApi, r2, "nit: please fix", 1);
    CommentsUtil.addCommentOnRange(gApi, r2, "looks good", createCommentRange(2, 5));

    List<CommentInfo> comments =
        gApi.changes().id(r2.getChangeId()).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(2);

    assertThat(
            comments.stream()
                .filter(c -> c.message.equals("nit: please fix"))
                .collect(MoreCollectors.onlyElement())
                .contextLines)
        .containsExactlyElementsIn(createContextLines("1", "line_1"));

    assertThat(
            comments.stream()
                .filter(c -> c.message.equals("looks good"))
                .collect(MoreCollectors.onlyElement())
                .contextLines)
        .containsExactlyElementsIn(
            createContextLines("2", "line_2", "3", "line_3", "4", "line_4", "5", "line_5"));
  }

  @Test
  public void commentContextForCommentsOnDifferentPatchsets() throws Exception {
    PushOneCommit.Result r1 = createChange();

    ImmutableList.Builder<String> content = ImmutableList.builder();
    for (int i = 1; i <= 10; i++) {
      content.add("line_" + i);
    }

    PushOneCommit.Result r2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                PushOneCommit.SUBJECT,
                FILE_NAME,
                String.join("\n", content.build()),
                r1.getChangeId())
            .to("refs/for/master");

    PushOneCommit.Result r3 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                PushOneCommit.SUBJECT,
                FILE_NAME,
                content.build().stream().collect(Collectors.joining("\n")),
                r1.getChangeId())
            .to("refs/for/master");

    CommentsUtil.addCommentOnLine(gApi, r2, "r2: please fix", 1);
    CommentsUtil.addCommentOnRange(gApi, r2, "r2: looks good", createCommentRange(2, 3));
    CommentsUtil.addCommentOnLine(gApi, r3, "r3: please fix", 6);
    CommentsUtil.addCommentOnRange(gApi, r3, "r3: looks good", createCommentRange(7, 8));

    List<CommentInfo> comments =
        gApi.changes().id(r2.getChangeId()).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(4);

    assertThat(
            comments.stream()
                .filter(c -> c.message.equals("r2: please fix"))
                .collect(MoreCollectors.onlyElement())
                .contextLines)
        .containsExactlyElementsIn(createContextLines("1", "line_1"));

    assertThat(
            comments.stream()
                .filter(c -> c.message.equals("r2: looks good"))
                .collect(MoreCollectors.onlyElement())
                .contextLines)
        .containsExactlyElementsIn(createContextLines("2", "line_2", "3", "line_3"));

    assertThat(
            comments.stream()
                .filter(c -> c.message.equals("r3: please fix"))
                .collect(MoreCollectors.onlyElement())
                .contextLines)
        .containsExactlyElementsIn(createContextLines("6", "line_6"));

    assertThat(
            comments.stream()
                .filter(c -> c.message.equals("r3: looks good"))
                .collect(MoreCollectors.onlyElement())
                .contextLines)
        .containsExactlyElementsIn(createContextLines("7", "line_7", "8", "line_8"));
  }

  @Test
  public void commentContextIsEmptyForPatchsetLevelComments() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput comment =
        CommentsUtil.newCommentWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    CommentsUtil.addComments(gApi, changeId, ps1, comment);

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).contextLines).isEmpty();
  }

  @Test
  public void commentContextWithNumLinesLessThanCommentRange() throws Exception {
    String changeId = createChangeWithComment(3, 6);
    assertContextLines(
        changeId,
        /** numContextLines= */
        1,
        ImmutableList.of(3));
  }

  @Test
  public void commentContextWithNumLinesEqualsToTheCommentRange() throws Exception {
    String changeId = createChangeWithComment(2, 4);
    assertContextLines(
        changeId,
        /** numContextLines= */
        3,
        ImmutableList.of(2, 3, 4));
  }

  @Test
  public void commentContextWithNumLinesGreaterThanCommentRange() throws Exception {
    String changeId = createChangeWithComment(3, 3);
    assertContextLines(
        changeId,
        /** numContextLines= */
        4,
        ImmutableList.of(2, 3, 4, 5));
  }

  @Test
  public void commentContextWithNumLinesLargerThanFileSizeReturnsWholeFile() throws Exception {
    String changeId = createChangeWithComment(3, 3);
    assertContextLines(
        changeId,
        /** numContextLines= */
        20,
        ImmutableList.of(1, 2, 3, 4, 5, 6)); // file only contains six lines.
  }

  @Test
  public void commentContextWithLargeNumLinesReturnsAdjustedMaximum() throws Exception {
    String changeId = createChangeWithCommentLarge(250, 250);
    assertContextLines(
        changeId,
        /** numContextLines= */
        300,
        IntStream.range(201, 301).boxed().collect(ImmutableList.toImmutableList()));
  }

  private String createChangeWithComment(int startLine, int endLine) throws Exception {
    PushOneCommit.Result result =
        createChange(testRepo, "master", SUBJECT, FILE_NAME, FILE_CONTENT, "topic");
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    Comment.Range commentRange = createCommentRange(startLine, endLine);
    CommentInput comment =
        CommentsUtil.newComment(FILE_NAME, Side.REVISION, commentRange, "comment", false);
    CommentsUtil.addComments(gApi, changeId, ps1, comment);
    return changeId;
  }

  private String createChangeWithCommentLarge(int startLine, int endLine) throws Exception {
    StringBuilder largeContent = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      largeContent.append("line " + i + "\n");
    }
    PushOneCommit.Result result =
        createChange(testRepo, "master", SUBJECT, FILE_NAME, largeContent.toString(), "topic");
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    Comment.Range commentRange = createCommentRange(startLine, endLine);
    CommentInput comment =
        CommentsUtil.newComment(FILE_NAME, Side.REVISION, commentRange, "comment", false);
    CommentsUtil.addComments(gApi, changeId, ps1, comment);
    return changeId;
  }

  private void assertContextLines(
      String changeId, int numContextLines, ImmutableList<Integer> expectedLines) throws Exception {
    List<CommentInfo> comments =
        gApi.changes()
            .id(changeId)
            .commentsRequest()
            .withContext(true)
            .numContextLines(numContextLines)
            .getAsList();

    assertThat(comments).hasSize(1);
    assertThat(expectedLines.size()).isLessThan(CommentContextCacheImpl.MAX_NUM_CONTEXT_LINES + 1);
    assertThat(
            comments.get(0).contextLines.stream()
                .map(c -> c.lineNumber)
                .collect(Collectors.toList()))
        .containsExactlyElementsIn(expectedLines);
  }

  private Comment.Range createCommentRange(int startLine, int endLine) {
    Comment.Range range = new Comment.Range();
    range.startLine = startLine;
    range.endLine = endLine;
    return range;
  }

  private List<ContextLineInfo> createContextLines(String... args) {
    List<ContextLineInfo> result = new ArrayList<>();
    for (int i = 0; i < args.length; i += 2) {
      int lineNbr = Integer.parseInt(args[i]);
      String contextLine = args[i + 1];
      ContextLineInfo info = new ContextLineInfo(lineNbr, contextLine);
      result.add(info);
    }
    return result;
  }
}
