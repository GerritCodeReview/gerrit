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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.acceptance.testsuite.change.TestRange.Position;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.Patch;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** Attributes of the human comment. If not provided, arbitrary values will be used.. */
@AutoValue
public abstract class TestCommentCreation {

  public abstract Optional<String> message();

  public abstract Optional<String> file();

  public abstract Optional<Integer> line();

  public abstract Optional<TestRange> range();

  public abstract Optional<CommentSide> side();

  public abstract Optional<Boolean> unresolved();

  public abstract Optional<String> parentUuid();

  public abstract Optional<String> tag();

  public abstract Optional<Account.Id> author();

  public abstract Optional<Instant> createdOn();

  abstract Comment.Status status();

  abstract ThrowingFunction<TestCommentCreation, String> commentCreator();

  public static Builder builder(
      ThrowingFunction<TestCommentCreation, String> commentCreator, Comment.Status commentStatus) {
    return new AutoValue_TestCommentCreation.Builder()
        .commentCreator(commentCreator)
        .status(commentStatus);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    @CanIgnoreReturnValue
    public Builder noMessage() {
      return message("");
    }

    /** Message text of the comment. */
    public abstract Builder message(String message);

    /** Indicates a patchset-level comment. */
    @CanIgnoreReturnValue
    public Builder onPatchsetLevel() {
      return file(Patch.PATCHSET_LEVEL);
    }

    /** Indicates a file comment. The comment will be on the specified file. */
    @CanIgnoreReturnValue
    public Builder onFileLevelOf(String filePath) {
      return file(filePath).line(null).range(null);
    }

    /**
     * Starts the fluent change to create a line comment. The line comment will be at the indicated
     * line. Lines start with 1.
     */
    public FileBuilder<Builder> onLine(int line) {
      return new FileBuilder<>(file -> file(file).line(line).range(null));
    }

    /**
     * Starts the fluent chain to create a range comment. The range begins at the specified line.
     * Lines start at 1. The start position (line, charOffset) is inclusive, the end position (line,
     * charOffset) is exclusive.
     */
    public PositionBuilder<StartAwarePositionBuilder<Builder>> fromLine(int startLine) {
      return new PositionBuilder<>(
          startCharOffset -> {
            Position start = Position.builder().line(startLine).charOffset(startCharOffset).build();
            TestRange.Builder testRangeBuilder = TestRange.builder().setStart(start);
            return new StartAwarePositionBuilder<>(testRangeBuilder, this::range, this::file);
          });
    }

    /** File on which the comment should be added. */
    abstract Builder file(String filePath);

    /** Line on which the comment should be added. */
    abstract Builder line(@Nullable Integer line);

    /** Range on which the comment should be added. */
    abstract Builder range(@Nullable TestRange range);

    /**
     * Indicates that the comment refers to a file, line, range, ... in the commit of the patchset.
     *
     * <p>On the UI, such comments are shown on the right side of a diff view when a diff against
     * base is selected. See {@link #onParentCommit()} for comments shown on the left side.
     */
    @CanIgnoreReturnValue
    public Builder onPatchsetCommit() {
      return side(CommentSide.PATCHSET_COMMIT);
    }

    /**
     * Indicates that the comment refers to a file, line, range, ... in the parent commit of the
     * patchset.
     *
     * <p>On the UI, such comments are shown on the left side of a diff view when a diff against
     * base is selected. See {@link #onPatchsetCommit()} for comments shown on the right side.
     *
     * <p>For merge commits, this indicates the first parent commit.
     */
    @CanIgnoreReturnValue
    public Builder onParentCommit() {
      return side(CommentSide.PARENT_COMMIT);
    }

    /** Like {@link #onParentCommit()} but for the second parent of a merge commit. */
    @CanIgnoreReturnValue
    public Builder onSecondParentCommit() {
      return side(CommentSide.SECOND_PARENT_COMMIT);
    }

    /**
     * Like {@link #onParentCommit()} but for the AutoMerge commit created from the parents of a
     * merge commit.
     */
    @CanIgnoreReturnValue
    public Builder onAutoMergeCommit() {
      return side(CommentSide.AUTO_MERGE_COMMIT);
    }

    abstract Builder side(CommentSide side);

    /** Indicates a resolved comment. */
    @CanIgnoreReturnValue
    public Builder resolved() {
      return unresolved(false);
    }

    /** Indicates an unresolved comment. */
    @CanIgnoreReturnValue
    public Builder unresolved() {
      return unresolved(true);
    }

    abstract Builder unresolved(boolean unresolved);

    /**
     * UUID of another comment to which this comment is a reply. This comment must have similar
     * attributes (e.g. file, line, side) as the parent comment. The parent comment must be a
     * published comment.
     */
    public abstract Builder parentUuid(String parentUuid);

    /** Tag to attach to the comment. */
    public abstract Builder tag(String value);

    /** Author of the comment. Must be an existing user account. */
    public abstract Builder author(Account.Id accountId);

    /**
     * Creation time of the comment. Like {@link #createdOn(Instant)} but with an arbitrary, fixed
     * time zone (-> deterministic test execution).
     */
    public Builder createdOn(LocalDateTime createdOn) {
      // We don't care about the exact time zone in most tests, just that it's fixed so that tests
      // are deterministic.
      return createdOn(createdOn.atZone(ZoneOffset.UTC).toInstant());
    }

    /**
     * Creation time of the comment. This may also lie in the past or future. Comments stored in
     * NoteDb support only second precision.
     */
    public abstract Builder createdOn(Instant createdOn);

    /**
     * Status of the comment. Hidden in the API surface. Use {@link
     * PerPatchsetOperations#newComment()} or {@link PerPatchsetOperations#newDraftComment()}
     * depending on which type of comment you want to create.
     */
    abstract Builder status(Comment.Status value);

    abstract Builder commentCreator(ThrowingFunction<TestCommentCreation, String> commentCreator);

    abstract TestCommentCreation autoBuild();

    /**
     * Creates the comment.
     *
     * @return the UUID of the created comment
     */
    @CanIgnoreReturnValue
    public String create() {
      TestCommentCreation commentCreation = autoBuild();
      return commentCreation.commentCreator().applyAndThrowSilently(commentCreation);
    }
  }
}
