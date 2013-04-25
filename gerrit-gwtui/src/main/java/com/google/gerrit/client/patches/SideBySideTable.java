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
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.common.data.PatchScript.FileMode;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.SparseHtmlFile;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import org.eclipse.jgit.diff.Edit;

import java.util.ArrayList;
import java.util.Iterator;

public class SideBySideTable extends AbstractPatchContentTable {
  private static final int A = 2;
  private static final int B = 3;
  private static final int NUM_ROWS_TO_EXPAND = 10;

  private SparseHtmlFile a;
  private SparseHtmlFile b;
  protected boolean isFileCommentBorderRowExist;

  protected void createFileCommentEditorOnSideA() {
    createCommentEditor(R_HEAD + 1, A, R_HEAD, FILE_SIDE_A);
    return;
  }

  protected void createFileCommentEditorOnSideB() {
    createCommentEditor(R_HEAD + 1, B, R_HEAD, FILE_SIDE_B);
    return;
  }

  @Override
  protected void onCellDoubleClick(final int row, int column) {
    if (column > C_ARROW && getRowItem(row) instanceof PatchLine) {
      final PatchLine line = (PatchLine) getRowItem(row);
      if (column == 1 || column == A) {
        createCommentEditor(row + 1, A, line.getLineA(), (short) 0);
      } else if (column == B || column == 4) {
        createCommentEditor(row + 1, B, line.getLineB(), (short) 1);
      }
    }
  }

  @Override
  protected void onCellSingleClick(int row, int column) {
    super.onCellSingleClick(row, column);
    if (column == 1 || column == 4) {
      onCellDoubleClick(row, column);
    }
  }

  @Override
  protected void onInsertComment(final PatchLine line) {
    final int row = getCurrentRow();
    createCommentEditor(row + 1, B, line.getLineB(), (short) 1);
  }

  @Override
  protected void render(final PatchScript script, final PatchSetDetail detail) {
    final ArrayList<Object> lines = new ArrayList<Object>();
    final SafeHtmlBuilder nc = new SafeHtmlBuilder();
    allocateTableHeader(script, nc);
    lines.add(null);
    if (!isDisplayBinary) {
      if (script.getFileModeA() != FileMode.FILE
          || script.getFileModeB() != FileMode.FILE) {
        openLine(nc);
        appendModeLine(nc, script.getFileModeA());
        appendModeLine(nc, script.getFileModeB());
        closeLine(nc);
        lines.add(null);
      }

      if (hasDifferences(script)) {
        int lastA = 0;
        int lastB = 0;
        final boolean ignoreWS = script.isIgnoreWhitespace();
        a = getSparseHtmlFileA(script);
        b = getSparseHtmlFileB(script);
        final boolean intraline =
            script.getDiffPrefs().isIntralineDifference()
                && script.hasIntralineDifference();
        for (final EditList.Hunk hunk : script.getHunks()) {
          if (!hunk.isStartOfFile()) {
            appendSkipLine(nc, hunk.getCurB() - lastB);
            lines.add(new SkippedLine(lastA, lastB, hunk.getCurB() - lastB));
          }

          while (hunk.next()) {
            if (hunk.isContextLine()) {
              openLine(nc);
              final SafeHtml ctx = a.getSafeHtmlLine(hunk.getCurA());
              appendLineNumber(nc, hunk.getCurA(), false);
              appendLineText(nc, CONTEXT, ctx, false, false);
              if (ignoreWS && b.contains(hunk.getCurB())) {
                appendLineText(nc, CONTEXT, b, hunk.getCurB(), false);
              } else {
                appendLineText(nc, CONTEXT, ctx, false, false);
              }
              appendLineNumber(nc, hunk.getCurB(), true);
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
                appendLineNumber(nc, hunk.getCurA(), false);
                appendLineText(nc, DELETE, a, hunk.getCurA(), full);
                hunk.incA();
              } else if (hunk.getCurEdit().getType() == Edit.Type.REPLACE) {
                appendLineNumber(nc, false);
                appendLineNone(nc, DELETE);
              } else {
                appendLineNumber(nc, false);
                appendLineNone(nc, CONTEXT);
              }

              if (ins) {
                appendLineText(nc, INSERT, b, hunk.getCurB(), full);
                appendLineNumber(nc, hunk.getCurB(), true);
                hunk.incB();
              } else if (hunk.getCurEdit().getType() == Edit.Type.REPLACE) {
                appendLineNone(nc, INSERT);
                appendLineNumber(nc, true);
              } else {
                appendLineNone(nc, CONTEXT);
                appendLineNumber(nc, true);
              }

              closeLine(nc);

              if (del && ins) {
                lines.add(new PatchLine(REPLACE, hunk.getCurA(), hunk.getCurB()));
              } else if (del) {
                lines.add(new PatchLine(DELETE, hunk.getCurA(), -1));
              } else if (ins) {
                lines.add(new PatchLine(INSERT, -1, hunk.getCurB()));
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
      }
    } else {
      // Display the patch header for binary
      for (final String line : script.getPatchHeader()) {
        appendFileHeader(nc, line);
      }
      // If there is a safe picture involved, we show it
      if (script.getDisplayMethodA() == DisplayMethod.IMG
          || script.getDisplayMethodB() == DisplayMethod.IMG) {
        appendImageLine(script, nc);
      }
    }
    if (!hasDifferences(script)) {
      appendNoDifferences(nc);
    }
    resetHtml(nc);
    populateTableHeader(script, detail);
    if (hasDifferences(script)) {
      initScript(script);
      if (!isDisplayBinary) {
        for (int row = 0; row < lines.size(); row++) {
          setRowItem(row, lines.get(row));
          if (lines.get(row) instanceof SkippedLine) {
            createSkipLine(row, (SkippedLine) lines.get(row));
          }
        }
      }
    }
  }

  private SafeHtml createImage(String url) {
    SafeHtmlBuilder m = new SafeHtmlBuilder();
    m.openElement("img");
    m.setAttribute("src", url);
    m.closeElement("img");
    return m.toSafeHtml();
  }

  private void appendImageLine(final PatchScript script,
      final SafeHtmlBuilder m) {
    m.openTr();
    m.setAttribute("valign", "center");
    m.setAttribute("align", "center");

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().iconCell());
    m.closeTd();

    appendLineNumber(m, false);
    if (script.getDisplayMethodA() == DisplayMethod.IMG) {
      final String url = getUrlA();
      appendLineText(m, DELETE, createImage(url), false, true);
    } else {
      appendLineNone(m, DELETE);
    }
    if (script.getDisplayMethodB() == DisplayMethod.IMG) {
      final String url = getUrlB();
      appendLineText(m, INSERT, createImage(url), false, true);
    } else {
      appendLineNone(m, INSERT);
    }

    appendLineNumber(m, true);
    m.closeTr();
  }

  private void populateTableHeader(final PatchScript script,
      final PatchSetDetail detail) {
    initHeaders(script, detail);
    table.setWidget(R_HEAD, A, headerSideA);
    table.setWidget(R_HEAD, B, headerSideB);

    // Populate icons to lineNumber column header.
    if (headerSideA.isFileOrCommitMessage()) {
      table.setWidget(R_HEAD, A - 1, iconA);
    }
    if (headerSideB.isFileOrCommitMessage()) {
      table.setWidget(R_HEAD, B + 1, iconB);
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
  protected PatchScreen.Type getPatchScreenType() {
    return PatchScreen.Type.SIDE_BY_SIDE;
  }

  @Override
  public void display(final CommentDetail cd, boolean expandComments) {
    if (cd.isEmpty()) {
      return;
    }
    setAccountInfoCache(cd.getAccounts());

    for (int row = 0; row < table.getRowCount();) {
      final Iterator<PatchLineComment> ai;
      final Iterator<PatchLineComment> bi;

      if (row == R_HEAD) {
        ai = cd.getForA(R_HEAD).iterator();
        bi = cd.getForB(R_HEAD).iterator();
      } else if (getRowItem(row) instanceof PatchLine) {
        final PatchLine pLine = (PatchLine) getRowItem(row);
        ai = cd.getForA(pLine.getLineA()).iterator();
        bi = cd.getForB(pLine.getLineB()).iterator();
      } else {
        row++;
        continue;
      }

      row++;
      while (ai.hasNext() && bi.hasNext()) {
        final PatchLineComment ac = ai.next();
        final PatchLineComment bc = bi.next();
        if (ac.getLine() == R_HEAD) {
          insertFileCommentRow(row);
        } else {
          insertRow(row);
        }
        bindComment(row, A, ac, !ai.hasNext(), expandComments);
        bindComment(row, B, bc, !bi.hasNext(), expandComments);
        row++;
      }

      row = finish(ai, row, A, expandComments);
      row = finish(bi, row, B, expandComments);
    }
  }

  private void defaultStyle(final int row, final CellFormatter fmt) {
    fmt.addStyleName(row, A - 1, Gerrit.RESOURCES.css().lineNumber());
    fmt.addStyleName(row, A, Gerrit.RESOURCES.css().diffText());
    if (isDisplayBinary) {
      fmt.addStyleName(row, A, Gerrit.RESOURCES.css().diffTextForBinaryInSideBySide());
    }
    fmt.addStyleName(row, B, Gerrit.RESOURCES.css().diffText());
    fmt.addStyleName(row, B + 1, Gerrit.RESOURCES.css().lineNumber());
    fmt.addStyleName(row, B + 1, Gerrit.RESOURCES.css().rightmost());
  }

  @Override
  protected void insertRow(final int row) {
    super.insertRow(row);
    final CellFormatter fmt = table.getCellFormatter();
    defaultStyle(row, fmt);
  }

  @Override
  protected void insertFileCommentRow(final int row) {
    table.insertRow(row);
    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, C_ARROW, Gerrit.RESOURCES.css().iconCellOfFileCommentRow());
    defaultStyle(row, fmt);

    fmt.addStyleName(row, C_ARROW, //
        Gerrit.RESOURCES.css().cellsNextToFileComment());
    fmt.addStyleName(row, A - 1, //
        Gerrit.RESOURCES.css().cellsNextToFileComment());
    fmt.addStyleName(row, B + 1, //
        Gerrit.RESOURCES.css().cellsNextToFileComment());
    createFileCommentBorderRow(row);
  }

  private void createFileCommentBorderRow(final int row) {
    if (row == 1 && !isFileCommentBorderRowExist) {
      isFileCommentBorderRowExist = true;
      table.insertRow(R_HEAD + 2);

      final CellFormatter fmt = table.getCellFormatter();

      fmt.addStyleName(R_HEAD + 2, C_ARROW, //
          Gerrit.RESOURCES.css().iconCellOfFileCommentRow());
      defaultStyle(R_HEAD + 2, fmt);

      final Element iconCell = fmt.getElement(R_HEAD + 2, C_ARROW);
      UIObject.setStyleName(DOM.getParent(iconCell), Gerrit.RESOURCES.css()
          .fileCommentBorder(), true);
    }
  }

  private int finish(final Iterator<PatchLineComment> i, int row, final int col, boolean expandComment) {
    while (i.hasNext()) {
      final PatchLineComment c = i.next();
      if (c.getLine() == R_HEAD) {
        insertFileCommentRow(row);
      } else {
        insertRow(row);
      }
      bindComment(row, col, c, !i.hasNext(), expandComment);
      row++;
    }
    return row;
  }

  private void allocateTableHeader(PatchScript script, final SafeHtmlBuilder m) {
    m.openTr();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().iconCell());
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.addStyleName(Gerrit.RESOURCES.css().fileLine());
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.addStyleName(Gerrit.RESOURCES.css().fileLine());
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.addStyleName(Gerrit.RESOURCES.css().rightmost());
    m.closeTd();

    m.closeTr();
  }

  private void appendFileHeader(final SafeHtmlBuilder m, final String line) {
    m.openTr();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().iconCell());
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().sideBySideTableBinaryHeader());
    m.setAttribute("colspan", 2);
    m.append(line);
    m.closeTd();

    m.openTd();
    m.nbsp();
    m.closeTd();

    m.closeTr();
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

  private ClickHandler expandAllListener = new ClickHandler() {
    @Override
    public void onClick(ClickEvent event) {
      expand(event, 0);
    }
  };

  private ClickHandler expandBeforeListener = new ClickHandler() {
    @Override
    public void onClick(ClickEvent event) {
      expand(event, NUM_ROWS_TO_EXPAND);
    }
  };

  private ClickHandler expandAfterListener = new ClickHandler() {
    @Override
    public void onClick(ClickEvent event) {
      expand(event, -NUM_ROWS_TO_EXPAND);
    }
  };

  private void expand(ClickEvent event, final int numRows) {
    int row = table.getCellForEvent(event).getRowIndex();
    if (!(getRowItem(row) instanceof SkippedLine)) {
      return;
    }

    SkippedLine line = (SkippedLine) getRowItem(row);
    int loopTo = numRows;
    if (numRows == 0) {
      loopTo = line.getSize();
    } else if (numRows < 0) {
      loopTo = -numRows;
    }
    int offset = 0;
    if (numRows < 0) {
      offset = 1;
    }

    CellFormatter fmt = table.getCellFormatter();
    for (int i = 0 + offset; i < loopTo + offset; i++) {
      insertRow(row + i);
      table.getRowFormatter().setVerticalAlign(row + i,
          HasVerticalAlignment.ALIGN_TOP);
      int lineA = line.getStartA() + i;
      int lineB = line.getStartB() + i;
      if (numRows < 0) {
        lineA = line.getStartA() + line.getSize() + numRows + i - offset;
        lineB = line.getStartB() + line.getSize() + numRows + i - offset;
      }

      table.setHTML(row + i, A - 1, "<a href=\"javascript:;\">" + (lineA + 1) + "</a>");
      fmt.addStyleName(row + i, A - 1, Gerrit.RESOURCES.css().lineNumber());

      table.setHTML(row + i, A, a.getSafeHtmlLine(lineA).asString());
      fmt.addStyleName(row + i, A, Gerrit.RESOURCES.css().fileLine());
      fmt.addStyleName(row + i, A, Gerrit.RESOURCES.css().fileLineCONTEXT());

      table.setHTML(row + i, B, b.getSafeHtmlLine(lineB).asString());
      fmt.addStyleName(row + i, B, Gerrit.RESOURCES.css().fileLine());
      fmt.addStyleName(row + i, B, Gerrit.RESOURCES.css().fileLineCONTEXT());

      table.setHTML(row + i, B + 1, "<a href=\"javascript:;\">" + (lineB + 1) + "</a>");
      fmt.addStyleName(row + i, B + 1, Gerrit.RESOURCES.css().lineNumber());

      setRowItem(row + i, new PatchLine(CONTEXT, lineA, lineB));
    }

    if (numRows > 0) {
      line.incrementStart(numRows);
      createSkipLine(row + loopTo, line);
    } else if (numRows < 0) {
      line.reduceSize(-numRows);
      createSkipLine(row, line);
    } else {
      table.removeRow(row + loopTo);
    }
  }

  private void createSkipLine(int row, SkippedLine line) {
    FlowPanel p = new FlowPanel();
    InlineLabel l1 = new InlineLabel(" " + PatchUtil.C.patchSkipRegionStart() + " ");
    InlineLabel l2 = new InlineLabel(" " + PatchUtil.C.patchSkipRegionEnd() + " ");

    Anchor all = new Anchor(String.valueOf(line.getSize()));
    all.addClickHandler(expandAllListener);
    all.setStyleName(Gerrit.RESOURCES.css().skipLine());

    if (line.getSize() > 30) {
      // Only show the expand before/after if skipped more than 30 lines.
      Anchor b = new Anchor(PatchUtil.M.expandBefore(NUM_ROWS_TO_EXPAND), true);
      Anchor a = new Anchor(PatchUtil.M.expandAfter(NUM_ROWS_TO_EXPAND), true);

      b.addClickHandler(expandBeforeListener);
      a.addClickHandler(expandAfterListener);

      b.setStyleName(Gerrit.RESOURCES.css().skipLine());
      a.setStyleName(Gerrit.RESOURCES.css().skipLine());

      p.add(b);
      p.add(l1);
      p.add(all);
      p.add(l2);
      p.add(a);
    } else {
      p.add(l1);
      p.add(all);
      p.add(l2);
    }
    table.setWidget(row, 1, p);
  }

  private void openLine(final SafeHtmlBuilder m) {
    m.openTr();
    m.setAttribute("valign", "top");

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().iconCell());
    m.closeTd();
  }

  private void appendLineNumber(SafeHtmlBuilder m, boolean right) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    if (right) {
      m.addStyleName(Gerrit.RESOURCES.css().rightmost());
    }
    m.closeTd();
  }

  private void appendLineNumber(SafeHtmlBuilder m, int lineNumberMinusOne, boolean right) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    if (right) {
      m.addStyleName(Gerrit.RESOURCES.css().rightmost());
    }
    m.append(SafeHtml.asis("<a href=\"javascript:;\">"+ (lineNumberMinusOne + 1) + "</a>"));
    m.closeTd();
  }

  private void appendLineText(final SafeHtmlBuilder m,
      final PatchLine.Type type, final SparseHtmlFile src, final int i,
      final boolean fullBlock) {
    appendLineText(m, type, src.getSafeHtmlLine(i), src.hasTrailingEdit(i), fullBlock);
  }

  private void appendLineText(final SafeHtmlBuilder m,
      final PatchLine.Type type, final SafeHtml lineHtml,
      final boolean trailingEdit, final boolean fullBlock) {
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
      case REPLACE:
        break;
    }
    m.append(lineHtml);
    m.closeTd();
  }

  private void appendLineNone(final SafeHtmlBuilder m, final PatchLine.Type type) {
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

  @Override
  protected void destroyCommentRow(final int row) {
    super.destroyCommentRow(row);
    if (row == R_HEAD + 1) {
      table.removeRow(row);
      isFileCommentBorderRowExist = false;
    }
  }
}
