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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Comment;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Function;

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

  private final ImmutableMap<String, T> commentPerUuid;
  private final Map<String, ImmutableSet<T>> childrenPerParent;

  public CommentThreads(
      ImmutableMap<String, T> commentPerUuid, Map<String, ImmutableSet<T>> childrenPerParent) {
    this.commentPerUuid = commentPerUuid;
    this.childrenPerParent = childrenPerParent;
  }

  public static <T extends Comment> CommentThreads<T> forComments(Iterable<T> comments) {
    ImmutableMap<String, T> commentPerUuid =
        Streams.stream(comments)
            .distinct()
            .collect(ImmutableMap.toImmutableMap(comment -> comment.key.uuid, Function.identity()));

    Map<String, ImmutableSet<T>> childrenPerParent =
        commentPerUuid.values().stream()
            .filter(comment -> comment.parentUuid != null)
            .collect(groupingBy(comment -> comment.parentUuid, toImmutableSet()));
    return new CommentThreads<>(commentPerUuid, childrenPerParent);
  }

  /**
   * Returns all comments organized into threads.
   *
   * <p>Comments appear only once.
   */
  public ImmutableSet<CommentThread<T>> getThreads() {
    ImmutableSet<T> roots =
        commentPerUuid.values().stream().filter(this::isRoot).collect(toImmutableSet());

    return buildThreadsOf(roots);
  }

  /**
   * Returns only the comment threads to which the specified comments are a reply.
   *
   * <p>If the specified child comments are part of the comments originally provided to {@link
   * CommentThreads#forComments(Iterable)}, they will also appear in the returned comment threads.
   * They don't need to be part of the originally provided comments, though, but should refer to one
   * of these comments via their {@link Comment#parentUuid}. Child comments not referring to any
   * known comments will be ignored.
   *
   * @param childComments comments for which the matching threads should be determined
   * @return threads to which the provided child comments are a reply
   */
  public ImmutableSet<CommentThread<T>> getThreadsForChildren(Iterable<? extends T> childComments) {
    ImmutableSet<T> relevantRoots =
        Streams.stream(childComments)
            .map(this::findRoot)
            .filter(root -> commentPerUuid.containsKey(root.key.uuid))
            .collect(toImmutableSet());
    return buildThreadsOf(relevantRoots);
  }

  private T findRoot(T comment) {
    T current = comment;
    while (!isRoot(current)) {
      current = commentPerUuid.get(current.parentUuid);
    }
    return current;
  }

  private boolean isRoot(T current) {
    return current.parentUuid == null || !commentPerUuid.containsKey(current.parentUuid);
  }

  private ImmutableSet<CommentThread<T>> buildThreadsOf(ImmutableSet<T> roots) {
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
