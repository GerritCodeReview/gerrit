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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MoreCollectors;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.ContextLineInfo;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.ObjectId;
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
  private static final ObjectId dummyCommit =
      ObjectId.fromString("93e2901bc0b4719ef6081ee6353b49c9cdd97614");

  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setup() throws Exception {
    requestScopeOperations.setApiUser(user.id());
  }

  @Test
  public void commentContextForGitSubmoduleFiles() throws Exception {
    String submodulePath = "submodule_path";

    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo).addGitSubmodule(submodulePath, dummyCommit);
    PushOneCommit.Result pushResult = push.to("refs/for/master");
    String changeId = pushResult.getChangeId();
    CommentInput comment =
        CommentsUtil.newComment(submodulePath, Side.REVISION, 1, "comment", false);
    CommentsUtil.addComments(gApi, changeId, pushResult.getCommit().name(), comment);

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();
    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).path).isEqualTo(submodulePath);
    assertThat(comments.get(0).contextLines)
        .isEqualTo(createContextLines("1", "Subproject commit " + dummyCommit.getName()));
  }

  @Test
  public void commentContextForRootCommitOnParentSideReturnsEmptyContext() throws Exception {
    // Create a change in a new branch, making the patchset commit a root commit
    ChangeInfo changeInfo = createChangeInNewBranch("newBranch");
    String changeId = changeInfo.changeId;
    String revision = changeInfo.revisions.keySet().iterator().next();

    // Write a comment on the parent side of the commit message. Set parent=1 because if unset, our
    // handler in PostReview assumes we want to write on the auto-merge commit and fails the
    // pre-condition.
    CommentInput comment = CommentsUtil.newComment(COMMIT_MSG, Side.PARENT, 0, "comment", false);
    comment.parent = 1;
    CommentsUtil.addComments(gApi, changeId, revision, comment);

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();
    assertThat(comments).hasSize(1);
    CommentInfo c = comments.stream().collect(MoreCollectors.onlyElement());
    assertThat(c.commitId).isEqualTo(ObjectId.zeroId().name());
    assertThat(c.contextLines).isEmpty();
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

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();
    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).contextLines).isEmpty();
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
  public void listChangeDraftsWithContextEnabled() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                PushOneCommit.SUBJECT,
                FILE_NAME,
                "line_1\nline_2\nline_3",
                r1.getChangeId())
            .to("refs/for/master");

    DraftInput in = CommentsUtil.newDraft(FILE_NAME, Side.REVISION, 2, "comment 1");
    gApi.changes().id(r2.getChangeId()).revision(r2.getCommit().name()).createDraft(in);

    // Test the getAsList interface
    List<CommentInfo> comments =
        gApi.changes().id(r2.getChangeId()).draftsRequest().withContext(true).getAsList();
    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).message).isEqualTo("comment 1");
    assertThat(comments.get(0).contextLines)
        .containsExactlyElementsIn(createContextLines("2", "line_2"));

    // Also test the get interface
    Map<String, List<CommentInfo>> commentsMap =
        gApi.changes().id(r2.getChangeId()).draftsRequest().withContext(true).get();
    assertThat(commentsMap).hasSize(1);
    assertThat(commentsMap.values().iterator().next()).hasSize(1);
    CommentInfo onlyComment = commentsMap.values().iterator().next().get(0);
    assertThat(onlyComment.message).isEqualTo("comment 1");
    assertThat(onlyComment.contextLines)
        .containsExactlyElementsIn(createContextLines("2", "line_2"));
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
  public void commentContextWithZeroPadding() throws Exception {
    String changeId = createChangeWithComment(3, 4);
    assertContextLines(changeId, /* contextPadding= */ 0, ImmutableList.of(3, 4));
  }

  @Test
  public void commentContextWithSmallPadding() throws Exception {
    String changeId = createChangeWithComment(3, 4);
    assertContextLines(changeId, /* contextPadding= */ 1, ImmutableList.of(2, 3, 4, 5));
  }

  @Test
  public void commentContextWithSmallPaddingAtTheBeginningOfFile() throws Exception {
    String changeId = createChangeWithComment(1, 2);
    assertContextLines(changeId, /* contextPadding= */ 2, ImmutableList.of(1, 2, 3, 4));
  }

  @Test
  public void commentContextWithPaddingLargerThanFileSize() throws Exception {
    String changeId = createChangeWithComment(3, 3);
    assertContextLines(
        changeId,
        /* contextPadding= */ 20,
        ImmutableList.of(1, 2, 3, 4, 5, 6)); // file only contains six lines.
  }

  @Test
  public void commentContextWithLargePaddingReturnsAdjustedMaximumPadding() throws Exception {
    String changeId = createChangeWithCommentLarge(250, 250);
    assertContextLines(
        changeId,
        /* contextPadding= */ 300,
        IntStream.range(200, 301).boxed().collect(ImmutableList.toImmutableList()));
  }

  @Test
  public void commentContextReturnsCorrectContentTypeForCommitMessage() throws Exception {
    PushOneCommit.Result result =
        createChange(testRepo, "master", SUBJECT, FILE_NAME, FILE_CONTENT, "topic");
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput comment = CommentsUtil.newComment(COMMIT_MSG, Side.REVISION, 7, "comment", false);
    CommentsUtil.addComments(gApi, changeId, ps1, comment);

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).path).isEqualTo(COMMIT_MSG);
    assertThat(comments.get(0).sourceContentType)
        .isEqualTo(FileContentUtil.TEXT_X_GERRIT_COMMIT_MESSAGE);
  }

  @Test
  public void commentContextReturnsCorrectContentType_Java() throws Exception {
    String javaContent =
        "public class Main {\n"
            + " public static void main(String[]args){\n"
            + " if(args==null){\n"
            + " System.err.println(\"Something\");\n"
            + " }\n"
            + " }\n"
            + " }";
    String fileName = "src.java";
    String changeId = createChangeWithContent(fileName, javaContent, /* line= */ 4);

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).path).isEqualTo(fileName);
    assertThat(comments.get(0).contextLines)
        .isEqualTo(createContextLines("4", " System.err.println(\"Something\");"));
    assertThat(comments.get(0).sourceContentType).isEqualTo("text/x-java");
  }

  @Test
  public void commentContextReturnsCorrectContentType_Cpp() throws Exception {
    String cppContent =
        "#include <iostream>\n"
            + "\n"
            + "int main() {\n"
            + "    std::cout << \"Hello World!\";\n"
            + "    return 0;\n"
            + "}";
    String fileName = "src.cpp";
    String changeId = createChangeWithContent(fileName, cppContent, /* line= */ 4);

    List<CommentInfo> comments =
        gApi.changes().id(changeId).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).path).isEqualTo(fileName);
    assertThat(comments.get(0).contextLines)
        .isEqualTo(createContextLines("4", "    std::cout << \"Hello World!\";"));
    assertThat(comments.get(0).sourceContentType).isEqualTo("text/x-c++src");
  }

  @Test
  public void listChangeCommentsWithContextEnabled_twoRangeCommentsWithTheSameContext()
      throws Exception {
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

    CommentsUtil.addCommentOnRange(gApi, r2, "looks good", createCommentRange(2, 5));
    CommentsUtil.addCommentOnRange(gApi, r2, "are you sure?", createCommentRange(2, 5));

    List<CommentInfo> comments =
        gApi.changes().id(r2.getChangeId()).commentsRequest().withContext(true).getAsList();

    assertThat(comments).hasSize(2);

    assertThat(
            comments.stream()
                .filter(c -> c.message.equals("looks good"))
                .collect(MoreCollectors.onlyElement())
                .contextLines)
        .containsExactlyElementsIn(
            createContextLines("2", "line_2", "3", "line_3", "4", "line_4", "5", "line_5"));

    assertThat(
            comments.stream()
                .filter(c -> c.message.equals("are you sure?"))
                .collect(MoreCollectors.onlyElement())
                .contextLines)
        .containsExactlyElementsIn(
            createContextLines("2", "line_2", "3", "line_3", "4", "line_4", "5", "line_5"));
  }

  private String createChangeWithContent(String fileName, String fileContent, int line)
      throws Exception {
    PushOneCommit.Result result =
        createChange(testRepo, "master", SUBJECT, fileName, fileContent, "topic");
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput comment = CommentsUtil.newComment(fileName, Side.REVISION, line, "comment", false);
    CommentsUtil.addComments(gApi, changeId, ps1, comment);
    return changeId;
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
      String changeId, int contextPadding, ImmutableList<Integer> expectedLines) throws Exception {
    List<CommentInfo> comments =
        gApi.changes()
            .id(changeId)
            .commentsRequest()
            .withContext(true)
            .contextPadding(contextPadding)
            .getAsList();

    assertThat(comments).hasSize(1);
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

  private ChangeInfo createChangeInNewBranch(String branchName) throws Exception {
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = branchName;
    in.newBranch = true;
    in.subject = "New changes";
    return gApi.changes().create(in).get();
  }
}
