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
import com.google.gerrit.common.data.EditList;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.SparseFileContent;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.safehtml.client.PrettyFormatter;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SideBySideTable extends AbstractPatchContentTable {
  private static final int COL_A = 2;
  private static final int COL_B = 4;

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
  protected void onInsertComment(final PatchLine line) {
    final int row = getCurrentRow();
    createCommentEditor(row + 1, 4, line.getLineB(), (short) 1);
  }

  @Override
  protected void render(final PatchScript script) {
    final SparseFileContent a = script.getA();
    final SparseFileContent b = script.getB();
    final PrettyFormatter fmtA = PrettyFormatter.newFormatter(formatLanguage);
    final PrettyFormatter fmtB = PrettyFormatter.newFormatter(formatLanguage);
    final ArrayList<PatchLine> lines = new ArrayList<PatchLine>();
    final SafeHtmlBuilder nc = new SafeHtmlBuilder();

    fmtB.setShowWhiteSpaceErrors(true);
    appendHeader(nc);
    lines.add(null);

    int lastB = 0;
    final boolean ignoreWS = script.isIgnoreWhitespace();
    for (final EditList.Hunk hunk : script.getHunks()) {
      if (!hunk.isStartOfFile()) {
        appendSkipLine(nc, hunk.getCurB() - lastB);
        lines.add(null);
      }

      while (hunk.next()) {
        if (hunk.isContextLine()) {
          openLine(nc);
          final SafeHtml ctx = fmtA.format(a.get(hunk.getCurA()));
          appendLineText(nc, hunk.getCurA(), CONTEXT, ctx);
          if (ignoreWS && b.contains(hunk.getCurB())) {
            appendLineText(nc, hunk.getCurB(), CONTEXT, b, hunk.getCurB(), fmtB);
          } else {
            appendLineText(nc, hunk.getCurB(), CONTEXT, ctx);
          }
          closeLine(nc);
          hunk.incBoth();
          lines.add(new PatchLine(CONTEXT, hunk.getCurA(), hunk.getCurB()));

        } else if (hunk.isModifiedLine()) {
          final boolean del = hunk.isDeletedA();
          final boolean ins = hunk.isInsertedB();
          openLine(nc);

          if (del) {
            appendLineText(nc, hunk.getCurA(), DELETE, a, hunk.getCurA(), fmtA);
            hunk.incA();
          } else {
            appendLineNone(nc);
          }

          if (ins) {
            appendLineText(nc, hunk.getCurB(), INSERT, b, hunk.getCurB(), fmtB);
            hunk.incB();
          } else {
            appendLineNone(nc);
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
      lastB = hunk.getCurB();
    }
    if (lastB != b.size()) {
      appendSkipLine(nc, b.size() - lastB);
    }
    resetHtml(nc);
    initScript(script);

    for (int row = 0; row < lines.size(); row++) {
      setRowItem(row, lines.get(row));
    }
  }

  @Override
  public void display(final CommentDetail cd) {
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
          bindComment(row, COL_A, ac, !ai.hasNext());
          bindComment(row, COL_B, bc, !bi.hasNext());
          row++;
        }

        row = finish(ai, row, COL_A);
        row = finish(bi, row, COL_B);
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

  private int finish(final Iterator<PatchLineComment> i, int row, final int col) {
    while (i.hasNext()) {
      final PatchLineComment c = i.next();
      insertRow(row);
      bindComment(row, col, c, !i.hasNext());
      row++;
    }
    return row;
  }

  private void appendHeader(final SafeHtmlBuilder m) {
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
    m.append(PatchUtil.C.patchHeaderOld());
    m.closeTd();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.addStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.setAttribute("width", "50%");
    m.append(PatchUtil.C.patchHeaderNew());
    m.closeTd();

    m.closeTr();
  }

  private void appendSkipLine(final SafeHtmlBuilder m, final int skipCnt) {
    m.openTr();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().iconCell());
    m.closeTd();

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().skipLine());
    m.setAttribute("colspan", 4);
    m.append(PatchUtil.M.patchSkipRegion(skipCnt));
    m.closeTd();
    m.closeTr();
  }

  private void openLine(final SafeHtmlBuilder m) {
    m.openTr();
    m.setAttribute("valign", "top");

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().iconCell());
    m.closeTd();
  }

  private SafeHtml appendLineText(final SafeHtmlBuilder m,
      final int lineNumberMinusOne, final PatchLine.Type type,
      final SparseFileContent src, final int i, final PrettyFormatter dst) {
    final SafeHtml lineHtml = dst.format(src.get(i));
    appendLineText(m, lineNumberMinusOne, type, lineHtml);
    return lineHtml;
  }

  private void appendLineText(final SafeHtmlBuilder m,
      final int lineNumberMinusOne, final PatchLine.Type type,
      final SafeHtml lineHtml) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.append(lineNumberMinusOne + 1);
    m.closeTd();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().fileLine());
    switch(type){
      case CONTEXT:
        m.addStyleName(Gerrit.RESOURCES.css().fileLineCONTEXT());
        break;
      case DELETE:
        m.addStyleName(Gerrit.RESOURCES.css().fileLineDELETE());
        break;
      case INSERT:
        m.addStyleName(Gerrit.RESOURCES.css().fileLineINSERT());
        break;
    }
    m.append(lineHtml);
    m.closeTd();
  }

  private void appendLineNone(final SafeHtmlBuilder m) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.closeTd();

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().fileLine());
    m.addStyleName(Gerrit.RESOURCES.css().fileLineNone());
    m.closeTd();
  }

  private void closeLine(final SafeHtmlBuilder m) {
    m.closeTr();
  }
}
