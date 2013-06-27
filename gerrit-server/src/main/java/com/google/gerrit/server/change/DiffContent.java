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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.prettify.common.SparseFileContent;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.ReplaceEdit;

import java.util.List;

public class DiffContent {
  private final List<Entry> lines;
  private final SparseFileContent fileA;
  private final SparseFileContent fileB;

  private int nextA;
  private int nextB;

  public DiffContent(PatchScript ps) {
    lines = Lists.newArrayListWithExpectedSize(ps.getEdits().size() + 2);
    fileA = ps.getA();
    fileB = ps.getB();

    for (Edit edit : ps.getEdits()) {
      if (edit.getType() == Edit.Type.EMPTY) {
        continue;
      }
      addCommon(edit.getBeginA());

      checkState(nextA == edit.getBeginA(),
          "nextA = %d; want %d", nextA, edit.getBeginA());
      checkState(nextB == edit.getBeginB(),
          "nextB = %d; want %d", nextB, edit.getBeginB());
      switch (edit.getType()) {
        case DELETE:
        case INSERT:
        case REPLACE:
          List<Edit> internalEdit = edit instanceof ReplaceEdit
            ? ((ReplaceEdit) edit).getInternalEdits()
            : null;
          addDiff(edit.getEndA(), edit.getEndB(), internalEdit);
          break;
        case EMPTY:
        default:
          throw new IllegalStateException();
      }
    }
    addCommon(ps.getA().size());
  }

  public List<Entry> getLines() {
    return lines;
  }

  private void addCommon(int end) {
    end = Math.min(end, fileA.size());
    if (nextA >= end) {
      return;
    }
    nextB += end - nextA;

    while (nextA < end) {
      if (fileA.contains(nextA)) {
        Entry e = entry();
        e.ab = Lists.newArrayListWithCapacity(end - nextA);
        for (int i = nextA; i == nextA && i < end; i = fileA.next(i), nextA++) {
          e.ab.add(fileA.get(i));
        }
      } else {
        int endRegion = Math.min(end,
            (nextA == 0) ? fileA.first() : fileA.next(nextA - 1));
        Entry e = entry();
        e.skip = endRegion - nextA;
        nextA = endRegion;
      }
    }
  }

  private void addDiff(int endA, int endB, List<Edit> internalEdit) {
    int lenA = endA - nextA;
    int lenB = endB - nextB;
    checkState(lenA > 0 || lenB > 0);

    Entry e = entry();
    if (lenA > 0) {
      e.a = Lists.newArrayListWithCapacity(lenA);
      for (; nextA < endA; nextA++) {
        e.a.add(fileA.get(nextA));
      }
    }
    if (lenB > 0) {
      e.b = Lists.newArrayListWithCapacity(lenB);
      for (; nextB < endB; nextB++) {
        e.b.add(fileB.get(nextB));
      }
    }
    if (internalEdit != null && !internalEdit.isEmpty()) {
      e.editA = Lists.newArrayListWithCapacity(internalEdit.size() * 2);
      e.editB = Lists.newArrayListWithCapacity(internalEdit.size() * 2);
      int lastA = 0;
      int lastB = 0;
      for (Edit edit : internalEdit) {
        if (edit.getBeginA() != edit.getEndA()) {
          e.editA.add(ImmutableList.of(edit.getBeginA() - lastA, edit.getEndA() - edit.getBeginA()));
          lastA = edit.getEndA();
        }
        if (edit.getBeginB() != edit.getEndB()) {
          e.editB.add(ImmutableList.of(edit.getBeginB() - lastB, edit.getEndB() - edit.getBeginB()));
          lastB = edit.getEndB();
        }
      }
    }
  }

  private Entry entry() {
    Entry e = new Entry();
    lines.add(e);
    return e;
  }

  public static final class Entry {
    // Common lines to both sides.
    public List<String> ab;
    // Lines of a.
    public List<String> a;
    // Lines of b.
    public List<String> b;

    // A list of changed sections of the of the corresponding line list.
    // Each entry is a character <offset, length> pair. The offset is from the
    // beginning of the first line in the list. Also, the offset includes an
    // implied trailing newline character for each line.
    public List<List<Integer>> editA;
    public List<List<Integer>> editB;

    // Number of lines to skip on both sides.
    public Integer skip;
  }
}
