// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.CommentContext;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.FixSuggestion;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.comment.CommentContextCache;
import com.google.gerrit.server.comment.CommentContextKey;
import com.google.inject.util.Providers;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommentJsonTest {

  private static final Project.NameKey PROJECT = Project.nameKey("test-project");
  private static final Change.Id CHANGE_ID = Change.id(12345);
  private static final ObjectId COMMIT_ID =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  private AccountLoader.Factory accountLoaderFactory;
  private AccountLoader accountLoader;
  private CommentContextCache commentContextCache;
  private final AtomicInteger cacheGetAllCount = new AtomicInteger(0);

  @Before
  public void setUp() {
    accountLoaderFactory = mock(AccountLoader.Factory.class);
    accountLoader = mock(AccountLoader.class);
    when(accountLoaderFactory.create(true)).thenReturn(accountLoader);
    when(accountLoader.get(any()))
        .thenAnswer(inv -> new AccountInfo(((Account.Id) inv.getArgument(0)).get()));

    commentContextCache = mock(CommentContextCache.class);
    when(commentContextCache.getAll(any()))
        .thenAnswer(
            inv -> {
              cacheGetAllCount.incrementAndGet();
              Iterable<CommentContextKey> keys = inv.getArgument(0);
              ImmutableMap.Builder<CommentContextKey, CommentContext> builder =
                  ImmutableMap.builder();
              for (CommentContextKey key : keys) {
                builder.put(
                    key,
                    CommentContext.create(
                        ImmutableMap.of(1, "line 1 context", 2, "line 2 context"), "text/x-java"));
              }
              return builder.build();
            });
  }

  private CommentJson newCommentJson() {
    return new CommentJson(Providers.of(accountLoaderFactory), Providers.of(commentContextCache))
        .setProjectKey(PROJECT)
        .setChangeId(CHANGE_ID);
  }

  private HumanComment newComment(
      String uuid, String filename, int patchSetId, int line, String message) {
    Comment.Key key = new Comment.Key(uuid, filename, patchSetId);
    HumanComment comment =
        new HumanComment(
            key,
            Account.id(1001),
            Instant.ofEpochMilli(1000000L),
            (short) 1,
            message,
            "serverId",
            /* unresolved= */ false);
    comment.setCommitId(COMMIT_ID);
    comment.lineNbr = line;
    return comment;
  }

  @Test
  public void formatSingleComment() throws Exception {
    CommentJson commentJson = newCommentJson().setFillAccounts(true).setFillPatchSet(true);
    HumanComment comment = newComment("c1", "file1.txt", 1, 10, "test message");

    CommentInfo info = commentJson.newHumanCommentFormatter().format(comment);

    assertThat(info.id).isEqualTo("c1");
    assertThat(info.path).isEqualTo("file1.txt");
    assertThat(info.patchSet).isEqualTo(1);
    assertThat(info.line).isEqualTo(10);
    assertThat(info.message).isEqualTo("test message");
    assertThat(info.author).isNotNull();
    assertThat(info.author._accountId).isEqualTo(1001);
  }

  @Test
  public void formatMapGroupingAndSorting() throws Exception {
    CommentJson commentJson = newCommentJson().setFillAccounts(false).setFillPatchSet(true);
    HumanComment c1 = newComment("c1", "fileA.txt", 1, 20, "msg2");
    HumanComment c2 = newComment("c2", "fileA.txt", 1, 10, "msg1");
    HumanComment c3 = newComment("c3", "fileB.txt", 1, 5, "msg3");

    Map<String, List<CommentInfo>> result =
        commentJson.newHumanCommentFormatter().format(ImmutableList.of(c1, c2, c3));

    assertThat(result.keySet()).containsExactly("fileA.txt", "fileB.txt").inOrder();
    assertThat(result.get("fileA.txt")).hasSize(2);
    assertThat(result.get("fileA.txt").get(0).id).isEqualTo("c2");
    assertThat(result.get("fileA.txt").get(0).line).isEqualTo(10);
    assertThat(result.get("fileA.txt").get(0).path).isNull(); // Path nulled out for map
    assertThat(result.get("fileA.txt").get(1).id).isEqualTo("c1");
    assertThat(result.get("fileA.txt").get(1).line).isEqualTo(20);
    assertThat(result.get("fileA.txt").get(1).path).isNull();

    assertThat(result.get("fileB.txt")).hasSize(1);
    assertThat(result.get("fileB.txt").get(0).id).isEqualTo("c3");
    assertThat(result.get("fileB.txt").get(0).path).isNull();
  }

  @Test
  public void formatWithCommentContext() throws Exception {
    CommentJson commentJson =
        newCommentJson()
            .setFillAccounts(false)
            .setFillPatchSet(true)
            .setFillCommentContext(true)
            .setContextPadding(3);
    HumanComment c1 = newComment("c1", "fileA.txt", 1, 10, "msg1");
    HumanComment c2 = newComment("c2", "fileB.txt", 1, 20, "msg2");

    Map<String, List<CommentInfo>> result =
        commentJson.newHumanCommentFormatter().format(ImmutableList.of(c1, c2));

    assertThat(cacheGetAllCount.get()).isEqualTo(1);
    CommentInfo info1 = result.get("fileA.txt").get(0);
    assertThat(info1.contextLines).hasSize(2);
    assertThat(info1.contextLines.get(0).lineNumber).isEqualTo(1);
    assertThat(info1.contextLines.get(0).contextLine).isEqualTo("line 1 context");
    assertThat(info1.sourceContentType).isEqualTo("text/x-java");
    assertThat(info1.path).isNull();

    CommentInfo info2 = result.get("fileB.txt").get(0);
    assertThat(info2.contextLines).hasSize(2);
    assertThat(info2.contextLines.get(0).lineNumber).isEqualTo(1);
    assertThat(info2.contextLines.get(0).contextLine).isEqualTo("line 1 context");
    assertThat(info2.sourceContentType).isEqualTo("text/x-java");
    assertThat(info2.path).isNull();
  }

  @Test
  public void formatAsListWithCommentContext() throws Exception {
    CommentJson commentJson =
        newCommentJson()
            .setFillAccounts(false)
            .setFillPatchSet(true)
            .setFillCommentContext(true)
            .setContextPadding(2);
    HumanComment c1 = newComment("c1", "fileB.txt", 1, 20, "msg2");
    HumanComment c2 = newComment("c2", "fileA.txt", 1, 10, "msg1");

    ImmutableList<CommentInfo> result =
        commentJson.newHumanCommentFormatter().formatAsList(ImmutableList.of(c1, c2));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id).isEqualTo("c2");
    assertThat(result.get(0).path).isEqualTo("fileA.txt"); // Path preserved in list
    assertThat(result.get(0).contextLines).hasSize(2);
    assertThat(result.get(1).id).isEqualTo("c1");
    assertThat(result.get(1).path).isEqualTo("fileB.txt");
    assertThat(result.get(1).contextLines).hasSize(2);
  }

  @Test
  public void formatWithFixSuggestions() throws Exception {
    CommentJson commentJson = newCommentJson().setFillAccounts(false).setFillPatchSet(true);
    HumanComment c1 = newComment("c1", "fileA.txt", 1, 10, "msg1");

    Comment.Range range = new Comment.Range(10, 2, 10, 8);

    FixReplacement replacement = new FixReplacement("fileA.txt", range, "replacement text");
    FixSuggestion suggestion =
        new FixSuggestion("fix-1", "Fix description", ImmutableList.of(replacement));
    c1.fixSuggestions = ImmutableList.of(suggestion);

    CommentInfo info = commentJson.newHumanCommentFormatter().format(c1);

    assertThat(info.fixSuggestions).hasSize(1);
    assertThat(info.fixSuggestions.get(0).fixId).isEqualTo("fix-1");
    assertThat(info.fixSuggestions.get(0).description).isEqualTo("Fix description");
    assertThat(info.fixSuggestions.get(0).replacements).hasSize(1);
    assertThat(info.fixSuggestions.get(0).replacements.get(0).path).isEqualTo("fileA.txt");
    assertThat(info.fixSuggestions.get(0).replacements.get(0).replacement)
        .isEqualTo("replacement text");
  }
}
