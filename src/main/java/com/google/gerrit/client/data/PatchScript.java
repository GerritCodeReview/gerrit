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

package com.google.gerrit.client.data;



import com.google.gerrit.client.data.PatchScriptSettings.Whitespace;

import org.spearce.jgit.diff.Edit;

import java.util.Iterator;
import java.util.List;

public class PatchScript {
  protected List<String> header;
  protected PatchScriptSettings settings;
  protected SparseFileContent a;
  protected SparseFileContent b;
  protected List<Edit> edits;
  /** protected for serialization over JSON-RPC */
  protected boolean isSafeInline;

  public PatchScript(final List<String> h, final PatchScriptSettings s,
      final SparseFileContent ca, final SparseFileContent cb, final List<Edit> e,
      boolean safe) {
    header = h;
    settings = s;
    a = ca;
    b = cb;
    edits = e;
    isSafeInline = safe;
  }

  protected PatchScript() {
  }

  public boolean isSafeInline() {
    return isSafeInline;
  }

  public List<String> getPatchHeader() {
    return header;
  }

  public int getContext() {
    return settings.getContext();
  }

  public boolean isIgnoreWhitespace() {
    return settings.getWhitespace() != Whitespace.IGNORE_NONE;
  }

  public SparseFileContent getA() {
    return a;
  }

  public SparseFileContent getB() {
    return b;
  }

  public List<Edit> getEdits() {
    return edits;
  }

  public Iterable<Hunk> getHunks() {
    return new Iterable<Hunk>() {
      public Iterator<Hunk> iterator() {
        return new Iterator<Hunk>() {
          private int curIdx;

          public boolean hasNext() {
            return curIdx < edits.size();
          }

          public Hunk next() {
            final int c = curIdx;
            final int e = findCombinedEnd(c);
            curIdx = e + 1;
            return new Hunk(c, e);
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  private int findCombinedEnd(final int i) {
    int end = i + 1;
    while (end < edits.size() && (combineA(end) || combineB(end)))
      end++;
    return end - 1;
  }

  private boolean combineA(final int i) {
    final Edit s = edits.get(i);
    final Edit e = edits.get(i - 1);
    return s.getBeginA() - e.getEndA() <= 2 * getContext();
  }

  private boolean combineB(final int i) {
    final int s = edits.get(i).getBeginB();
    final int e = edits.get(i - 1).getEndB();
    return s - e <= 2 * getContext();
  }

  private static boolean end(final Edit edit, final int a, final int b) {
    return edit.getEndA() <= a && edit.getEndB() <= b;
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

    private Hunk(final int ci, final int ei) {
      curIdx = ci;
      endIdx = ei;
      curEdit = edits.get(curIdx);
      endEdit = edits.get(endIdx);

      aCur = Math.max(0, curEdit.getBeginA() - getContext());
      bCur = Math.max(0, curEdit.getBeginB() - getContext());
      aEnd = Math.min(a.size(), endEdit.getEndA() + getContext());
      bEnd = Math.min(b.size(), endEdit.getEndB() + getContext());
    }

    public int getCurA() {
      return aCur;
    }

    public int getCurB() {
      return bCur;
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

    public boolean hasNextLine() {
      return aCur < aEnd || bCur < bEnd;
    }

    public boolean isContextLine() {
      return aCur < curEdit.getBeginA() || endIdx + 1 < curIdx;
    }

    public boolean isDeletedA() {
      return aCur < curEdit.getEndA();
    }

    public boolean isInsertedB() {
      return bCur < curEdit.getEndB();
    }

    public boolean isModifiedLine() {
      return isDeletedA() || isInsertedB();
    }

    public void next() {
      if (end(curEdit, aCur, bCur) && ++curIdx < edits.size()) {
        curEdit = edits.get(curIdx);
      }
    }
  }
}
