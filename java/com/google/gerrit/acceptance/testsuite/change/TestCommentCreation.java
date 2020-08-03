/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.acceptance.testsuite.change;

import com.google.auto.value.AutoValue;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.acceptance.testsuite.change.TestRange.Position;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Attributes of the comment. If not provided, arbitrary values will be used. */
@AutoValue
public abstract class TestCommentCreation {

  public abstract Optional<String> message();

  public abstract Optional<String> file();

  public abstract Optional<Integer> line();

  public abstract Optional<TestRange> range();

  public abstract Optional<CommentSide> side();

  public abstract Optional<Boolean> unresolved();

  public abstract Optional<String> parentUuid();

  abstract ThrowingFunction<TestCommentCreation, String> commentCreator();

  public static TestCommentCreation.Builder builder(
      ThrowingFunction<TestCommentCreation, String> commentCreator) {
    return new AutoValue_TestCommentCreation.Builder().commentCreator(commentCreator);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public Builder noMessage() {
      return message("");
    }

    /** Message text of the comment. */
    public abstract Builder message(String message);

    /** Indicates a patchset-level comment. */
    public Builder onPatchsetLevel() {
      return file(Patch.PATCHSET_LEVEL);
    }

    /** Indicates a file comment. The comment will be on the specified file. */
    public Builder onFileLevelOf(String filePath) {
      return file(filePath).line(null).range(null);
    }

    /**
     * Starts the fluent change to create a line comment. The line comment will be at the indicated
     * line. Lines start with 1.
     */
    public FileBuilder onLine(int line) {
      return new FileBuilder(file -> file(file).line(line).range(null));
    }

    /**
     * Starts the fluent chain to create a range comment. The range begins at the specified line.
     * Lines start at 1. The start position (line, charOffset) is inclusive, the end position (line,
     * charOffset) is exclusive.
     */
    public PositionBuilder<StartAwarePositionBuilder> fromLine(int startLine) {
      return new PositionBuilder<>(
          startCharOffset -> {
            Position start = Position.builder().line(startLine).charOffset(startCharOffset).build();
            TestRange.Builder testRangeBuilder = TestRange.builder().setStart(start);
            return new StartAwarePositionBuilder(this, testRangeBuilder);
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
    public Builder onParentCommit() {
      return side(CommentSide.PARENT_COMMIT);
    }

    /** Like {@link #onParentCommit()} but for the second parent of a merge commit. */
    public Builder onSecondParentCommit() {
      return side(CommentSide.SECOND_PARENT_COMMIT);
    }

    /**
     * Like {@link #onParentCommit()} but for the AutoMerge commit created from the parents of a
     * merge commit.
     */
    public Builder onAutoMergeCommit() {
      return side(CommentSide.AUTO_MERGE_COMMIT);
    }

    abstract Builder side(CommentSide side);

    /** Indicates a resolved comment. */
    public Builder resolved() {
      return unresolved(false);
    }

    /** Indicates an unresolved comment. */
    public Builder unresolved() {
      return unresolved(true);
    }

    abstract Builder unresolved(boolean unresolved);

    /**
     * UUID of another comment to which this comment is a reply. This comment must have similar
     * attributes (e.g. file, line, side) as the parent comment.
     */
    public abstract Builder parentUuid(String parentUuid);

    abstract TestCommentCreation.Builder commentCreator(
        ThrowingFunction<TestCommentCreation, String> commentCreator);

    abstract TestCommentCreation autoBuild();

    /**
     * Creates the comment.
     *
     * @return the UUID of the created comment
     */
    public String create() {
      TestCommentCreation commentCreation = autoBuild();
      return commentCreation.commentCreator().applyAndThrowSilently(commentCreation);
    }
  }

  /** Builder for the file specification of line/range comments. */
  public static class FileBuilder {
    private final Function<String, Builder> nextStepProvider;

    private FileBuilder(Function<String, Builder> nextStepProvider) {
      this.nextStepProvider = nextStepProvider;
    }

    /** File on which the comment should be added. */
    public Builder ofFile(String file) {
      return nextStepProvider.apply(file);
    }
  }

  /** Builder to simplify a position specification. */
  public static class PositionBuilder<T> {
    private final IntFunction<T> nextStepProvider;

    private PositionBuilder(IntFunction<T> nextStepProvider) {
      this.nextStepProvider = nextStepProvider;
    }

    /** Character offset within the line. A value of 0 indicates the beginning of the line. */
    public T charOffset(int characterOffset) {
      return nextStepProvider.apply(characterOffset);
    }
  }

  /** Builder for the end position of a range. */
  public static class StartAwarePositionBuilder {
    private final TestCommentCreation.Builder testCommentCreationBuilder;
    private final TestRange.Builder testRangeBuilder;

    private StartAwarePositionBuilder(
        Builder testCommentCreationBuilder, TestRange.Builder testRangeBuilder) {
      this.testCommentCreationBuilder = testCommentCreationBuilder;
      this.testRangeBuilder = testRangeBuilder;
    }

    /** Line of the end position of the range. */
    public PositionBuilder<FileBuilder> toLine(int endLine) {
      return new PositionBuilder<>(
          endCharOffset -> {
            Position end = Position.builder().line(endLine).charOffset(endCharOffset).build();
            TestRange range = testRangeBuilder.setEnd(end).build();
            testCommentCreationBuilder.range(range);
            return new FileBuilder(testCommentCreationBuilder::file);
          });
    }
  }

  enum CommentSide {
    PATCHSET_COMMIT(1),
    AUTO_MERGE_COMMIT(0),
    PARENT_COMMIT(-1),
    SECOND_PARENT_COMMIT(-2);

    private final short numericSide;

    CommentSide(int numericSide) {
      this.numericSide = (short) numericSide;
    }

    public short getNumericSide() {
      return numericSide;
    }
  }
}
