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

import com.google.gerrit.client.data.PatchScript;
import com.google.gerrit.client.data.SparseFileContent;
import com.google.gerrit.client.reviewdb.PatchLineComment;
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
    final ArrayList<PatchLine> lines = new ArrayList<PatchLine>();
    final SafeHtmlBuilder nc = new SafeHtmlBuilder();

    appendHeader(nc);
    lines.add(null);

    int lastB = 0;
    for (final PatchScript.Hunk hunk : script.getHunks()) {
      if (!hunk.isStartOfFile()) {
        appendSkipLine(nc, hunk.getCurB() - lastB);
        lines.add(null);
      }

      while (hunk.hasNextLine()) {
        if (hunk.isContextLine()) {
          openLine(nc);
          appendLineText(nc, hunk.getCurA(), CONTEXT, a, hunk.getCurA());
          appendLineText(nc, hunk.getCurB(), CONTEXT, a, hunk.getCurA());
          closeLine(nc);
          hunk.incBoth();
          lines.add(new PatchLine(CONTEXT, hunk.getCurA(), hunk.getCurB()));

        } else if (hunk.isModifiedLine()) {
          final boolean del = hunk.isDeletedA();
          final boolean ins = hunk.isInsertedB();
          openLine(nc);

          if (del) {
            appendLineText(nc, hunk.getCurA(), DELETE, a, hunk.getCurA());
            hunk.incA();
          } else {
            appendLineNone(nc);
          }

          if (ins) {
            appendLineText(nc, hunk.getCurB(), INSERT, b, hunk.getCurB());
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

        hunk.next();
      }
      lastB = hunk.getCurB();
    }
    if (lastB != b.size()) {
      appendSkipLine(nc, b.size() - lastB);
    }
    resetHtml(nc);

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
          table.insertRow(row);
          table.getCellFormatter().setStyleName(row, 0, S_ICON_CELL);
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

  private int finish(final Iterator<PatchLineComment> i, int row, final int col) {
    while (i.hasNext()) {
      final PatchLineComment c = i.next();
      table.insertRow(row);
      table.getCellFormatter().setStyleName(row, 0, S_ICON_CELL);
      bindComment(row, col, c, !i.hasNext());
      row++;
    }
    return row;
  }


  private void appendHeader(final SafeHtmlBuilder m) {
    m.openTr();

    m.openTd();
    m.addStyleName(S_ICON_CELL);
    m.addStyleName("FileColumnHeader");
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.addStyleName("FileColumnHeader");
    m.addStyleName("LineNumber");
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName("FileColumnHeader");
    m.setAttribute("width", "50%");
    m.append(PatchUtil.C.patchHeaderOld());
    m.closeTd();

    m.openTd();
    m.addStyleName("FileColumnHeader");
    m.addStyleName("LineNumber");
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName("FileColumnHeader");
    m.setAttribute("width", "50%");
    m.append(PatchUtil.C.patchHeaderNew());
    m.closeTd();

    m.closeTr();
  }

  private void appendSkipLine(final SafeHtmlBuilder m, final int skipCnt) {
    m.openTr();

    m.openTd();
    m.setStyleName(S_ICON_CELL);
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName("SkipLine");
    m.setAttribute("colspan", 4);
    m.append(PatchUtil.M.patchSkipRegion(skipCnt));
    m.closeTd();
    m.closeTr();
  }

  private void openLine(final SafeHtmlBuilder m) {
    m.openTr();
    m.setAttribute("valign", "top");

    m.openTd();
    m.setStyleName(S_ICON_CELL);
    m.nbsp();
    m.closeTd();
  }

  private void appendLineText(final SafeHtmlBuilder m,
      final int lineNumberMinusOne, final PatchLine.Type type,
      final SparseFileContent src, final int i) {
    m.openTd();
    m.setStyleName("LineNumber");
    m.append(lineNumberMinusOne + 1);
    m.closeTd();

    m.openTd();
    m.addStyleName("FileLine");
    m.addStyleName("FileLine-" + type.name());
    final String text = src.get(i);
    if ("".equals(text)) {
      m.nbsp();
    } else {
      final boolean ws = type == INSERT;
      m.append(PatchUtil
          .lineToSafeHtml(text, PatchUtil.DEFAULT_LINE_LENGTH, ws));
    }
    m.closeTd();
  }

  private void appendLineNone(final SafeHtmlBuilder m) {
    m.openTd();
    m.setStyleName("LineNumber");
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.addStyleName("FileLine");
    m.addStyleName("FileLineNone");
    m.nbsp();
    m.closeTd();
  }

  private void closeLine(final SafeHtmlBuilder m) {
    m.closeTr();
  }
}
