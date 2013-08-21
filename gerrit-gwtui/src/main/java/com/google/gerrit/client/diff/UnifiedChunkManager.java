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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.diff.DiffInfo.Region;
import com.google.gerrit.client.diff.DiffInfo.Span;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineCharacter;

import java.util.Collections;
import java.util.Comparator;

/** Colors modified regions for {@link Unified2}. */
class UnifiedChunkManager extends ChunkManager {
  private final CodeMirror cm;

  UnifiedChunkManager(Unified2 host,
      CodeMirror cm,
      OverviewBar sidePanel) {
    super(host, sidePanel);

    this.cm = cm;
  }

  @Override
  void render(DiffInfo diff) {
    super.render(diff);

    int cmLine = 0;
    boolean useIntralineBg = diff.meta_a() == null || diff.meta_b() == null;

    for (Region current : Natives.asList(diff.content())) {
      if (current.ab() != null) {
        int length = current.ab().length();
        getMapper().appendCommon(length);
        for (int j = 0; j < length; j++) {
          ((Unified2) getDiffScreen()).setLineNumber(DisplaySide.A, cmLine + j, getMapper().getLineA() + j + 1);
          ((Unified2) getDiffScreen()).setLineNumber(DisplaySide.B, cmLine + j, getMapper().getLineB() + j + 1);
        }
        cmLine += length;
      } else if (current.skip() > 0) {
        getMapper().appendCommon(current.skip());
        cmLine += current.skip(); // Maybe current.ab().length();
      } else if (current.common()) {
        getMapper().appendCommon(current.b().length());
        cmLine += current.b().length();
      } else {
        cmLine += render(current, cmLine, useIntralineBg);
      }
    }
  }

  private int render(Region region, int cmLine, boolean useIntralineBg) {
    int origLineA = getMapper().getLineA();
    int origLineB = getMapper().getLineB();

    JsArrayString a = region.a();
    JsArrayString b = region.b();
    int aLen = a != null ? a.length() : 0;
    int bLen = b != null ? b.length() : 0;
    boolean insertOrDelete = a == null || b == null;

    colorLines(cm,
        insertOrDelete && !useIntralineBg
            ? UnifiedTable2.style.diffDelete()
            : UnifiedTable2.style.intralineDelete(), cmLine, aLen);
    colorLines(cm,
        insertOrDelete && !useIntralineBg
            ? UnifiedTable2.style.diffInsert()
            : UnifiedTable2.style.intralineInsert(), cmLine + aLen,
        bLen);
    markEdit(DisplaySide.A, cmLine, a, region.edit_a());
    markEdit(DisplaySide.B, cmLine + aLen, b, region.edit_b());
    //addGutterTag(region, cmLine);
    getMapper().appendReplace(aLen, bLen);

    int endA = getMapper().getLineA() - 1;
    int endB = getMapper().getLineB() - 1;
    if (aLen > 0) {
      addDiffChunk(DisplaySide.A, endA, aLen, cmLine, bLen > 0);
      for (int j = 0; j < aLen; j++) {
        ((Unified2) getDiffScreen()).setLineNumber(DisplaySide.A, cmLine + j, origLineA + j + 1);
      }
    }
    if (bLen > 0) {
      addDiffChunk(DisplaySide.B, endB, bLen, cmLine + aLen, aLen > 0);
      for (int j = 0; j < bLen; j++) {
        ((Unified2) getDiffScreen()).setLineNumber(DisplaySide.B, cmLine + aLen + j, origLineB + j + 1);
      }
    }
    return aLen + bLen;
  }

  /*private void addGutterTag(Region region, int cmLine) {
    if (region.a() == null) {
      sidePanel.add(cm, cmLine, region.b().length(), INSERT);
    } else if (region.b() == null) {
      sidePanel.add(cm, cmLine, region.a().length(), DELETE);
    } else {
      sidePanel.add(cm, cmLine, region.b().length(), EDIT);
    }
  }*/

  private void markEdit(DisplaySide side, int startLine,
      JsArrayString lines, JsArray<Span> edits) {
    if (lines == null || edits == null) {
      return;
    }

    EditIterator iter = new EditIterator(lines, startLine);
    Configuration bg = Configuration.create()
        .set("className", getIntralineBgFromSide(side))
        .set("readOnly", true);

    Configuration diff = Configuration.create()
        .set("className", getDiffColorFromSide(side))
        .set("readOnly", true);

    LineCharacter last = CodeMirror.pos(0, 0);
    for (Span span : Natives.asList(edits)) {
      LineCharacter from = iter.advance(span.skip());
      LineCharacter to = iter.advance(span.mark());
      if (from.getLine() == last.getLine()) {
        addTextMarker(cm.markText(last, from, bg));
      } else {
        addTextMarker(cm.markText(CodeMirror.pos(from.getLine(), 0), from, bg));
      }
      addTextMarker(cm.markText(from, to, diff));
      last = to;
      colorLines(cm, LineClassWhere.BACKGROUND,
          getDiffColorFromSide(side),
          from.getLine(), to.getLine());
    }
  }

  private String getIntralineBgFromSide(DisplaySide side) {
    return side == DisplaySide.A ? UnifiedTable2.style.intralineDelete()
        : UnifiedTable2.style.intralineInsert();
  }

  private String getDiffColorFromSide(DisplaySide side) {
    return side == DisplaySide.A ? UnifiedTable2.style.diffDelete()
        : UnifiedTable2.style.diffInsert();
  }

  private void addDiffChunk(DisplaySide side, int chunkEnd, int chunkSize,
      int cmLine, boolean edit) {
    addDiffChunk(new UnifiedDiffChunkInfo(side, chunkEnd - chunkSize + 1, chunkEnd,
        cmLine, edit));
  }

  Runnable diffChunkNav(final CodeMirror cm, final Direction dir) {
    return new Runnable() {
      @Override
      public void run() {
        int line = cm.hasActiveLine() ? cm.getLineNumber(cm.getActiveLine()) : 0;
        int res = Collections.binarySearch(
                getChunks(),
                new UnifiedDiffChunkInfo(cm.side(), 0, 0, line, false),
                getDiffChunkComparatorCmLine());
        diffChunkNavHelper(cm, res, dir);
      }
    };
  }

  /** Diff chunks are ordered by their starting lines in CodeMirror */
  private Comparator<DiffChunkInfo> getDiffChunkComparatorCmLine() {
    return new Comparator<DiffChunkInfo>() {
      @Override
      public int compare(DiffChunkInfo o1, DiffChunkInfo o2) {
        return ((UnifiedDiffChunkInfo) o1).getCmLine() - ((UnifiedDiffChunkInfo) o2).getCmLine();
      }
    };
  }

  /*DiffChunkInfo getDiffChunk(DisplaySide side, int line) {
    int res = Collections.binarySearch(
        chunks,
        new DiffChunkInfo(side, line, 0, false), // Dummy DiffChunkInfo
        getDiffChunkComparator());
    if (res >= 0) {
      return chunks.get(res);
    } else { // The line might be within a DiffChunk
      res = -res - 1;
      if (res > 0) {
        DiffChunkInfo info = chunks.get(res - 1);
        if (info.getSide() == side && info.getStart() <= line &&
            line <= info.getEnd()) {
          return info;
        }
      }
    }
    return null;
  }*/

  int getCmLine(DisplaySide side, int line) {
    int res =
        Collections.binarySearch(getChunks(),
            new UnifiedDiffChunkInfo(
                side, line, 0, 0, false), // Dummy DiffChunkInfo
            getDiffChunkComparator());
    if (res >= 0) {
      return ((UnifiedDiffChunkInfo) getChunks().get(res)).getCmLine();
    } else { // The line might be within a DiffChunk
      res = -res - 1;
      if (res > 0) {
        UnifiedDiffChunkInfo info = (UnifiedDiffChunkInfo) getChunks().get(res - 1);
        if (info.getSide() == side && info.getStart() <= line && line <= info.getEnd()) {
          return info.getCmLine() + line - info.getStart();
        } else {
          return info.getCmLine() + (side == info.getSide()
              ? line
              : getMapper().lineOnOther(side, line).getLine()) - info.getStart();
        }
      } else {
        return line;
      }
    }
  }

  int getCmLineFromLinePair(LinePair pair) {
    int res =
        Collections.binarySearch(getChunks(),
            new UnifiedDiffChunkInfo(
                DisplaySide.A, pair.getLineA(), 0, 0, false),
                getDiffChunkComparator());
    if (res >= 0) {
      throw new IllegalStateException("Not a valid LinePair for cmLine query");
    } else {
      res = -res - 1;
      if (res > 0) {
        UnifiedDiffChunkInfo info = (UnifiedDiffChunkInfo) getChunks().get(res - 1);
        return info.getCmLine() + (info.getSide() == DisplaySide.A
            ? pair.getLineA()
            : pair.getLineB()) - info.getStart();
      } else { // lineA == lineB == cmLine before all diff chunks.
        return pair.getLineA();
      }
    }
  }

  LinePair getLinePairFromCmLine(int cmLine) {
    int res =
        Collections.binarySearch(getChunks(),
            new UnifiedDiffChunkInfo(
                DisplaySide.A, 0, 0, cmLine, false), // Dummy DiffChunkInfo
            getDiffChunkComparatorCmLine());
    if (res >= 0) {
      UnifiedDiffChunkInfo info = (UnifiedDiffChunkInfo) getChunks().get(res);
      return info.getSide() == DisplaySide.A
          ? new LinePair(info.getStart(), null)
          : new LinePair(null, info.getStart());
    } else {  // The line pair might be within a DiffChunk
      res = -res - 1;
      if (res > 0) {
        UnifiedDiffChunkInfo info = (UnifiedDiffChunkInfo) getChunks().get(res - 1);
        int delta = info.getCmLine() + info.getEnd() - info.getStart() - cmLine;
        if (delta > 0) {
          return info.getSide() == DisplaySide.A
              ? new LinePair(info.getStart() + delta, null)
              : new LinePair(null, info.getStart() + delta);
        } else {
          delta = cmLine - info.getCmLine();
          int result = info.getStart() + delta;
          return info.getSide() == DisplaySide.A
              ? new LinePair(result, getMapper().lineOnOther(DisplaySide.A, result).getLine())
              : new LinePair(getMapper().lineOnOther(DisplaySide.B, result).getLine(), result);
        }
      } else {
        return new LinePair(cmLine, cmLine);
      }
    }
  }

  static class LinePair {
    private Integer lineA;
    private Integer lineB;

    LinePair(Integer lineA, Integer lineB) {
      this.lineA = lineA;
      this.lineB = lineB;
    }

    Integer getLineA() {
      return lineA;
    }

    Integer getLineB() {
      return lineB;
    }
  }
}
