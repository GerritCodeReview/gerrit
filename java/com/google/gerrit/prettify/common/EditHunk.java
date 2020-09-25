// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.prettify.common;

import java.util.List;
import org.eclipse.jgit.diff.Edit;

// This is a legacy class. It was only simplified but not improved regarding readability or code
// health. Feel free to completely rewrite it or replace it with some other, better code.
public class EditHunk {
  private final List<Edit> edits;

  private int curIdx;
  private Edit curEdit;

  private int aCur;
  private int bCur;
  private final int aEnd;
  private final int bEnd;

  public EditHunk(List<Edit> edits, int aSize, int bSize) {
    this.edits = edits;

    curIdx = 0;
    curEdit = edits.get(curIdx);

    aCur = 0;
    bCur = 0;
    aEnd = aSize;
    bEnd = bSize;
  }

  public int getCurA() {
    return aCur;
  }

  public int getCurB() {
    return bCur;
  }

  public void incA() {
    aCur++;
  }

  public void incB() {
    bCur++;
  }

  public void incBoth() {
    incA();
    incB();
  }

  public boolean isUnmodifiedLine() {
    return !isDeletedA() && !isInsertedB();
  }

  public boolean isDeletedA() {
    return curEdit.getBeginA() <= aCur && aCur < curEdit.getEndA();
  }

  public boolean isInsertedB() {
    return curEdit.getBeginB() <= bCur && bCur < curEdit.getEndB();
  }

  public boolean next() {
    if (!in(curEdit)) {
      if (curIdx < edits.size() - 1) {
        curEdit = edits.get(++curIdx);
      }
    }
    return aCur < aEnd || bCur < bEnd;
  }

  private boolean in(Edit edit) {
    return aCur < edit.getEndA() || bCur < edit.getEndB();
  }
}
