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

import static com.google.gerrit.client.diff.DisplaySide.A;
import static com.google.gerrit.client.diff.DisplaySide.B;

import com.google.gerrit.client.diff.DiffInfo.Region;
import com.google.gerrit.client.diff.DiffInfo.Span;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.EventListener;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.Pos;

import java.util.Collections;
import java.util.Comparator;

/** Colors modified regions for {@link Unified}. */
class UnifiedChunkManager extends ChunkManager {
  private static final JavaScriptObject focus = initOnClick();
  private static final native JavaScriptObject initOnClick() /*-{
    return $entry(function(e){
      @com.google.gerrit.client.diff.UnifiedChunkManager::focus(
        Lcom/google/gwt/dom/client/NativeEvent;)(e)
    });
  }-*/;

  private static void focus(NativeEvent event) {
    Element e = Element.as(event.getEventTarget());
    for (e = DOM.getParent(e); e != null; e = DOM.getParent(e)) {
      EventListener l = DOM.getEventListener(e);
      if (l instanceof Unified) {
        ((Unified) l).getCmFromSide(DisplaySide.A).focus();
        event.stopPropagation();
      }
    }
  }

  static void focusOnClick(Element e) {
    onClick(e, focus);
  }

  private final Unified host;
  private final CodeMirror cm;

  UnifiedChunkManager(Unified host,
      CodeMirror cm,
      Scrollbar scrollbar) {
    super(scrollbar);

    this.host = host;
    this.cm = cm;
  }

  @Override
  void render(DiffInfo diff) {
    super.render(diff);

    LineMapper mapper = getLineMapper();

    int cmLine = 0;
    boolean useIntralineBg = diff.metaA() == null || diff.metaB() == null;

    for (Region current : Natives.asList(diff.content())) {
      if (current.ab() != null) {
        int length = current.ab().length();
        mapper.appendCommon(length);
        for (int j = 0; j < length; j++) {
          host.setLineNumber(DisplaySide.A, cmLine + j, mapper.getLineA() + j + 1);
          host.setLineNumber(DisplaySide.B, cmLine + j, mapper.getLineB() + j + 1);
        }
        cmLine += length;
      } else if (current.skip() > 0) {
        mapper.appendCommon(current.skip());
        cmLine += current.skip(); // Maybe current.ab().length();
      } else if (current.common()) {
        mapper.appendCommon(current.b().length());
        cmLine += current.b().length();
      } else {
        cmLine += render(current, cmLine, useIntralineBg);
      }
    }
  }

  private int render(Region region, int cmLine, boolean useIntralineBg) {
    LineMapper mapper = getLineMapper();

    int startA = mapper.getLineA();
    int startB = mapper.getLineB();

    JsArrayString a = region.a();
    JsArrayString b = region.b();
    int aLen = a != null ? a.length() : 0;
    int bLen = b != null ? b.length() : 0;
    boolean insertOrDelete = a == null || b == null;

    colorLines(cm,
        insertOrDelete && !useIntralineBg
            ? UnifiedTable.style.diffDelete()
            : UnifiedTable.style.intralineDelete(), cmLine, aLen);
    colorLines(cm,
        insertOrDelete && !useIntralineBg
            ? UnifiedTable.style.diffInsert()
            : UnifiedTable.style.intralineInsert(), cmLine + aLen,
        bLen);
    markEdit(DisplaySide.A, cmLine, a, region.editA());
    markEdit(DisplaySide.B, cmLine + aLen, b, region.editB());
    //addGutterTag(region, cmLine);
    mapper.appendReplace(aLen, bLen);

    int endA = mapper.getLineA() - 1;
    int endB = mapper.getLineB() - 1;
    if (aLen > 0) {
      addDiffChunk(DisplaySide.A, endA, aLen, cmLine, bLen > 0);
      for (int j = 0; j < aLen; j++) {
        host.setLineNumber(DisplaySide.A, cmLine + j, startA + j + 1);
      }
    }
    if (bLen > 0) {
      addDiffChunk(DisplaySide.B, endB, bLen, cmLine + aLen, aLen > 0);
      for (int j = 0; j < bLen; j++) {
        host.setLineNumber(DisplaySide.B, cmLine + aLen + j, startB + j + 1);
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

    Pos last = Pos.create(0, 0);
    for (Span span : Natives.asList(edits)) {
      Pos from = iter.advance(span.skip());
      Pos to = iter.advance(span.mark());
      if (from.line() == last.line()) {
        getMarkers().add(cm.markText(last, from, bg));
      } else {
        getMarkers().add(cm.markText(Pos.create(from.line(), 0), from, bg));
      }
      getMarkers().add(cm.markText(from, to, diff));
      last = to;
      colorLines(cm, LineClassWhere.BACKGROUND,
          getDiffColorFromSide(side),
          from.line(), to.line());
    }
  }

  private String getIntralineBgFromSide(DisplaySide side) {
    return side == DisplaySide.A ? UnifiedTable.style.intralineDelete()
        : UnifiedTable.style.intralineInsert();
  }

  private String getDiffColorFromSide(DisplaySide side) {
    return side == DisplaySide.A ? UnifiedTable.style.diffDelete()
        : UnifiedTable.style.diffInsert();
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
