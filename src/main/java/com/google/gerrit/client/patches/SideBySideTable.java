// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.data.SideBySideLine;
import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.Iterator;
import java.util.List;

public class SideBySideTable extends AbstractPatchContentTable {
  private int fileCnt;
  private int maxLineNumber;

  protected int getFileCount() {
    return fileCnt;
  }

  protected String getFileTitle(int file) {
    return table.getText(0, 1 + file * 2 + 1);
  }

  @Override
  protected void onCellDoubleClick(final int row, int column) {
    if (column > 0 && getRowItem(row) instanceof SideBySideLineList) {
      final SideBySideLineList pl = (SideBySideLineList) getRowItem(row);
      final short file = (short) ((column - 1) / 2);
      if (column < (1 + file * 2 + 1)) {
        column++;
      }

      final SideBySideLine line = pl.lines.get(file);
      switch (line.getType()) {
        case DELETE:
        case EQUAL:
        case INSERT: {
          createCommentEditor(row + 1, column, line.getLineNumber(), file);
          break;
        }
      }
    }
  }

  @Override
  protected void onOpenItem(final Object item) {
    if (item instanceof SideBySideLineList) {
      final SideBySideLineList pl = (SideBySideLineList) item;
      final short file = (short) (pl.lines.size() - 1);
      final int row = getCurrentRow();
      final int column = 1 + file * 2 + 1;
      final SideBySideLine line = pl.lines.get(file);
      createCommentEditor(row + 1, column, line.getLineNumber(), file);
      return;
    }

    super.onOpenItem(item);
  }

  @Override
  protected void bindDrafts(final List<PatchLineComment> drafts) {
    int[] rows = new int[fileCnt];
    for (final PatchLineComment c : drafts) {
      final int side = fileFor(c);
      if (side < 0 || fileCnt <= side) {
        // We shouldn't have been given this draft; it doesn't display
        // in our current UI layout.
        //
        continue;
      }
      int row = rows[side];
      while (row < table.getRowCount()) {
        if (getRowItem(row) instanceof SideBySideLineList) {
          final SideBySideLineList pl = (SideBySideLineList) getRowItem(row);
          final SideBySideLine line = pl.lines.get(side);
          if (line != null && line.getLineNumber() >= c.getLine()) {
            break;
          }
        }
        row++;
      }
      row++;
      boolean needInsert = true;
      for (int cell = 0; cell < table.getCellCount(row); cell++) {
        final Widget w = table.getWidget(row, cell);
        if (w instanceof CommentEditorPanel
            || w instanceof ComplexDisclosurePanel) {
          needInsert = false;
          break;
        }
      }
      if (needInsert) {
        table.insertRow(row);
        table.getCellFormatter().setStyleName(row, 0, S_ICON_CELL);
      }
      bindComment(row, 1 + side * 2 + 1, c, true);
      rows[side] = row + 1;
    }
  }

  public void display(final SideBySidePatchDetail detail) {
    setPatchKey(detail.getPatch().getKey());
    initVersions(detail.getFileCount());
    setAccountInfoCache(detail.getAccounts());
    fileCnt = detail.getFileCount();
    maxLineNumber = detail.getLineCount();

    List<SideBySideLine> prior = null;

    // Generate the table in HTML, because its quicker than by DOM.
    // This pass does not include the line comments; they need full
    // GWT widgets and are relatively infrequent. We do them later.
    //
    final SafeHtmlBuilder nc = new SafeHtmlBuilder();
    appendHeader(nc);
    for (final List<SideBySideLine> pLine : detail.getLines()) {
      if (skipped(prior, pLine) > 0) {
        appendSkipLine(nc);
      }
      prior = pLine;
      appendFileLine(nc, pLine);
    }
    if (skipped(prior, null) > 0) {
      appendSkipLine(nc);
    }
    resetHtml(nc);

    // Insert the comment widgets now that the table DOM has been
    // parsed out of the HTML by the browser. We also bind each
    // of the row item objects.
    //
    int row = 1;
    prior = null;
    for (final List<SideBySideLine> pLine : detail.getLines()) {
      final int skipCnt = skipped(prior, pLine);
      if (skipCnt > 0) {
        bindSkipLine(row, skipCnt);
        row++;
      }
      prior = pLine;

      setRowItem(row, new SideBySideLineList(pLine));

      final int lineRow = row;
      for (int fileId = 0; fileId < fileCnt; fileId++) {
        final SideBySideLine s = pLine.get(fileId);
        if (s == null) {
          continue;
        }

        final List<PatchLineComment> comments = s.getComments();
        if (comments == null) {
          continue;
        }

        int commentRow = lineRow + 1;
        for (Iterator<PatchLineComment> ci = comments.iterator(); ci.hasNext();) {
          final PatchLineComment c = ci.next();
          boolean needInsert = true;
          for (int cell = 0; cell < table.getCellCount(commentRow); cell++) {
            final Widget w = table.getWidget(commentRow, cell);
            if (w instanceof CommentEditorPanel
                || w instanceof ComplexDisclosurePanel) {
              needInsert = false;
              break;
            }
          }
          if (needInsert) {
            table.insertRow(commentRow);
            table.getCellFormatter().setStyleName(commentRow, 0, S_ICON_CELL);
          }
          table.setWidget(commentRow, 1 + 2 * fileId, null);
          bindComment(commentRow, 1 + 2 * fileId + 1, c, !ci.hasNext());
          commentRow++;
        }
        row = Math.max(row, commentRow - 1);
      }
      row++;
    }
    final int skipCnt = skipped(prior, null);
    if (skipCnt > 0) {
      bindSkipLine(row, skipCnt);
      row++;
    }
  }

  private void appendHeader(final SafeHtmlBuilder m) {
    final String width = (100 / fileCnt) + "%";
    m.openTr();

    m.openTd();
    m.addStyleName(S_ICON_CELL);
    m.addStyleName("FileColumnHeader");
    m.nbsp();
    m.closeTd();

    if (fileCnt == 2) {
      m.openTd();
      m.addStyleName("FileColumnHeader");
      m.addStyleName("LineNumber");
      m.nbsp();
      m.closeTd();

      m.openTd();
      m.setStyleName("FileColumnHeader");
      m.setAttribute("width", width);
      m.append(PatchUtil.C.patchHeaderOld());
      m.closeTd();
    } else {
      for (int fileId = 0; fileId < fileCnt - 1; fileId++) {
        m.openTd();
        m.addStyleName("FileColumnHeader");
        m.addStyleName("LineNumber");
        m.nbsp();
        m.closeTd();

        m.openTd();
        m.setStyleName("FileColumnHeader");
        m.setAttribute("width", width);
        m.append(PatchUtil.M.patchHeaderAncestor(fileId + 1));
        m.closeTd();
      }
    }

    m.openTd();
    m.addStyleName("FileColumnHeader");
    m.addStyleName("LineNumber");
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName("FileColumnHeader");
    m.setAttribute("width", width);
    m.append(PatchUtil.C.patchHeaderNew());
    m.closeTd();

    m.closeTr();
  }

  private int skipped(List<SideBySideLine> prior,
      final List<SideBySideLine> pLine) {
    int existCnt = 0;
    int gapCnt = 0;
    int lines = 0;

    if (prior != null && pLine != null) {
      for (int i = 0; i < fileCnt; i++) {
        final SideBySideLine ps = prior.get(i);
        final SideBySideLine cs = pLine.get(i);
        if (ps != null && cs != null) {
          existCnt++;
          if (ps.getLineNumber() + 1 != cs.getLineNumber()) {
            lines =
                Math.max(lines, cs.getLineNumber() - ps.getLineNumber() - 1);
            gapCnt++;
          }
        }
      }
    } else if (prior != null) {
      for (int i = 0; i < fileCnt; i++) {
        final SideBySideLine ps = prior.get(i);
        if (ps != null) {
          existCnt++;
          if (ps.getLineNumber() < maxLineNumber) {
            lines = Math.max(lines, maxLineNumber - ps.getLineNumber() - 1);
            gapCnt++;
          }
        }
      }
    } else {
      for (int i = 0; i < fileCnt; i++) {
        final SideBySideLine cs = pLine.get(i);
        if (cs != null) {
          existCnt++;
          if (1 != cs.getLineNumber()) {
            lines = Math.max(lines, cs.getLineNumber() - 1);
            gapCnt++;
          }
        }
      }
    }
    return existCnt == gapCnt ? lines : 0;
  }

  private void appendSkipLine(final SafeHtmlBuilder m) {
    m.openTr();

    m.openTd();
    m.setStyleName(S_ICON_CELL);
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName("SkipLine");
    m.setAttribute("colspan", fileCnt * 2);
    m.closeTd();
    m.closeTr();
  }

  private void bindSkipLine(int row, final int skipCnt) {
    final FlowPanel skipPanel = new FlowPanel();
    skipPanel.add(new InlineLabel(PatchUtil.M.patchSkipRegion(skipCnt)));
    table.setWidget(row, 1, skipPanel);
  }

  private void appendFileLine(final SafeHtmlBuilder m,
      final List<SideBySideLine> line) {
    m.openTr();
    m.setAttribute("valign", "top");

    m.openTd();
    m.setStyleName(S_ICON_CELL);
    m.nbsp();
    m.closeTd();

    for (int fileId = 0; fileId < fileCnt; fileId++) {
      final SideBySideLine s = line.get(fileId);
      if (s != null) {
        m.openTd();
        m.setStyleName("LineNumber");
        m.append(s.getLineNumber());
        m.closeTd();

        m.openTd();
        m.addStyleName("FileLine");
        m.addStyleName("FileLine-" + s.getType().name());
        if (!"".equals(s.getText())) {
          boolean showWhitespaceErrors = false;
          if (fileId == fileCnt - 1
              && s.getType() == SideBySideLine.Type.INSERT) {
            // Only show whitespace errors in the last column, and
            // only if the line is introduced here.
            //
            showWhitespaceErrors = true;
          }
          m.append(PatchUtil.lineToSafeHtml(s.getText(),
              PatchUtil.DEFAULT_LINE_LENGTH, showWhitespaceErrors));
        } else {
          m.nbsp();
        }
        m.closeTd();
      } else {
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
    }

    m.closeTr();
  }

  private static class SideBySideLineList {
    final List<SideBySideLine> lines;

    SideBySideLineList(final List<SideBySideLine> a) {
      lines = a;
    }
  }
}
