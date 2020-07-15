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

package com.google.gerrit.server;

import static java.util.stream.Collectors.groupingBy;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.CommentContext;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.ContextLineInfo;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.Text;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Computes the list of {@link ContextLineInfo} for a given comment, that is, the lines of the
 * source file surrounding and including the area where the comment was written.
 */
public class CommentContextLoader {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final Project.NameKey project;

  public interface Factory {
    CommentContextLoader create(Project.NameKey project);
  }

  @Inject
  public CommentContextLoader(GitRepositoryManager repoManager, @Assisted Project.NameKey project) {
    this.repoManager = repoManager;
    this.project = project;
  }

  /**
   * Load the comment context for multiple comments at once. This method will open the repository
   * and read the source files for all necessary comments' file paths.
   *
   * @param comments a list of comments.
   * @return a Map where all entries consist of the input comments and the values are their
   *     corresponding {@link CommentContext}.
   */
  public Map<Comment, CommentContext> getContext(Iterable<Comment> comments) {
    ImmutableMap.Builder<Comment, CommentContext> result =
        ImmutableMap.builderWithExpectedSize(Iterables.size(comments));

    // Group comments by commit ID so that each commit is parsed only once
    Map<ObjectId, List<Comment>> commentsByCommitId =
        Streams.stream(comments).collect(groupingBy(Comment::getCommitId));

    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      for (ObjectId commitId : commentsByCommitId.keySet()) {
        RevCommit commit = rw.parseCommit(commitId);
        for (Comment comment : commentsByCommitId.get(commitId)) {
          Optional<Range> range = getStartAndEndLines(comment);
          if (!range.isPresent()) {
            continue;
          }
          // TODO(ghareeb): We can further group the comments by file paths to avoid opening
          // the same file multiple times.
          try (TreeWalk tw =
              TreeWalk.forPath(rw.getObjectReader(), comment.key.filename, commit.getTree())) {
            if (tw == null) {
              logger.atWarning().log(
                  "Failed to find path %s in the git tree of ID %s.",
                  comment.key.filename, commit.getTree().getId());
              continue;
            }
            ObjectId id = tw.getObjectId(0);
            Text src = new Text(repo.open(id, Constants.OBJ_BLOB));
            Range r = range.get();
            ImmutableMap.Builder<Integer, String> context =
                ImmutableMap.builderWithExpectedSize(r.end() - r.start());
            for (int i = r.start(); i < r.end(); i++) {
              context.put(i, src.getString(i - 1));
            }
            result.put(comment, CommentContext.create(context.build()));
          }
        }
      }
      return result.build();
    } catch (IOException e) {
      throw new StorageException("Failed to load the comment context", e);
    }
  }

  private static Optional<Range> getStartAndEndLines(Comment comment) {
    if (comment.range != null) {
      return Optional.of(Range.create(comment.range.startLine, comment.range.endLine + 1));
    } else if (comment.lineNbr > 0) {
      return Optional.of(Range.create(comment.lineNbr, comment.lineNbr + 1));
    }
    return Optional.empty();
  }

  @AutoValue
  abstract static class Range {
    static Range create(int start, int end) {
      return new AutoValue_CommentContextLoader_Range(start, end);
    }

    /** Start line of the comment (inclusive). */
    abstract int start();

    /** End line of the comment (exclusive). */
    abstract int end();
  }
}
