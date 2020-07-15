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
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.ContextLine;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.Text;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Computes the list of {@link ContextLine} for a given comment, that is, the lines of the source
 * file surrounding and including the area where the comment was written.
 */
public class CommentContextLoader {
  private final GitRepositoryManager repoManager;
  private final Project.NameKey project;
  private Map<ContextData, List<ContextLine>> candidates;

  public interface Factory {
    CommentContextLoader create(Project.NameKey project);
  }

  @Inject
  public CommentContextLoader(GitRepositoryManager repoManager, @Assisted Project.NameKey project) {
    this.repoManager = repoManager;
    this.project = project;
    this.candidates = new HashMap<>();
  }

  /**
   * Returns and empty list of {@link ContextLine}. The caller is expected to call this method one
   * or more times with different comments. After that, a single call to fill will populate all the
   * lists.
   *
   * @param comment the comment entity for which we want to load the context
   * @return a list of {@link ContextLine}
   */
  public List<ContextLine> getContext(Comment comment, ObjectId commitId) {
    ContextData key =
        ContextData.create(
            comment.key.uuid, commitId, comment.key.filename, getStartAndEndLines(comment));
    List<ContextLine> context = candidates.get(key);
    if (context == null) {
      context = new ArrayList<>();
      candidates.put(key, context);
    }
    return context;
  }

  /**
   * A call to this method loads the context for all comments stored in {@link
   * CommentContextLoader#candidates}. This is useful so that the repository is opened once for all
   * comments.
   */
  public void fill() {
    // Group comments by commit ID so that each commit is parsed only once
    Map<ObjectId, List<ContextData>> group =
        candidates.keySet().stream().collect(groupingBy(ContextData::commitId));

    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      for (ObjectId commitId : group.keySet()) {
        RevCommit commit = rw.parseCommit(commitId);
        for (ContextData k : group.get(commitId)) {
          try (TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), k.path(), commit.getTree())) {
            ObjectId id = tw != null ? tw.getObjectId(0) : ObjectId.zeroId();
            Text src = new Text(repo.open(id, Constants.OBJ_BLOB));
            List<ContextLine> contextLines = candidates.get(k);
            for (int i = k.range().start(); i <= k.range().end(); i++) {
              contextLines.add(ContextLine.create(i, src.getString(i - 1)));
            }
          }
        }
      }
    } catch (IOException e) {
      throw new StorageException("Failed to load the comment context", e);
    }
  }

  private static Range getStartAndEndLines(Comment comment) {
    if (comment.range != null) {
      return Range.create(comment.range.startLine, comment.range.endLine);
    } else if (comment.lineNbr > 0) {
      return Range.create(comment.lineNbr, comment.lineNbr);
    }
    return Range.create(0, -1);
  }

  @AutoValue
  abstract static class Range {
    static Range create(int start, int end) {
      return new AutoValue_CommentContextLoader_Range(start, end);
    }

    abstract int start();

    abstract int end();
  }

  @AutoValue
  abstract static class ContextData {
    static ContextData create(String id, ObjectId commitId, String path, Range range) {
      return new AutoValue_CommentContextLoader_ContextData(id, commitId, path, range);
    }

    abstract String id();

    abstract ObjectId commitId();

    abstract String path();

    abstract Range range();
  }
}
