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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.SparseHtmlFile;
import com.google.gerrit.prettify.common.EditList.Hunk;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;

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
  protected void onCellSingleClick(int row, int column) {
    if (column == 1 || column == 2) {
      if (!"".equals(table.getText(row, column))) {
        onCellDoubleClick(row, column);
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

  private void appendImgTag(SafeHtmlBuilder nc, String url) {
    nc.openElement("img");
    nc.setAttribute("src", url);
    nc.closeElement("img");
  }

  @Override
  protected void render(final PatchScript script) {
    final SparseHtmlFile a = script.getSparseHtmlFileA();
    final SparseHtmlFile b = script.getSparseHtmlFileB();
    final SafeHtmlBuilder nc = new SafeHtmlBuilder();

    // Display the patch header
    for (final String line : script.getPatchHeader()) {
      appendFileHeader(nc, line);
    }

    if (script.getDisplayMethodA() == DisplayMethod.IMG
        || script.getDisplayMethodB() == DisplayMethod.IMG) {
      final String rawBase = GWT.getHostPageBaseURL() + "cat/";

      nc.openTr();
      nc.setAttribute("valign", "center");
      nc.setAttribute("align", "center");

      nc.openTd();
      nc.nbsp();
      nc.closeTd();

      nc.openTd();
      nc.nbsp();
      nc.closeTd();

      nc.openTd();
      nc.nbsp();
      nc.closeTd();

      nc.openTd();
      if (script.getDisplayMethodA() == DisplayMethod.IMG) {
        if (idSideA == null) {
          appendImgTag(nc, rawBase + KeyUtil.encode(patchKey.toString()) + "^1");
        } else {
          Patch.Key k = new Patch.Key(idSideA, patchKey.get());
          appendImgTag(nc, rawBase + KeyUtil.encode(k.toString()) + "^0");
        }
      }
      if (script.getDisplayMethodB() == DisplayMethod.IMG) {
        appendImgTag(nc, rawBase + KeyUtil.encode(patchKey.toString()) + "^0");
      }
      nc.closeTd();

      nc.closeTr();
    }

    final boolean syntaxHighlighting =
        script.getDiffPrefs().isSyntaxHighlighting();
    final ArrayList<PatchLine> lines = new ArrayList<PatchLine>();
    for (final EditList.Hunk hunk : script.getHunks()) {
      appendHunkHeader(nc, hunk);
      while (hunk.next()) {
        if (hunk.isContextLine()) {
          openLine(nc);
          appendLineNumber(nc, hunk.getCurA());
          appendLineNumber(nc, hunk.getCurB());
          appendLineText(nc, false, CONTEXT, a, hunk.getCurA());
          closeLine(nc);
          hunk.incBoth();
          lines.add(new PatchLine(CONTEXT, hunk.getCurA(), hunk.getCurB()));

        } else if (hunk.isDeletedA()) {
          openLine(nc);
          appendLineNumber(nc, hunk.getCurA());
          padLineNumber(nc);
          appendLineText(nc, syntaxHighlighting, DELETE, a, hunk.getCurA());
          closeLine(nc);
          hunk.incA();
          lines.add(new PatchLine(DELETE, hunk.getCurA(), 0));
          if (a.size() == hunk.getCurA()
              && script.getA().isMissingNewlineAtEnd()) {
            appendNoLF(nc);
          }

        } else if (hunk.isInsertedB()) {
          openLine(nc);
          padLineNumber(nc);
          appendLineNumber(nc, hunk.getCurB());
          appendLineText(nc, syntaxHighlighting, INSERT, b, hunk.getCurB());
          closeLine(nc);
          hunk.incB();
          lines.add(new PatchLine(INSERT, 0, hunk.getCurB()));
          if (b.size() == hunk.getCurB()
              && script.getB().isMissingNewlineAtEnd()) {
            appendNoLF(nc);
          }
        }
      }
    }
    resetHtml(nc);
    initScript(script);

    int row = script.getPatchHeader().size();
    final CellFormatter fmt = table.getCellFormatter();
    final Iterator<PatchLine> iLine = lines.iterator();
    while (iLine.hasNext()) {
      final PatchLine l = iLine.next();
      final String n;
      switch (l.getType()) {
        case CONTEXT:
          n = Gerrit.RESOURCES.css().diffTextCONTEXT();
          break;
        case DELETE:
          n = Gerrit.RESOURCES.css().diffTextDELETE();
          break;
        case INSERT:
          n = Gerrit.RESOURCES.css().diffTextINSERT();
          break;
        default:
          continue;
      }
      while (!fmt.getStyleName(row, PC).contains(n)) {
        row++;
      }
      setRowItem(row++, l);
    }
  }

  @Override
  public void display(final CommentDetail cd, boolean expandComments) {
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
          row = insert(all, row, expandComments);

        } else if (!fora.isEmpty()) {
          row = insert(fora, row, expandComments);

        } else if (!forb.isEmpty()) {
          row = insert(forb, row, expandComments);
        }
      } else {
        row++;
      }
    }
  }


  @Override
  protected void insertRow(final int row) {
    super.insertRow(row);
    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, PC - 2, Gerrit.RESOURCES.css().lineNumber());
    fmt.addStyleName(row, PC - 1, Gerrit.RESOURCES.css().lineNumber());
    fmt.addStyleName(row, PC, Gerrit.RESOURCES.css().diffText());
  }

  private int insert(final List<PatchLineComment> in, int row, boolean expandComment) {
    for (Iterator<PatchLineComment> ci = in.iterator(); ci.hasNext();) {
      final PatchLineComment c = ci.next();
      insertRow(row);
      bindComment(row, PC, c, !ci.hasNext(), expandComment);
      row++;
    }
    return row;
  }

  private void appendFileHeader(final SafeHtmlBuilder m, final String line) {
    openLine(m);
    padLineNumber(m);
    padLineNumber(m);

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().diffText());
    m.addStyleName(Gerrit.RESOURCES.css().diffTextFileHeader());
    m.append(line);
    m.closeTd();
    closeLine(m);
  }

  private void appendHunkHeader(final SafeHtmlBuilder m, final Hunk hunk) {
    openLine(m);
    padLineNumber(m);
    padLineNumber(m);

    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().diffText());
    m.addStyleName(Gerrit.RESOURCES.css().diffTextHunkHeader());
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
      boolean syntaxHighlighting, final PatchLine.Type type,
      final SparseHtmlFile src, final int i) {
    final SafeHtml text = src.getSafeHtmlLine(i);
    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().diffText());
    switch (type) {
      case CONTEXT:
        m.addStyleName(Gerrit.RESOURCES.css().diffTextCONTEXT());
        m.nbsp();
        m.append(text);
        break;
      case DELETE:
        m.addStyleName(Gerrit.RESOURCES.css().diffTextDELETE());
        if (syntaxHighlighting) {
          m.addStyleName(Gerrit.RESOURCES.css().fileLineDELETE());
        }
        m.append("-");
        m.append(text);
        break;
      case INSERT:
        m.addStyleName(Gerrit.RESOURCES.css().diffTextINSERT());
        if (syntaxHighlighting) {
          m.addStyleName(Gerrit.RESOURCES.css().fileLineINSERT());
        }
        m.append("+");
        m.append(text);
        break;
    }
    m.closeTd();
  }

  private void appendNoLF(final SafeHtmlBuilder m) {
    openLine(m);
    padLineNumber(m);
    padLineNumber(m);
    m.openTd();
    m.addStyleName(Gerrit.RESOURCES.css().diffText());
    m.addStyleName(Gerrit.RESOURCES.css().diffTextNoLF());
    m.append("\\ No newline at end of file");
    m.closeTd();
    closeLine(m);
  }

  private void openLine(final SafeHtmlBuilder m) {
    m.openTr();
    m.setAttribute("valign", "top");
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().iconCell());
    m.closeTd();
  }

  private void closeLine(final SafeHtmlBuilder m) {
    m.closeTr();
  }

  private void padLineNumber(final SafeHtmlBuilder m) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.closeTd();
  }

  private void appendLineNumber(final SafeHtmlBuilder m, final int idx) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.openAnchor();
    m.setAttribute("href", "");
    m.append(idx + 1);
    m.closeAnchor();
    m.closeTd();
  }
}
