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
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.ContextLineInfo;
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

public class CommentContextLoader {
  private final GitRepositoryManager repoManager;
  private final Project.NameKey project;
  private Map<ContextData, List<ContextLineInfo>> candidates;

  public interface Factory {
    CommentContextLoader create(Project.NameKey project);
  }

  @Inject
  public CommentContextLoader(GitRepositoryManager repoManager, @Assisted Project.NameKey project) {
    this.repoManager = repoManager;
    this.project = project;
    this.candidates = new HashMap<>();
  }

  public List<ContextLineInfo> getContext(CommentInfo comment) {
    ContextData key =
        ContextData.create(
            comment.id, comment.commitId, comment.path, getStartAndEndLines(comment));
    List<ContextLineInfo> context = candidates.get(key);
    if (context == null) {
      context = new ArrayList<>();
      candidates.put(key, context);
    }
    return context;
  }

  public void fill() throws CommentContextException {
    // Group comments by commit ID so that each commit is parsed only once
    Map<String, List<ContextData>> group =
        candidates.keySet().stream().collect(groupingBy(ContextData::commitId));

    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      for (String commitId : group.keySet()) {
        RevCommit commit = rw.parseCommit(ObjectId.fromString(commitId));
        for (ContextData k : group.get(commitId)) {
          try (TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), k.path(), commit.getTree())) {
            ObjectId id = tw != null ? tw.getObjectId(0) : ObjectId.zeroId();
            Text src = new Text(repo.open(id, Constants.OBJ_BLOB));
            List<ContextLineInfo> contextLines = candidates.get(k);
            for (int i = k.range().start(); i <= k.range().end(); i++) {
              contextLines.add(new ContextLineInfo(i, src.getString(i - 1)));
            }
          }
        }
      }
    } catch (IOException e) {
      throw new CommentContextException("Failed to load the comment context", e);
    }
  }

  private static Range getStartAndEndLines(CommentInfo comment) {
    if (comment.range != null) {
      return Range.create(comment.range.startLine, comment.range.endLine);
    } else if (comment.line != null) {
      return Range.create(comment.line, comment.line);
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
    static ContextData create(String id, String commitId, String path, Range range) {
      return new AutoValue_CommentContextLoader_ContextData(id, commitId, path, range);
    }

    abstract String id();

    abstract String commitId();

    abstract String path();

    abstract Range range();
  }
}
