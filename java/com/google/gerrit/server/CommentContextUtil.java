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

import com.google.gerrit.entities.Patch;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.LabeledContextLineInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

public class CommentContextUtil {
  private final FileContentUtil fileContentUtil;

  @Inject
  public CommentContextUtil(FileContentUtil fileContentUtil) {
    this.fileContentUtil = fileContentUtil;
  }

  public void attachContextToComment(CommentInfo comment, String path, ProjectState projectState)
      throws IOException, BadRequestException, ResourceNotFoundException {
    if (!Patch.isMagic(path)) {
      ObjectId commitId = ObjectId.fromString(comment.commitId);
      BinaryResult content = fileContentUtil.getContent(projectState, commitId, path, null);
      // TODO(ghareeb): ignore binary and large files
      ByteArrayOutputStream buf = new ByteArrayOutputStream((int) content.getContentLength());
      content.writeTo(buf);
      Text src = new Text(buf.toByteArray());
      Integer startLine = null;
      Integer endLine = null;
      if (comment.range != null) {
        startLine = comment.range.startLine;
        endLine = comment.range.endLine + 1;
      } else if (comment.line != null) {
        startLine = comment.line;
        endLine = comment.line + 1;
      }
      // TODO(ghareeb): reuse the classes of GetDiff to extract lines from the file
      List<String> textLines =
          Arrays.asList(src.getString(startLine - 1, endLine - 1, false).split("\n"));
      List<LabeledContextLineInfo> contextLines = new ArrayList<>();
      int cur = startLine;
      for (String textLine : textLines) {
        LabeledContextLineInfo line = new LabeledContextLineInfo();
        line.contextLine = textLine;
        line.lineNumber = cur;
        cur += 1;
        contextLines.add(line);
      }
      comment.contextLines = contextLines;
    }
  }
}
