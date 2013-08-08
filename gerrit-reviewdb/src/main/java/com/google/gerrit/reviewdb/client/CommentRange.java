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
  int startLine;

  @Column(id = 2)
  int startCh;

  @Column(id = 3)
  int endLine;

  @Column(id = 4)
  int endCh;

  public CommentRange(int sl, int sc, int el, int ec) {
    startLine = sl;
    startCh = sc;
    endLine = el;
    endCh = ec;
  }

  public int getStartLine() {
    return startLine;
  }

  public int getStartCh() {
    return startCh;
  }

  public int getEndLine() {
    return endLine;
  }

  public int getEndCh() {
    return endCh;
  }

  public void setStartLine(int sl) {
    startLine = sl;
  }

  public void setStartCh(int sc) {
    startCh = sc;
  }

  public void setEndLine(int el) {
    endLine = el;
  }

  public void setEndCh(int ec) {
    endCh = ec;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CommentRange) {
      CommentRange other = (CommentRange) obj;
      return startLine == other.startLine && startCh == other.startCh &&
          endLine == other.endLine && endCh == other.endCh;
    }
    return false;
  }

  @Override
  public String toString() {
    return "Range [startLine=" + startLine + ", startCh=" + startCh
        + ", endLine=" + endLine + ", endCh=" + endCh + "]";
  }

  protected CommentRange() {
  }
}