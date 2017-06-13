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

import java.util.Iterator;
import java.util.List;
import org.eclipse.jgit.diff.Edit;

public class EditList {
  private final List<Edit> edits;
  private final int context;
  private final int aSize;
  private final int bSize;

  public EditList(final List<Edit> edits, int contextLines, int aSize, int bSize) {
    this.edits = edits;
    this.context = contextLines;
    this.aSize = aSize;
    this.bSize = bSize;
  }

  public List<Edit> getEdits() {
    return edits;
  }

  public Iterable<Hunk> getHunks() {
    return new Iterable<Hunk>() {
      @Override
      public Iterator<Hunk> iterator() {
        return new Iterator<Hunk>() {
          private int curIdx;

          @Override
          public boolean hasNext() {
            return curIdx < edits.size();
          }

          @Override
          public Hunk next() {
            final int c = curIdx;
            final int e = findCombinedEnd(c);
            curIdx = e + 1;
            return new Hunk(c, e);
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  private int findCombinedEnd(int i) {
    int end = i + 1;
    while (end < edits.size() && (combineA(end) || combineB(end))) {
      end++;
    }
    return end - 1;
  }

  private boolean combineA(int i) {
    final Edit s = edits.get(i);
    final Edit e = edits.get(i - 1);
    // + 1 to prevent '... skipping 1 common line ...' messages.
    return s.getBeginA() - e.getEndA() <= 2 * context + 1;
  }

  private boolean combineB(int i) {
    final int s = edits.get(i).getBeginB();
    final int e = edits.get(i - 1).getEndB();
    // + 1 to prevent '... skipping 1 common line ...' messages.
    return s - e <= 2 * context + 1;
  }

  public class Hunk {
    private int curIdx;
    private Edit curEdit;
    private final int endIdx;
    private final Edit endEdit;

    private int aCur;
    private int bCur;
    private final int aEnd;
    private final int bEnd;

    private Hunk(int ci, int ei) {
      curIdx = ci;
      endIdx = ei;
      curEdit = edits.get(curIdx);
      endEdit = edits.get(endIdx);

      aCur = Math.max(0, curEdit.getBeginA() - context);
      bCur = Math.max(0, curEdit.getBeginB() - context);
      aEnd = Math.min(aSize, endEdit.getEndA() + context);
      bEnd = Math.min(bSize, endEdit.getEndB() + context);
    }

    public int getCurA() {
      return aCur;
    }

    public int getCurB() {
      return bCur;
    }

    public Edit getCurEdit() {
      return curEdit;
    }

    public int getEndA() {
      return aEnd;
    }

    public int getEndB() {
      return bEnd;
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

    public boolean isStartOfFile() {
      return aCur == 0 && bCur == 0;
    }

    public boolean isContextLine() {
      return !isModifiedLine();
    }

    public boolean isDeletedA() {
      return curEdit.getBeginA() <= aCur && aCur < curEdit.getEndA();
    }

    public boolean isInsertedB() {
      return curEdit.getBeginB() <= bCur && bCur < curEdit.getEndB();
    }

    public boolean isModifiedLine() {
      return isDeletedA() || isInsertedB();
    }

    public boolean next() {
      if (!in(curEdit)) {
        if (curIdx < endIdx) {
          curEdit = edits.get(++curIdx);
        }
      }
      return aCur < aEnd || bCur < bEnd;
    }

    private boolean in(Edit edit) {
      return aCur < edit.getEndA() || bCur < edit.getEndB();
    }
  }
}
