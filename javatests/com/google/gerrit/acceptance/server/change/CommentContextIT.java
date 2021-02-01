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
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MoreCollectors;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.ContextLineInfo;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CommentContextIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setUp() {
    requestScopeOperations.setApiUser(user.id());
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
                SUBJECT,
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
                SUBJECT,
                FILE_NAME,
                String.join("\n", content.build()),
                r1.getChangeId())
            .to("refs/for/master");

    PushOneCommit.Result r3 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                SUBJECT,
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
