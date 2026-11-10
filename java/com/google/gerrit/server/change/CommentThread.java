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

package com.google.gerrit.server.change;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Representation of a comment thread.
 *
 * <p>A comment thread consists of at least one comment.
 *
 * @param <T> type of comments in the thread. Can also be {@link Comment} if the thread mixes
 *     comments of different types.
 */
@AutoValue
public abstract class CommentThread<T extends Comment> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Comments in the thread in exactly the order they appear in the thread. */
  public abstract ImmutableList<T> comments();

  /** Returns the root comment that started the thread. */
  public T root() {
    return comments().get(0);
  }

  /** Returns the number of comments in the thread. */
  public int size() {
    return comments().size();
  }

  /** Returns the file path associated with the root comment of the thread. */
  public String filename() {
    return root().key.filename;
  }

  /** Returns the diff side associated with the root comment of the thread. */
  public short side() {
    return root().side;
  }

  /**
   * Returns the unique author account IDs across the thread in first-appearance order, ignoring
   * comments without an author identity.
   */
  public ImmutableSet<Account.Id> participants() {
    return comments().stream()
        .map(comment -> comment.author)
        .filter(Objects::nonNull)
        .map(Comment.Identity::getId)
        .collect(toImmutableSet());
  }

  /** Returns the distinct patch set numbers referenced by comments in this thread, in order. */
  public ImmutableSet<Integer> patchSetIds() {
    return comments().stream().map(comment -> comment.key.patchSetId).collect(toImmutableSet());
  }

  /** Whether the whole thread is considered as unresolved. */
  public boolean unresolved() {
    Optional<HumanComment> lastHumanComment =
        Streams.findLast(
            comments().stream()
                .filter(HumanComment.class::isInstance)
                .map(HumanComment.class::cast));
    if (lastHumanComment.isPresent()) {
      logger.atFine().log(
          "last human comment in comment thread: %s -> {parentUuid = %s, unresolved = %s}",
          lastHumanComment.get().key,
          lastHumanComment.get().parentUuid,
          lastHumanComment.get().unresolved);
    } else {
      logger.atFine().log("no human comment in comment thread");
    }

    // We often use false == null for boolean fields. It's also a safe fall-back if no human comment
    // is part of the thread.
    return lastHumanComment.map(comment -> comment.unresolved).orElse(false);
  }

  public static <T extends Comment> Builder<T> builder() {
    return new AutoValue_CommentThread.Builder<>();
  }

  @AutoValue.Builder
  public abstract static class Builder<T extends Comment> {

    public abstract Builder<T> comments(List<T> value);

    @CanIgnoreReturnValue
    public Builder<T> addComment(T comment) {
      commentsBuilder().add(comment);
      return this;
    }

    abstract ImmutableList.Builder<T> commentsBuilder();

    abstract ImmutableList<T> comments();

    abstract CommentThread<T> autoBuild();

    public CommentThread<T> build() {
      Preconditions.checkState(
          !comments().isEmpty(), "A comment thread must contain at least one comment.");
      return autoBuild();
    }
  }
}
