package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import java.util.Comparator;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class DefaultSubmitRequirementsIT extends AbstractDaemonTest {
  /**
   * Tests the "No-Unresolved-Comments" submit requirement that is created during the site
   * initialization.
   */
  @Test
  public void cannotSubmitChangeWithUnresolvedComment() throws Exception {
    TestRepository<InMemoryRepository> repo = cloneProject(project);
    PushOneCommit.Result r =
        createChange(repo, "master", "Add a file", "foo", "content", /* topic= */ null);
    String changeId = r.getChangeId();
    CommentInfo commentInfo =
        addComment(changeId, "foo", "message", /* unresolved= */ true, /* inReplyTo= */ null);
    assertThat(commentInfo.unresolved).isTrue();
    approve(changeId);
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Failed to submit 1 change due to the following problems:\n"
                    + "Change %s: submit requirement 'No-Unresolved-Comments' is unsatisfied.",
                r.getChange().getId().get()));

    // Resolve the comment and check that the change can be submitted now.
    CommentInfo commentInfo2 =
        addComment(
            changeId, "foo", "reply", /* unresolved= */ false, /* inReplyTo= */ commentInfo.id);
    assertThat(commentInfo2.unresolved).isFalse();
    gApi.changes().id(changeId).current().submit();
  }

  @CanIgnoreReturnValue
  private CommentInfo addComment(
      String changeId, String file, String message, boolean unresolved, @Nullable String inReplyTo)
      throws Exception {
    ReviewInput in = new ReviewInput();
    CommentInput commentInput = new CommentInput();
    commentInput.path = file;
    commentInput.line = 1;
    commentInput.message = message;
    commentInput.unresolved = unresolved;
    commentInput.inReplyTo = inReplyTo;
    in.comments = ImmutableMap.of(file, ImmutableList.of(commentInput));
    gApi.changes().id(changeId).current().review(in);

    return gApi.changes().id(changeId).commentsRequest().getAsList().stream()
        .filter(commentInfo -> commentInput.message.equals(commentInfo.message))
        // if there are multiple comments with the same message, take the one was created last
        .max(
            Comparator.comparing(commentInfo1 -> commentInfo1.updated.toInstant().getEpochSecond()))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("comment '%s' not found", commentInput.message)));
  }
}
