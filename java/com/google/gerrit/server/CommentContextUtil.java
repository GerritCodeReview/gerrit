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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.LabeledContextLineInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

public class CommentContextUtil {
  private final FileContentUtil fileContentUtil;
  private final ProjectCache projectCache;

  @Inject
  public CommentContextUtil(FileContentUtil fileContentUtil, ProjectCache projectCache) {
    this.fileContentUtil = fileContentUtil;
    this.projectCache = projectCache;
  }

  public void attachContextToComment(CommentInfo comment, String path, Project.NameKey project)
      throws IOException, BadRequestException, ResourceNotFoundException {
    ProjectState projectState = projectCache.get(project).orElse(null);
    if (projectState == null) {
      return;
    }
    if (!Patch.isMagic(path)) {
      Text src = readSourceContent(projectState, ObjectId.fromString(comment.commitId), path);
      ImmutableList<Integer> region = getStartAndEndLines(comment);
      Integer startLine = region.get(0);
      Integer endLine = region.get(1);
      List<LabeledContextLineInfo> contextLines = new ArrayList<>();
      while (startLine <= endLine) {
        contextLines.add(new LabeledContextLineInfo(startLine, src.getString(startLine - 1)));
        startLine += 1;
      }
      comment.contextLines = contextLines;
    }
  }

  private Text readSourceContent(ProjectState projectState, ObjectId commitId, String path)
      throws IOException, BadRequestException, ResourceNotFoundException {
    BinaryResult content = fileContentUtil.getContent(projectState, commitId, path, null);
    ByteArrayOutputStream buf = new ByteArrayOutputStream((int) content.getContentLength());
    content.writeTo(buf);
    return new Text(buf.toByteArray());
  }

  private ImmutableList<Integer> getStartAndEndLines(CommentInfo comment) {
    Integer startLine = null;
    Integer endLine = null;
    if (comment.range != null) {
      startLine = comment.range.startLine;
      endLine = comment.range.endLine;
    } else if (comment.line != null) {
      startLine = comment.line;
      endLine = comment.line;
    }
    return ImmutableList.of(startLine, endLine);
  }
}
