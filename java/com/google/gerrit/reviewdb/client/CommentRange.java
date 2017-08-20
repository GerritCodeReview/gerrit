// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;

public class CommentRange {

  @Column(id = 1)
  protected int startLine;

  @Column(id = 2)
  protected int startCharacter;

  @Column(id = 3)
  protected int endLine;

  @Column(id = 4)
  protected int endCharacter;

  protected CommentRange() {}

  public CommentRange(int sl, int sc, int el, int ec) {
    startLine = sl;
    startCharacter = sc;
    endLine = el;
    endCharacter = ec;
  }

  public int getStartLine() {
    return startLine;
  }

  public int getStartCharacter() {
    return startCharacter;
  }

  public int getEndLine() {
    return endLine;
  }

  public int getEndCharacter() {
    return endCharacter;
  }

  public void setStartLine(int sl) {
    startLine = sl;
  }

  public void setStartCharacter(int sc) {
    startCharacter = sc;
  }

  public void setEndLine(int el) {
    endLine = el;
  }

  public void setEndCharacter(int ec) {
    endCharacter = ec;
  }

  public Comment.Range asCommentRange() {
    return new Comment.Range(startLine, startCharacter, endLine, endCharacter);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CommentRange) {
      CommentRange other = (CommentRange) obj;
      return startLine == other.startLine
          && startCharacter == other.startCharacter
          && endLine == other.endLine
          && endCharacter == other.endCharacter;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = startLine;
    h = h * 31 + startCharacter;
    h = h * 31 + endLine;
    h = h * 31 + endCharacter;
    return h;
  }

  @Override
  public String toString() {
    return "Range[startLine="
        + startLine
        + ", startCharacter="
        + startCharacter
        + ", endLine="
        + endLine
        + ", endCharacter="
        + endCharacter
        + "]";
  }
}
