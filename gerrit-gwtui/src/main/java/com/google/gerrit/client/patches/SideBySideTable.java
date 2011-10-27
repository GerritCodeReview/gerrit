// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import static com.google.gerrit.client.patches.PatchLine.Type.CONTEXT;
import static com.google.gerrit.client.patches.PatchLine.Type.DELETE;
import static com.google.gerrit.client.patches.PatchLine.Type.INSERT;
import static com.google.gerrit.client.patches.PatchLine.Type.REPLACE;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.FileMode;
import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.SparseHtmlFile;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.Patch.ChangeType;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;

import org.eclipse.jgit.diff.Edit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SideBySideTable extends AbstractPatchContentTable {
  private static final int COL_A = 2;
  private static final int COL_B = 4;

  private static final int NUM_ROWS_TO_EXPAND = 10;

  private SparseHtmlFile a;
  private SparseHtmlFile b;

  @Override
  protected void onCellDoubleClick(final int row, int column) {
    if (column > 0 && getRowItem(row) instanceof PatchLine) {
      final PatchLine line = (PatchLine) getRowItem(row);
      final short file = (short) ((column - 1) / 2);
      if (column < (1 + file * 2 + 1)) {
        column++;
      }
      switch (file) {
        case 0:
          createCommentEditor(row + 1, column, line.getLineA(), file);
          break;
        case 1:
          createCommentEditor(row + 1, column, line.getLineB(), file);
          break;
      }
    }
  }

  @Override
  protected void onCellSingleClick(int row, int column) {
    if (column == 1 || column == 3) {
      onCellDoubleClick(row, column);
    }
  }

  @Override
  protected void onInsertComment(final PatchLine line) {
    final int row = getCurrentRow();
    createCommentEditor(row + 1, 4, line.getLineB(), (short) 1);
  }

  @Override
  protected void render(final PatchScript script) {
    a = script.getSparseHtmlFileA();
    b = script.getSparseHtmlFileB();
    final ArrayList<Object> lines = new ArrayList<Object>();
    final SafeHtmlBuilder nc = new SafeHtmlBuilder();
    final boolean intraline =
        script.getDiffPrefs().isIntralineDifference()
            && script.hasIntralineDifference();

    appendHeader(script, nc);
    lines.add(null);

    if(script.getFileModeA()!=FileMode.FILE||script.getFileModeB()!=FileMode.FILE){
      openLine(nc);
      appendModeLine(nc, script.getFileModeA());
      appendModeLine(nc, script.getFileModeB());
      closeLine(nc);
      lines.add(null);
    }

    int lastA = 0;
    int lastB = 0;
    final boolean ignoreWS = script.isIgnoreWhitespace();
    for (final EditList.Hunk hunk : script.getHunks()) {
      if (!hunk.isStartOfFile()) {
        appendSkipLine(nc, hunk.getCurB() - lastB);
        lines.add(new SkippedLine(lastA, lastB, hunk.getCurB() - lastB));
      }

      while (hunk.next()) {
        if (hunk.isContextLine()) {
          openLine(nc);
          final SafeHtml ctx = a.getSafeHtmlLine(hunk.getCurA());
          appendLineText(nc, hunk.getCurA(), CONTEXT, ctx, false, false);
          if (ignoreWS && b.contains(hunk.getCurB())) {
            appendLineText(nc, hunk.getCurB(), CONTEXT, b, hunk.getCurB(),
                false);
          } else {
            appendLineText(nc, hunk.getCurB(), CONTEXT, ctx, false, false);
          }
          closeLine(nc);
          hunk.incBoth();
          lines.add(new PatchLine(CONTEXT, hunk.getCurA(), hunk.getCurB()));

        } else if (hunk.isModifiedLine()) {
          final boolean del = hunk.isDeletedA();
          final boolean ins = hunk.isInsertedB();
          final boolean full =
              intraline && hunk.getCurEdit().getType() != Edit.Type.REPLACE;
          openLine(nc);

          if (del) {
            appendLineText(nc, hunk.getCurA(), DELETE, a, hunk.getCurA(), full);
            hunk.incA();
          } else if (hunk.getCurEdit().getType() == Edit.Type.REPLACE) {
            appendLineNone(nc, DELETE);
          } else {
            appendLineNone(nc, CONTEXT);
          }

          if (ins) {
            appendLineText(nc, hunk.getCurB(), INSERT, b, hunk.getCurB(), full);
            hunk.incB();
          } else if (hunk.getCurEdit().getType() == Edit.Type.REPLACE) {
            appendLineNone(nc, INSERT);
          } else {
            appendLineNone(nc, CONTEXT);
          }

          closeLine(nc);

          if (del && ins) {
            lines.add(new PatchLine(REPLACE, hunk.getCurA(), hunk.getCurB()));
          } else if (del) {
            lines.add(new PatchLine(DELETE, hunk.getCurA(), 0));
          } else if (ins) {
            lines.add(new PatchLine(INSERT, 0, hunk.getCurB()));
          }
        }
      }
      lastA = hunk.getCurA();
      lastB = hunk.getCurB();
    }
    if (lastB != b.size()) {
      appendSkipLine(nc, b.size() - lastB);
      lines.add(new SkippedLine(lastA, lastB, b.size() - lastB));
    }
    resetHtml(nc);
    initScript(script);

    for (int row = 0; row < lines.size(); row++) {
      setRowItem(row, lines.get(row));
      if(lines.get(row) instanceof SkippedLine) {
        createSkipLine(row, (SkippedLine) lines.get(row));
      }
    }
  }

  private void appendModeLine(final SafeHtmlBuilder nc, final FileMode mode) {
    nc.openTd();
    nc.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    nc.nbsp();
    nc.closeTd();

    nc.openTd();
    nc.addStyleName(Gerrit.RESOURCES.css().fileLine());
    nc.addStyleName(Gerrit.RESOURCES.css().fileLineMode());
    switch(mode){
      case FILE:
        nc.nbsp();
        break;
      case SYMLINK:
        nc.append(PatchUtil.C.fileTypeSymlink());
        break;
      case GITLINK:
        nc.append(PatchUtil.C.fileTypeGitlink());
        break;
    }
    nc.closeTd();
  }

  @Override
  public void display(final CommentDetail cd, boolean expandComments) {
    if (cd.isEmpty()) {
      return;
    }
    setAccountInfoCache(cd.getAccounts());

    for (int row = 0; row < table.getRowCount();) {
      if (getRowItem(row) instanceof PatchLine) {
        final PatchLine pLine = (PatchLine) getRowItem(row);
        final List<PatchLineComment> fora = cd.getForA(pLine.getLineA());
        final List<PatchLineComment> forb = cd.getForB(pLine.getLineB());
        row++;

        final Iterator<PatchLineComment> ai = fora.iterator();
        final Iterator<PatchLineComment> bi = forb.iterator();
        while (ai.hasNext() && bi.hasNext()) {
          final PatchLineComment ac = ai.next();
          final PatchLineComment bc = bi.next();
          insertRow(row);
          bindComment(row, COL_A, ac, !ai.hasNext(), expandComments);
          bindComment(row, COL_B, bc, !bi.hasNext(), expandComments);
          row++;
        }

        row = finish(ai, row, COL_A, expandComments);
        row = finish(bi, row, COL_B, expandComments);
      } else {
        row++;
      }
    }
  }

  @Override
  protected void insertRow(final int row) {
    super.insertRow(row);
    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, COL_A - 1, Gerrit.RESOURCES.css().lineNumber());
    fmt.addStyleName(row, COL_A, Gerrit.RESOURCES.css().diffText());
    fmt.addStyleName(row, COL_B - 1, Gerrit.RESOURCES.css().lineNumber());
    fmt.addStyleName(row, COL_B, Gerrit.RESOURCES.css().diffText());
  }

  private int finish(final Iterator<PatchLineComment> i, int row, final int col, boolean expandComment) {
    while (i.hasNext()) {
      final PatchLineComment c = i.next();
      insertRow(row);
      bindComment(row, col, c, !i.hasNext(), expandComment);
      row++;
    }
    return row;
  }

  private void appendHeader(PatchScript script, final SafeHtmlBuilder m) {
    m.openTr();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().iconCell());
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.closeTd();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.addStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.setAttribute("width", "50%");
    if (script.getChangeType() == ChangeType.RENAMED
        || script.getChangeType() == ChangeType.COPIED) {
      m.append(script.getOldName());
    } else {
      m.append(PatchUtil.C.patchHeaderOld());
    }
    m.br();
    if (0 < script.getA().size()) {
      if (idSideA == null) {
        downloadLink(m, patchKey, "1");
      } else {
        downloadLink(m, new Patch.Key(idSideA, patchKey.get()), "0");
      }
    }
    m.closeTd();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.addStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.setAttribute("width", "50%");
    m.append(PatchUtil.C.patchHeaderNew());
    m.br();
    if (0 < script.getB().size()) {
      downloadLink(m, new Patch.Key(idSideB, patchKey.get()), "0");
    }
    m.closeTd();

    m.closeTr();
  }

  private void downloadLink(final SafeHtmlBuilder m, final Patch.Key key,
      final String side) {
    final String base = GWT.getHostPageBaseURL() + "cat/";
    m.openAnchor();
    m.setAttribute("href", base + KeyUtil.encode(key.toString()) + "^" + side);
    m.append(PatchUtil.C.download());
    m.closeAnchor();
  }

  private void appendSkipLine(final SafeHtmlBuilder m, final int skipCnt) {
    m.openTr();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().iconCell());
    m.addStyleName(Gerrit.RESOURCES.css().skipLine());
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().skipLine());
    m.setAttribute("colspan", 4);
    m.closeTd();
    m.closeTr();
  }

  ClickHandler expandAllListener = new ClickHandler() {
    @Override
    public void onClick(ClickEvent event) {
      expand(event, 0);
    }
  };

  ClickHandler expandBeforeListener = new ClickHandler() {
    @Override
    public void onClick(ClickEvent event) {
      expand(event, NUM_ROWS_TO_EXPAND);
    }
  };

  ClickHandler expandAfterListener = new ClickHandler() {
    @Override
    public void onClick(ClickEvent event) {
      expand(event, -NUM_ROWS_TO_EXPAND);
    }
  };

  private void expand(ClickEvent event, final int numRows) {
    Cell cell = table.getCellForEvent(event);
    int row = cell.getRowIndex();
    if(!(getRowItem(row) instanceof SkippedLine)) {
      return;
    }
    SkippedLine line = (SkippedLine) getRowItem(row);
    int loopTo = numRows;
    if(numRows == 0) {
      loopTo = line.getSize();
    } else if(numRows < 0) {
      loopTo = -numRows;
    }
    int offset = 0;
    if(numRows < 0) {
      offset = 1;
    }
    for(int i = 0 + offset; i < loopTo + offset; i++) {
      insertRow(row + i);
      int lineA = line.getStartA() + i;
      int lineB = line.getStartB() + i;
      if(numRows < 0) {
        lineA = line.getStartA() + line.getSize() + numRows + i - offset;
        lineB = line.getStartB() + line.getSize() + numRows + i - offset;
      }
      setHtml(row + i, 1,
          "<a href=\"javascript:void(0)\">" + (lineA + 1)
              + "</a>");
      setHtml(row + i, 2, a.getSafeHtmlLine(lineA).asString());
      setHtml(row + i, 3,
          "<a href=\"javascript:void(0)\">" + (lineB + 1)
              + "</a>");
      setHtml(row + i, 4, b.getSafeHtmlLine(lineB).asString());
      setRowItem(row + i,
          new PatchLine(CONTEXT, lineA, lineB));
    }

    if (numRows > 0) {
      line.incrementStart(numRows);
      createSkipLine(row + loopTo, line);
    } else if (numRows < 0) {
      line.reduceSize(-numRows);
      createSkipLine(row, line);
    } else {
      removeRow(row + loopTo);
    }
  }

  private void createSkipLine(int row, SkippedLine line) {
    FlowPanel p = new FlowPanel();
    Label l1 = new Label(" " + PatchUtil.C.patchSkipRegionStart() + " ");
    Anchor all = new Anchor(String.valueOf(line.getSize()));
    Label l2 = new Label(" " + PatchUtil.C.patchSkipRegionEnd() + " ");
    all.addClickHandler(expandAllListener);
    if(line.getSize() > 30) {
      // We only show the expand before & after links if we skip more than
      // 30 lines.
      Anchor before = new Anchor(PatchUtil.M.expandBefore(NUM_ROWS_TO_EXPAND));
      before.addClickHandler(expandBeforeListener);
      Anchor after = new Anchor(PatchUtil.M.expandAfter(NUM_ROWS_TO_EXPAND));
      after.addClickHandler(expandAfterListener);
      p.add(before);
      p.add(l1);
      p.add(all);
      p.add(l2);
      p.add(after);
    } else {
      p.add(l1);
      p.add(all);
      p.add(l2);
    }
    setWidget(row, 1, p);
  }

  private void openLine(final SafeHtmlBuilder m) {
    m.openTr();
    m.setAttribute("valign", "top");

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().iconCell());
    m.closeTd();
  }

  private void appendLineText(final SafeHtmlBuilder m,
      final int lineNumberMinusOne, final PatchLine.Type type,
      final SparseHtmlFile src, final int i, final boolean fullBlock) {
    appendLineText(m, lineNumberMinusOne, type, //
        src.getSafeHtmlLine(i), src.hasTrailingEdit(i), fullBlock);
  }

  private void appendLineText(final SafeHtmlBuilder m,
      final int lineNumberMinusOne, final PatchLine.Type type,
      final SafeHtml lineHtml, final boolean trailingEdit,
      final boolean fullBlock) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.append(SafeHtml.asis("<a href=\"javascript:void(0)\">"+ (lineNumberMinusOne + 1) + "</a>"));
    m.closeTd();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().fileLine());
    switch (type) {
      case CONTEXT:
        m.addStyleName(Gerrit.RESOURCES.css().fileLineCONTEXT());
        break;
      case DELETE:
        m.addStyleName(Gerrit.RESOURCES.css().fileLineDELETE());
        if (trailingEdit || fullBlock) {
          m.addStyleName("wdd");
        }
        break;
      case INSERT:
        m.addStyleName(Gerrit.RESOURCES.css().fileLineINSERT());
        if (trailingEdit || fullBlock) {
          m.addStyleName("wdi");
        }
        break;
    }
    m.append(lineHtml);
    m.closeTd();
  }

  private void appendLineNone(final SafeHtmlBuilder m, final PatchLine.Type type) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.closeTd();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().fileLine());
    switch (type != null ? type : PatchLine.Type.CONTEXT) {
      case DELETE:
        m.addStyleName(Gerrit.RESOURCES.css().fileLineDELETE());
        break;
      case INSERT:
        m.addStyleName(Gerrit.RESOURCES.css().fileLineINSERT());
        break;
      default:
        m.addStyleName(Gerrit.RESOURCES.css().fileLineNone());
        break;
    }
    m.closeTd();
  }

  private void closeLine(final SafeHtmlBuilder m) {
    m.closeTr();
  }
}
