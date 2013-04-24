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
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.EditList.Hunk;
import com.google.gerrit.prettify.common.SparseHtmlFile;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.UIObject;
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

  protected boolean isFileCommentBorderRowExist;
  // Cursors.
  protected int rowOfTableHeaderB;
  protected int borderRowOfFileComment;

  @Override
  protected void onCellDoubleClick(final int row, final int column) {
    if (column > C_ARROW && getRowItem(row) instanceof PatchLine) {
      final PatchLine pl = (PatchLine) getRowItem(row);
      switch (pl.getType()) {
        case DELETE:
        case CONTEXT:
          createCommentEditor(row + 1, PC, pl.getLineA(), (short) 0);
          break;
        case INSERT:
          createCommentEditor(row + 1, PC, pl.getLineB(), (short) 1);
          break;
        case REPLACE:
          break;
      }
    }
  }

  @Override
  protected void updateCursor(final PatchLineComment newComment) {
    if (newComment.getLine() == R_HEAD) {
      final PatchSet.Id psId =
          newComment.getKey().getParentKey().getParentKey();
      switch (newComment.getSide()) {
        case FILE_SIDE_A:
          if (idSideA == null && idSideB.equals(psId)) {
            rowOfTableHeaderB++;
            borderRowOfFileComment++;
            return;
          }
          break;
        case FILE_SIDE_B:
          if (idSideA != null && idSideA.equals(psId)) {
            rowOfTableHeaderB++;
            borderRowOfFileComment++;
            return;
          }
          if (idSideB.equals(psId)) {
            borderRowOfFileComment++;
            return;
          }
      }
    }
  }

  @Override
  protected void onCellSingleClick(int row, int column) {
    super.onCellSingleClick(row, column);
    if (column == 1 || column == 2) {
      if (!"".equals(table.getText(row, column))) {
        onCellDoubleClick(row, column);
      }
    }
  }

  @Override
  protected void destroyCommentRow(final int row) {
    super.destroyCommentRow(row);
    if (this.rowOfTableHeaderB + 1 == row && row + 1 == borderRowOfFileComment) {
      table.removeRow(row);
      isFileCommentBorderRowExist = false;
    }
  }

  @Override
  public void remove(CommentEditorPanel panel) {
    super.remove(panel);
    if (panel.getComment().getLine() == AbstractPatchContentTable.R_HEAD) {
      final PatchSet.Id psId =
          panel.getComment().getKey().getParentKey().getParentKey();
      switch (panel.getComment().getSide()) {
        case FILE_SIDE_A:
          if (idSideA == null && idSideB.equals(psId)) {
            rowOfTableHeaderB--;
            borderRowOfFileComment--;
            return;
          }
          break;
        case FILE_SIDE_B:
          if (idSideA != null && idSideA.equals(psId)) {
            rowOfTableHeaderB--;
            borderRowOfFileComment--;
            return;
          }
          if (idSideB.equals(psId)) {
            borderRowOfFileComment--;
            return;
          }
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
      case REPLACE:
        break;
    }
  }

  private void appendImgTag(SafeHtmlBuilder nc, String url) {
    nc.openElement("img");
    nc.setAttribute("src", url);
    nc.closeElement("img");
  }

  protected void createFileCommentEditorOnSideA() {
    createCommentEditor(R_HEAD + 1, PC, R_HEAD, FILE_SIDE_A);
    return;
  }

  protected void createFileCommentEditorOnSideB() {
    createCommentEditor(rowOfTableHeaderB + 1, PC, R_HEAD, FILE_SIDE_B);
    createFileCommentBorderRow();
  }

  private void populateTableHeader(final PatchScript script,
      final PatchSetDetail detail) {
    initHeaders(script, detail);
    table.setWidget(R_HEAD, PC, headerSideA);
    table.setWidget(rowOfTableHeaderB, PC, headerSideB);
    table.getFlexCellFormatter().addStyleName(R_HEAD, PC,
        Gerrit.RESOURCES.css().unifiedTableHeader());
    table.getFlexCellFormatter().addStyleName(rowOfTableHeaderB, PC,
        Gerrit.RESOURCES.css().unifiedTableHeader());

    // Add icons to lineNumber column header
    if (headerSideA.isFileOrCommitMessage()) {
      table.setWidget(R_HEAD, 1, iconA);
    }
    if (headerSideB.isFileOrCommitMessage()) {
      table.setWidget(rowOfTableHeaderB, 2, iconB);
    }
  }

  private void allocateTableHeader(SafeHtmlBuilder nc) {
    rowOfTableHeaderB = 1;
    borderRowOfFileComment = 2;
    for (int i = R_HEAD; i < borderRowOfFileComment; i++) {
      openTableHeaderLine(nc);
      padLineNumberOnTableHeaderForSideA(nc);
      padLineNumberOnTableHeaderForSideB(nc);
      nc.openTd();
      nc.setStyleName(Gerrit.RESOURCES.css().fileLine());
      nc.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
      nc.closeTd();
      closeLine(nc);
    }
  }

  @Override
  protected void render(final PatchScript script, final PatchSetDetail detail) {
    final SafeHtmlBuilder nc = new SafeHtmlBuilder();
    allocateTableHeader(nc);

    // Display the patch header
    for (final String line : script.getPatchHeader()) {
      appendFileHeader(nc, line);
    }
    final ArrayList<PatchLine> lines = new ArrayList<PatchLine>();
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

    if (!isDisplayBinary) {
      final SparseHtmlFile a = getSparseHtmlFileA(script);
      final SparseHtmlFile b = getSparseHtmlFileB(script);
      if (hasDifferences(script)) {
        final boolean syntaxHighlighting =
            script.getDiffPrefs().isSyntaxHighlighting();
        for (final EditList.Hunk hunk : script.getHunks()) {
          appendHunkHeader(nc, hunk);
          while (hunk.next()) {
            if (hunk.isContextLine()) {
              openLine(nc);
              appendLineNumberForSideA(nc, hunk.getCurA());
              appendLineNumberForSideB(nc, hunk.getCurB());
              appendLineText(nc, false, CONTEXT, a, hunk.getCurA());
              closeLine(nc);
              hunk.incBoth();
              lines.add(new PatchLine(CONTEXT, hunk.getCurA(), hunk.getCurB()));

            } else if (hunk.isDeletedA()) {
              openLine(nc);
              appendLineNumberForSideA(nc, hunk.getCurA());
              padLineNumberForSideB(nc);
              appendLineText(nc, syntaxHighlighting, DELETE, a, hunk.getCurA());
              closeLine(nc);
              hunk.incA();
              lines.add(new PatchLine(DELETE, hunk.getCurA(), -1));
              if (a.size() == hunk.getCurA()
                  && script.getA().isMissingNewlineAtEnd()) {
                appendNoLF(nc);
              }

            } else if (hunk.isInsertedB()) {
              openLine(nc);
              padLineNumberForSideA(nc);
              appendLineNumberForSideB(nc, hunk.getCurB());
              appendLineText(nc, syntaxHighlighting, INSERT, b, hunk.getCurB());
              closeLine(nc);
              hunk.incB();
              lines.add(new PatchLine(INSERT, -1, hunk.getCurB()));
              if (b.size() == hunk.getCurB()
                  && script.getB().isMissingNewlineAtEnd()) {
                appendNoLF(nc);
              }
            }
          }
        }
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
      final List<PatchLineComment> fora;
      final List<PatchLineComment> forb;
      if (row == R_HEAD) {
        fora = cd.getForA(R_HEAD);
        forb = cd.getForB(R_HEAD);
        row++;

        if (!fora.isEmpty()) {
          row = insert(fora, row, expandComments);
        }
        rowOfTableHeaderB = row;
        borderRowOfFileComment = row + 1;
        if (!forb.isEmpty()) {
          row++;// Skip the Header of sideB.
          row = insert(forb, row, expandComments);
          borderRowOfFileComment = row;
          createFileCommentBorderRow();
        }
      } else if (getRowItem(row) instanceof PatchLine) {
        final PatchLine pLine = (PatchLine) getRowItem(row);
        fora = cd.getForA(pLine.getLineA());
        forb = cd.getForB(pLine.getLineB());
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
        continue;
      }
    }
  }

  private void defaultStyle(final int row, final CellFormatter fmt) {
    fmt.addStyleName(row, PC - 2, Gerrit.RESOURCES.css().lineNumber());
    fmt.addStyleName(row, PC - 2, Gerrit.RESOURCES.css().rightBorder());
    fmt.addStyleName(row, PC - 1, Gerrit.RESOURCES.css().lineNumber());
    fmt.addStyleName(row, PC, Gerrit.RESOURCES.css().diffText());
  }

  @Override
  protected void insertRow(final int row) {
    super.insertRow(row);
    final CellFormatter fmt = table.getCellFormatter();
    defaultStyle(row, fmt);
  }

  @Override
  protected PatchScreen.Type getPatchScreenType() {
    return PatchScreen.Type.UNIFIED;
  }

  private int insert(final List<PatchLineComment> in, int row, boolean expandComment) {
    for (Iterator<PatchLineComment> ci = in.iterator(); ci.hasNext();) {
      final PatchLineComment c = ci.next();
      if (c.getLine() == R_HEAD) {
        insertFileCommentRow(row);
      } else {
        insertRow(row);
      }
      bindComment(row, PC, c, !ci.hasNext(), expandComment);
      row++;
    }
    return row;
  }

  @Override
  protected void insertFileCommentRow(final int row) {
    table.insertRow(row);
    final CellFormatter fmt = table.getCellFormatter();

    fmt.addStyleName(row, C_ARROW, //
        Gerrit.RESOURCES.css().iconCellOfFileCommentRow());
    defaultStyle(row, fmt);

    fmt.addStyleName(row, C_ARROW, //
        Gerrit.RESOURCES.css().cellsNextToFileComment());
    fmt.addStyleName(row, PC - 2, //
        Gerrit.RESOURCES.css().cellsNextToFileComment());
    fmt.addStyleName(row, PC - 1, //
        Gerrit.RESOURCES.css().cellsNextToFileComment());
  }

  private void createFileCommentBorderRow() {
    if (!isFileCommentBorderRowExist) {
      isFileCommentBorderRowExist = true;
      table.insertRow(borderRowOfFileComment);
      final CellFormatter fmt = table.getCellFormatter();
      fmt.addStyleName(borderRowOfFileComment, C_ARROW, //
          Gerrit.RESOURCES.css().iconCellOfFileCommentRow());
      defaultStyle(borderRowOfFileComment, fmt);

      final Element iconCell =
          fmt.getElement(borderRowOfFileComment, C_ARROW);
      UIObject.setStyleName(DOM.getParent(iconCell), //
          Gerrit.RESOURCES.css().fileCommentBorder(), true);
    }
  }

  private void appendFileHeader(final SafeHtmlBuilder m, final String line) {
    openLine(m);
    padLineNumberForSideA(m);
    padLineNumberForSideB(m);

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().fileLine());
    m.addStyleName(Gerrit.RESOURCES.css().diffText());
    m.addStyleName(Gerrit.RESOURCES.css().diffTextFileHeader());
    m.append(line);
    m.closeTd();
    closeLine(m);
  }

  private void appendHunkHeader(final SafeHtmlBuilder m, final Hunk hunk) {
    openLine(m);
    padLineNumberForSideA(m);
    padLineNumberForSideB(m);

    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().fileLine());
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
    m.setStyleName(Gerrit.RESOURCES.css().fileLine());
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
      case REPLACE:
        break;
    }
    m.closeTd();
  }

  private void appendNoLF(final SafeHtmlBuilder m) {
    openLine(m);
    padLineNumberForSideA(m);
    padLineNumberForSideB(m);
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

  private void openTableHeaderLine(final SafeHtmlBuilder m) {
    m.openTr();
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().iconCell());
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.closeTd();
  }

  private void closeLine(final SafeHtmlBuilder m) {
    m.closeTr();
  }

  private void padLineNumberForSideB(final SafeHtmlBuilder m) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.closeTd();
  }

  private void padLineNumberForSideA(final SafeHtmlBuilder m) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.addStyleName(Gerrit.RESOURCES.css().rightBorder());
    m.closeTd();
  }

  private void appendLineNumberForSideB(final SafeHtmlBuilder m, final int idx) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.append(SafeHtml.asis("<a href=\"javascript:void(0)\">"+ (idx + 1) + "</a>"));
    m.closeTd();
  }

  private void appendLineNumberForSideA(final SafeHtmlBuilder m, final int idx) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.addStyleName(Gerrit.RESOURCES.css().rightBorder());
    m.append(SafeHtml.asis("<a href=\"javascript:void(0)\">"+ (idx + 1) + "</a>"));
    m.closeTd();
  }

  private void padLineNumberOnTableHeaderForSideB(final SafeHtmlBuilder m) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.closeTd();
  }

  private void padLineNumberOnTableHeaderForSideA(final SafeHtmlBuilder m) {
    m.openTd();
    m.setStyleName(Gerrit.RESOURCES.css().lineNumber());
    m.addStyleName(Gerrit.RESOURCES.css().fileColumnHeader());
    m.addStyleName(Gerrit.RESOURCES.css().rightBorder());
    m.closeTd();
  }
}
