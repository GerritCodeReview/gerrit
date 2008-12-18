// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.PatchLineComment;

import java.util.ArrayList;
import java.util.List;

/** A single line of a 2-way patch file. */
public class PatchLine {
  public static enum Type {
    FILE_HEADER,

    HUNK_HEADER,

    PRE_IMAGE,

    CONTEXT,

    POST_IMAGE;
  }

  protected int oldLineNumber;
  protected int newLineNumber;
  protected PatchLine.Type type;
  protected String text;
  protected List<PatchLineComment> comments;

  protected PatchLine() {
  }

  public PatchLine(final int oLine, final int nLine, final PatchLine.Type t,
      final String s) {
    oldLineNumber = oLine;
    newLineNumber = nLine;
    type = t;
    text = s;
  }

  public int getOldLineNumber() {
    return oldLineNumber;
  }

  public int getNewLineNumber() {
    return newLineNumber;
  }

  public PatchLine.Type getType() {
    return type;
  }

  public String getText() {
    return text;
  }

  public List<PatchLineComment> getComments() {
    return comments;
  }

  public void addComment(final PatchLineComment plc) {
    if (comments == null) {
      comments = new ArrayList<PatchLineComment>(4);
    }
    comments.add(plc);
  }
}
