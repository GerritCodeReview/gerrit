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
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.acceptance.testsuite.change.TestRange.Position;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Patch;
import java.util.Map;
import java.util.Optional;

/**
 * Attributes of the robot comment. If not provided, arbitrary values will be used. This class is
 * very similar to {@link TestCommentCreation} to allow separation between robot and human comments.
 */
@AutoValue
public abstract class TestRobotCommentCreation {

  public abstract Optional<String> message();

  public abstract Optional<String> file();

  public abstract Optional<Integer> line();

  public abstract Optional<TestRange> range();

  public abstract Optional<CommentSide> side();

  public abstract Optional<String> parentUuid();

  public abstract Optional<String> tag();

  public abstract Optional<Account.Id> author();

  public abstract Optional<String> robotId();

  public abstract Optional<String> robotRunId();

  public abstract Optional<String> url();

  public abstract ImmutableMap<String, String> properties();

  abstract ThrowingFunction<TestRobotCommentCreation, String> commentCreator();

  public static Builder builder(ThrowingFunction<TestRobotCommentCreation, String> commentCreator) {
    return new AutoValue_TestRobotCommentCreation.Builder().commentCreator(commentCreator);
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

    /** Id of the robot that created the comment. */
    public abstract Builder robotId(String robotId);

    /** An ID of the run of the robot that created the comment. */
    public abstract Builder robotRunId(String robotRunId);

    /** Url for more information for the robot comment. */
    public abstract Builder url(String url);

    /** Robot specific properties as map that maps arbitrary keys to values. */
    public abstract Builder properties(Map<String, String> properties);

    abstract ImmutableMap.Builder<String, String> propertiesBuilder();

    public Builder addProperty(String key, String value) {
      propertiesBuilder().put(key, value);
      return this;
    }

    abstract Builder commentCreator(
        ThrowingFunction<TestRobotCommentCreation, String> commentCreator);

    abstract TestRobotCommentCreation autoBuild();

    /**
     * Creates the robot comment.
     *
     * @return the UUID of the created robot comment
     */
    public String create() {
      TestRobotCommentCreation commentCreation = autoBuild();
      return commentCreation.commentCreator().applyAndThrowSilently(commentCreation);
    }
  }
}
