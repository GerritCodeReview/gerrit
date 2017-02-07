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
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.EventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.Pos;

/** Colors modified regions for {@link Unified}. */
class UnifiedChunkManager extends ChunkManager {
  private static final JavaScriptObject focus = initOnClick();

  private static native JavaScriptObject initOnClick() /*-{
    return $entry(function(e){
      @com.google.gerrit.client.diff.UnifiedChunkManager::focus(
        Lcom/google/gwt/dom/client/NativeEvent;)(e)
    });
  }-*/;

  private List<UnifiedDiffChunkInfo> chunks;

  @Override
  DiffChunkInfo getFirst() {
    return !chunks.isEmpty() ? chunks.get(0) : null;
  }

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

  UnifiedChunkManager(Unified host, CodeMirror cm, Scrollbar scrollbar) {
    super(scrollbar);

    this.host = host;
    this.cm = cm;
  }

  @Override
  void render(DiffInfo diff) {
    super.render();

    chunks = new ArrayList<>();

    int cmLine = 0;
    boolean useIntralineBg = diff.metaA() == null || diff.metaB() == null;

    for (Region current : Natives.asList(diff.content())) {
      int origLineA = lineMapper.getLineA();
      int origLineB = lineMapper.getLineB();
      if (current.ab() != null) {
        int length = current.ab().length();
        lineMapper.appendCommon(length);
        for (int i = 0; i < length; i++) {
          host.setLineNumber(DisplaySide.A, cmLine + i, origLineA + i + 1);
          host.setLineNumber(DisplaySide.B, cmLine + i, origLineB + i + 1);
        }
        cmLine += length;
      } else if (current.skip() > 0) {
        lineMapper.appendCommon(current.skip());
        cmLine += current.skip(); // Maybe current.ab().length();
      } else if (current.common()) {
        lineMapper.appendCommon(current.b().length());
        cmLine += current.b().length();
      } else {
        cmLine += render(current, cmLine, useIntralineBg);
      }
    }
    host.setLineNumber(DisplaySide.A, cmLine, lineMapper.getLineA() + 1);
    host.setLineNumber(DisplaySide.B, cmLine, lineMapper.getLineB() + 1);
  }

  private int render(Region region, int cmLine, boolean useIntralineBg) {
    int startA = lineMapper.getLineA();
    int startB = lineMapper.getLineB();

    JsArrayString a = region.a();
    JsArrayString b = region.b();
    int aLen = a != null ? a.length() : 0;
    int bLen = b != null ? b.length() : 0;
    boolean insertOrDelete = a == null || b == null;

    colorLines(
        cm,
        insertOrDelete && !useIntralineBg
            ? UnifiedTable.style.diffDelete()
            : UnifiedTable.style.intralineDelete(),
        cmLine,
        aLen);
    colorLines(
        cm,
        insertOrDelete && !useIntralineBg
            ? UnifiedTable.style.diffInsert()
            : UnifiedTable.style.intralineInsert(),
        cmLine + aLen,
        bLen);
    markEdit(DisplaySide.A, cmLine, a, region.editA());
    markEdit(DisplaySide.B, cmLine + aLen, b, region.editB());
    addGutterTag(region, cmLine); // TODO: verify addGutterTag
    lineMapper.appendReplace(aLen, bLen);

    int endA = lineMapper.getLineA() - 1;
    int endB = lineMapper.getLineB() - 1;
    if (aLen > 0) {
      addDiffChunk(DisplaySide.A, endA, aLen, cmLine, bLen > 0);
      for (int j = 0; j < aLen; j++) {
        host.setLineNumber(DisplaySide.A, cmLine + j, startA + j + 1);
        host.setLineNumberEmpty(DisplaySide.B, cmLine + j);
      }
    }
    if (bLen > 0) {
      addDiffChunk(DisplaySide.B, endB, bLen, cmLine + aLen, aLen > 0);
      for (int j = 0; j < bLen; j++) {
        host.setLineNumberEmpty(DisplaySide.A, cmLine + aLen + j);
        host.setLineNumber(DisplaySide.B, cmLine + aLen + j, startB + j + 1);
      }
    }
    return aLen + bLen;
  }

  private void addGutterTag(Region region, int cmLine) {
    if (region.a() == null) {
      scrollbar.insert(cm, cmLine, region.b().length());
    } else if (region.b() == null) {
      scrollbar.delete(cm, cm, cmLine, region.a().length());
    } else {
      scrollbar.edit(cm, cmLine, region.b().length());
    }
  }

  private void markEdit(DisplaySide side, int startLine, JsArrayString lines, JsArray<Span> edits) {
    if (lines == null || edits == null) {
      return;
    }

    EditIterator iter = new EditIterator(lines, startLine);
    Configuration bg =
        Configuration.create().set("className", getIntralineBgFromSide(side)).set("readOnly", true);

    Configuration diff =
        Configuration.create().set("className", getDiffColorFromSide(side)).set("readOnly", true);

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
      colorLines(cm, LineClassWhere.BACKGROUND, getDiffColorFromSide(side), from.line(), to.line());
    }
  }

  private String getIntralineBgFromSide(DisplaySide side) {
    return side == DisplaySide.A
        ? UnifiedTable.style.intralineDelete()
        : UnifiedTable.style.intralineInsert();
  }

  private String getDiffColorFromSide(DisplaySide side) {
    return side == DisplaySide.A
        ? UnifiedTable.style.diffDelete()
        : UnifiedTable.style.diffInsert();
  }

  private void addDiffChunk(
      DisplaySide side, int chunkEnd, int chunkSize, int cmLine, boolean edit) {
    chunks.add(new UnifiedDiffChunkInfo(side, chunkEnd - chunkSize + 1, chunkEnd, cmLine, edit));
  }

  @Override
  Runnable diffChunkNav(final CodeMirror cm, final Direction dir) {
    return new Runnable() {
      @Override
      public void run() {
        int line = cm.extras().hasActiveLine() ? cm.getLineNumber(cm.extras().activeLine()) : 0;
        int res =
            Collections.binarySearch(
                chunks,
                new UnifiedDiffChunkInfo(cm.side(), 0, 0, line, false),
                getDiffChunkComparatorCmLine());
        diffChunkNavHelper(chunks, host, res, dir);
      }
    };
  }

  /** Diff chunks are ordered by their starting lines in CodeMirror */
  private Comparator<UnifiedDiffChunkInfo> getDiffChunkComparatorCmLine() {
    return new Comparator<UnifiedDiffChunkInfo>() {
      @Override
      public int compare(UnifiedDiffChunkInfo o1, UnifiedDiffChunkInfo o2) {
        return o1.getCmLine() - o2.getCmLine();
      }
    };
  }

  @Override
  int getCmLine(int line, DisplaySide side) {
    int res =
        Collections.binarySearch(
            chunks,
            new UnifiedDiffChunkInfo(side, line, 0, 0, false), // Dummy DiffChunkInfo
            getDiffChunkComparator());
    if (res >= 0) {
      return chunks.get(res).getCmLine();
    }
    // The line might be within a DiffChunk
    res = -res - 1;
    if (res > 0) {
      UnifiedDiffChunkInfo info = chunks.get(res - 1);
      if (side == DisplaySide.A && info.isEdit() && info.getSide() == DisplaySide.B) {
        // Need to use the start and cmLine of the deletion chunk
        UnifiedDiffChunkInfo delete = chunks.get(res - 2);
        if (line <= delete.getEnd()) {
          return delete.getCmLine() + line - delete.getStart();
        }
        // Need to add the length of the insertion chunk
        return delete.getCmLine() + line - delete.getStart() + info.getEnd() - info.getStart() + 1;
      } else if (side == info.getSide()) {
        return info.getCmLine() + line - info.getStart();
      } else {
        return info.getCmLine() + lineMapper.lineOnOther(side, line).getLine() - info.getStart();
      }
    }
    return line;
  }

  LineRegionInfo getLineRegionInfoFromCmLine(int cmLine) {
    int res =
        Collections.binarySearch(
            chunks,
            new UnifiedDiffChunkInfo(DisplaySide.A, 0, 0, cmLine, false), // Dummy DiffChunkInfo
            getDiffChunkComparatorCmLine());
    if (res >= 0) { // The line is right at the start of a diff chunk.
      UnifiedDiffChunkInfo info = chunks.get(res);
      return new LineRegionInfo(info.getStart(), displaySideToRegionType(info.getSide()));
    }
    // The line might be within or after a diff chunk.
    res = -res - 1;
    if (res > 0) {
      UnifiedDiffChunkInfo info = chunks.get(res - 1);
      int lineOnInfoSide = info.getStart() + cmLine - info.getCmLine();
      if (lineOnInfoSide > info.getEnd()) { // After a diff chunk
        if (info.getSide() == DisplaySide.A) {
          // For the common region after a deletion chunk, associate the line
          // on side B with a common region.
          return new LineRegionInfo(
              lineMapper.lineOnOther(DisplaySide.A, lineOnInfoSide).getLine(), RegionType.COMMON);
        }
        return new LineRegionInfo(lineOnInfoSide, RegionType.COMMON);
      }
      // Within a diff chunk
      return new LineRegionInfo(lineOnInfoSide, displaySideToRegionType(info.getSide()));
    }
    // The line is before any diff chunk, so it always equals cmLine and
    // belongs to a common region.
    return new LineRegionInfo(cmLine, RegionType.COMMON);
  }

  enum RegionType {
    INSERT,
    DELETE,
    COMMON,
  }

  private static RegionType displaySideToRegionType(DisplaySide side) {
    return side == DisplaySide.A ? RegionType.DELETE : RegionType.INSERT;
  }

  /**
   * Helper class to associate a line in the original file with the type of the region it belongs
   * to.
   *
   * @field line The 0-based line number in the original file. Note that this might be different
   *     from the line number shown in CodeMirror.
   * @field type The type of the region the line belongs to. Can be INSERT, DELETE or COMMON.
   */
  static class LineRegionInfo {
    final int line;
    final RegionType type;

    LineRegionInfo(int line, RegionType type) {
      this.line = line;
      this.type = type;
    }

    DisplaySide getSide() {
      // Always return DisplaySide.B for INSERT or COMMON
      return type == RegionType.DELETE ? DisplaySide.A : DisplaySide.B;
    }
  }
}
