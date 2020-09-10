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
import static java.util.stream.Collectors.groupingBy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Comment;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Identifier of comment threads.
 *
 * <p>Comments are ordered into threads according to their parent relationship indicated via {@link
 * Comment#parentUuid}. It's possible that two comments refer to the same parent, which especially
 * happens when two persons reply in parallel. If such branches exist, we merge them into a flat
 * list taking the comment creation date ({@link Comment#writtenOn} into account (but still
 * preserving the general parent order). Remaining ties are resolved by using the natural order of
 * the comment UUID, which is unique.
 *
 * @param <T> type of comments in the threads. Can also be {@link Comment} if the threads mix
 *     comments of different types.
 */
public class CommentThreads<T extends Comment> {

  private final Iterable<T> comments;

  private CommentThreads(Iterable<T> comments) {
    this.comments = comments;
  }

  public static <T extends Comment> CommentThreads<T> forComments(Iterable<T> comments) {
    return new CommentThreads<>(comments);
  }

  public ImmutableSet<CommentThread<T>> getThreads() {
    ImmutableSet<String> commentUuids =
        Streams.stream(comments).map(comment -> comment.key.uuid).collect(toImmutableSet());

    ImmutableSet<T> roots =
        Streams.stream(comments)
            .filter(
                comment -> comment.parentUuid == null || !commentUuids.contains(comment.parentUuid))
            .collect(toImmutableSet());

    Map<String, ImmutableSet<T>> childrenPerParent =
        Streams.stream(comments)
            .filter(comment -> comment.parentUuid != null)
            .collect(groupingBy(comment -> comment.parentUuid, toImmutableSet()));

    return roots.stream()
        .map(root -> buildCommentThread(root, childrenPerParent))
        .collect(toImmutableSet());
  }

  private static <T extends Comment> CommentThread<T> buildCommentThread(
      T root, Map<String, ImmutableSet<T>> childrenPerParent) {
    CommentThread.Builder<T> commentThread = CommentThread.builder();
    // Expand comments gradually from the root. If there is more than one child per level, place the
    // earlier-created child earlier in the thread. Break ties with the UUID to be deterministic.
    Queue<T> unvisited =
        new PriorityQueue<>(
            Comparator.comparing((T comment) -> comment.writtenOn)
                .thenComparing(comment -> comment.key.uuid));
    unvisited.add(root);
    while (!unvisited.isEmpty()) {
      T nextComment = unvisited.remove();
      commentThread.addComment(nextComment);
      ImmutableSet<T> children =
          childrenPerParent.getOrDefault(nextComment.key.uuid, ImmutableSet.of());
      unvisited.addAll(children);
    }
    return commentThread.build();
  }
}
