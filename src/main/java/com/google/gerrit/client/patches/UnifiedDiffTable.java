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

import com.google.gerrit.client.data.PatchScript;
import com.google.gerrit.client.data.SparseFileContent;
import com.google.gerrit.client.data.PatchScript.Hunk;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class UnifiedDiffTable extends AbstractPatchContentTable {
  private static final int PC = 3;
  private static final Comparator<PatchLineComment> BY_DATE =
      new Comparator<PatchLineComment>() {
        public int compare(final PatchLineComment o1, final PatchLineComment o2) {
          return o1.getWrittenOn().compareTo(o2.getWrittenOn());
        }
      };

  @Override
  protected void onCellDoubleClick(final int row, final int column) {
    if (getRowItem(row) instanceof PatchLine) {
      final PatchLine pl = (PatchLine) getRowItem(row);
      switch (pl.getType()) {
        case DELETE:
        case CONTEXT:
          createCommentEditor(row + 1, PC, pl.getLineA(), (short) 0);
          break;
        case INSERT:
          createCommentEditor(row + 1, PC, pl.getLineB(), (short) 1);
          break;
      }
    }
  }

  @Override
  protected void onInsertComment(final PatchLine pl) {
    final int row = getCurrentRow();
    switch (pl.getType()) {
      case DELETE:
      case CONTEXT:
        createCommentEditor(row + 1, PC, pl.getLineA(), (short) 0);
        break;
      case INSERT:
        createCommentEditor(row + 1, PC, pl.getLineB(), (short) 1);
        break;
    }
  }

  @Override
  protected void render(final PatchScript script) {
    final SparseFileContent a = script.getA();
    final SparseFileContent b = script.getB();
    final SafeHtmlBuilder nc = new SafeHtmlBuilder();
    for (final String line : script.getPatchHeader()) {
      appendFileHeader(nc, line);
    }

    final ArrayList<PatchLine> lines = new ArrayList<PatchLine>();
    for (final PatchScript.Hunk hunk : script.getHunks()) {
      appendHunkHeader(nc, hunk);
      while (hunk.hasNextLine()) {
        if (hunk.isContextLine()) {
          openLine(nc);
          appendLineNumber(nc, hunk.getCurA());
          appendLineNumber(nc, hunk.getCurB());
          appendLineText(nc, CONTEXT, a, hunk.getCurA());
          closeLine(nc);
          hunk.incBoth();
          lines.add(new PatchLine(CONTEXT, hunk.getCurA(), hunk.getCurB()));

        } else if (hunk.isDeletedA()) {
          openLine(nc);
          appendLineNumber(nc, hunk.getCurA());
          padLineNumber(nc);
          appendLineText(nc, DELETE, a, hunk.getCurA());
          closeLine(nc);
          hunk.incA();
          lines.add(new PatchLine(DELETE, hunk.getCurA(), 0));
          if (a.size() == hunk.getCurA() && a.isMissingNewlineAtEnd())
            appendNoLF(nc);

        } else if (hunk.isInsertedB()) {
          openLine(nc);
          padLineNumber(nc);
          appendLineNumber(nc, hunk.getCurB());
          appendLineText(nc, INSERT, b, hunk.getCurB());
          closeLine(nc);
          hunk.incB();
          lines.add(new PatchLine(INSERT, 0, hunk.getCurB()));
          if (b.size() == hunk.getCurB() && b.isMissingNewlineAtEnd())
            appendNoLF(nc);
        }

        hunk.next();
      }
    }
    resetHtml(nc);

    int row = script.getPatchHeader().size();
    final CellFormatter fmt = table.getCellFormatter();
    final Iterator<PatchLine> iLine = lines.iterator();
    while (iLine.hasNext()) {
      final PatchLine l = iLine.next();
      final String n = "DiffText-" + l.getType().name();
      while (!fmt.getStyleName(row, PC).contains(n)) {
        row++;
      }
      setRowItem(row++, l);
    }
  }

  @Override
  public void display(final CommentDetail cd) {
    if (cd.isEmpty()) {
      return;
    }
    setAccountInfoCache(cd.getAccounts());

    final ArrayList<PatchLineComment> all = new ArrayList<PatchLineComment>();
    for (int row = 0; row < table.getRowCount();) {
      if (getRowItem(row) instanceof PatchLine) {
        final PatchLine pLine = (PatchLine) getRowItem(row);
        final List<PatchLineComment> fora = cd.getForA(pLine.getLineA());
        final List<PatchLineComment> forb = cd.getForB(pLine.getLineB());
        row++;

        if (!fora.isEmpty() && !forb.isEmpty()) {
          all.clear();
          all.addAll(fora);
          all.addAll(forb);
          Collections.sort(all, BY_DATE);
          row = insert(all, row);

        } else if (!fora.isEmpty()) {
          row = insert(fora, row);

        } else if (!forb.isEmpty()) {
          row = insert(forb, row);
        }
      } else {
        row++;
      }
    }
  }

  private int insert(final List<PatchLineComment> in, int row) {
    for (Iterator<PatchLineComment> ci = in.iterator(); ci.hasNext();) {
      final PatchLineComment c = ci.next();
      table.insertRow(row);
      table.getCellFormatter().setStyleName(row, 0, S_ICON_CELL);
      bindComment(row, PC, c, !ci.hasNext());
      row++;
    }
    return row;
  }

  private void appendFileHeader(final SafeHtmlBuilder m, final String line) {
    openLine(m);
    padLineNumber(m);
    padLineNumber(m);

    m.openTd();
    m.addStyleName("DiffText");
    m.addStyleName("DiffText-FILE_HEADER");
    m.append(line);
    m.closeTd();
    closeLine(m);
  }

  private void appendHunkHeader(final SafeHtmlBuilder m, final Hunk hunk) {
    openLine(m);
    padLineNumber(m);
    padLineNumber(m);

    m.openTd();
    m.addStyleName("DiffText");
    m.addStyleName("DiffText-HUNK_HEADER");
    m.append("@@ -");
    appendRange(m, hunk.getCurA() + 1, hunk.getEndA() - hunk.getCurA());
    m.append(" +");
    appendRange(m, hunk.getCurB() + 1, hunk.getEndB() - hunk.getCurB());
    m.append(" @@");
    m.closeTd();

    closeLine(m);
  }

  private void appendRange(final SafeHtmlBuilder m, final int begin,
      final int cnt) {
    switch (cnt) {
      case 0:
        m.append(begin - 1);
        m.append(",0");
        break;

      case 1:
        m.append(begin);
        break;

      default:
        m.append(begin);
        m.append(',');
        m.append(cnt);
        break;
    }
  }

  private void appendLineText(final SafeHtmlBuilder m,
      final PatchLine.Type type, final SparseFileContent src, final int i) {
    final int len = PatchUtil.DEFAULT_LINE_LENGTH;
    final String text = src.get(i);
    m.openTd();
    m.addStyleName("DiffText");
    m.addStyleName("DiffText-" + type.name());
    switch (type) {
      case CONTEXT:
        if ("".equals(text)) {
          m.nbsp();
        } else {
          m.append(" ");
          m.append(PatchUtil.lineToSafeHtml(text, len, false));
        }
        break;
      case DELETE:
        m.append("-");
        m.append(PatchUtil.lineToSafeHtml(text, len, false));
        break;
      case INSERT:
        m.append("+");
        m.append(PatchUtil.lineToSafeHtml(text, len, true));
        break;
    }
    m.closeTd();
  }

  private void appendNoLF(final SafeHtmlBuilder m) {
    openLine(m);
    padLineNumber(m);
    padLineNumber(m);
    m.openTd();
    m.addStyleName("DiffText");
    m.addStyleName("DiffText-NO_LF");
    m.append("\\ No newline at end of file");
    m.closeTd();
    closeLine(m);
  }

  private void openLine(final SafeHtmlBuilder m) {
    m.openTr();
    m.setAttribute("valign", "top");
    m.openTd();
    m.setStyleName(S_ICON_CELL);
    m.nbsp();
    m.closeTd();
  }

  private void closeLine(final SafeHtmlBuilder m) {
    m.closeTr();
  }

  private void padLineNumber(final SafeHtmlBuilder m) {
    m.openTd();
    m.setStyleName("LineNumber");
    m.nbsp();
    m.closeTd();
  }

  private void appendLineNumber(final SafeHtmlBuilder m, final int idx) {
    m.openTd();
    m.setStyleName("LineNumber");
    m.append(idx + 1);
    m.closeTd();
  }
}
