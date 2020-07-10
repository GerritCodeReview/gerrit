// Copyright (C) 2014 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.LabeledContextLineInfo;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.Text;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

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

  public List<LabeledContextLineInfo> getContext(CommentInfo comment, String path) {
    if (Patch.isMagic(path)) {
      return ImmutableList.of();
    }
    try {
      Text src = readSourceContent(project, ObjectId.fromString(comment.commitId), path);
      Range range = getStartAndEndLines(comment);
      List<LabeledContextLineInfo> contextLines = new ArrayList<>();
      for (int i = range.start(); i <= range.end(); i++) {
        contextLines.add(new LabeledContextLineInfo(i, src.getString(i - 1)));
      }
      return contextLines;
    } catch (IOException e) {
      logger.atWarning().log("Failed to retrieve context for comment " + comment.id);
    }
    return ImmutableList.of();
  }

  private Text readSourceContent(Project.NameKey project, ObjectId commitId, String path)
      throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(commitId);
      try (TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), path, commit.getTree())) {
        ObjectId id = tw != null ? tw.getObjectId(0) : ObjectId.zeroId();
        return new Text(repo.open(id, Constants.OBJ_BLOB));
      }
    }
  }

  private Range getStartAndEndLines(CommentInfo comment) {
    Integer startLine = null;
    Integer endLine = null;
    if (comment.range != null) {
      startLine = comment.range.startLine;
      endLine = comment.range.endLine;
    } else if (comment.line != null) {
      startLine = comment.line;
      endLine = comment.line;
    }
    return Range.create(startLine, endLine);
  }

  @AutoValue
  abstract static class Range {
    static Range create(int start, int end) {
      return new AutoValue_CommentContextLoader_Range(start, end);
    }

    abstract int start();

    abstract int end();
  }
}
