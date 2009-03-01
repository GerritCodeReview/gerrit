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

import com.google.gerrit.client.data.PatchLine;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.Iterator;
import java.util.List;

public class UnifiedDiffTable extends AbstractPatchContentTable {
  private static final int PC = 3;

  @Override
  protected void onCellDoubleClick(final int row, final int column) {
    if (getRowItem(row) instanceof PatchLine) {
      final PatchLine pl = (PatchLine) getRowItem(row);
      switch (pl.getType()) {
        case PRE_IMAGE:
        case CONTEXT:
          createCommentEditor(row + 1, PC, pl.getOldLineNumber(), (short) 0);
          break;
        case POST_IMAGE:
          createCommentEditor(row + 1, PC, pl.getNewLineNumber(), (short) 1);
          break;
      }
    }
  }

  @Override
  protected void onOpenItem(final Object item) {
    if (item instanceof PatchLine) {
      final PatchLine pl = (PatchLine) item;
      final int row = getCurrentRow();
      switch (pl.getType()) {
        case PRE_IMAGE:
        case CONTEXT:
          createCommentEditor(row + 1, PC, pl.getOldLineNumber(), (short) 0);
          break;
        case POST_IMAGE:
          createCommentEditor(row + 1, PC, pl.getOldLineNumber(), (short) 1);
          break;
      }
      return;
    }

    super.onOpenItem(item);
  }

  @Override
  protected void bindDrafts(final List<PatchLineComment> drafts) {
    int row = 0;
    for (final PatchLineComment c : drafts) {
      while (row < table.getRowCount()) {
        if (getRowItem(row) instanceof PatchLine) {
          final PatchLine pl = (PatchLine) getRowItem(row);
          if (pl.getOldLineNumber() >= c.getLine()) {
            break;
          }
        }
        row++;
      }
      table.insertRow(row + 1);
      table.getCellFormatter().setStyleName(row + 1, 0, S_ICON_CELL);
      bindComment(row + 1, PC, c, true);
    }
  }

  public void display(final List<PatchLine> list) {
    initVersions(2);

    final SafeHtmlBuilder nc = new SafeHtmlBuilder();
    for (final PatchLine pLine : list) {
      appendLine(nc, pLine);
    }
    resetHtml(nc);

    int row = 0;
    for (final PatchLine pLine : list) {
      setRowItem(row, pLine);
      row++;

      final List<PatchLineComment> comments = pLine.getComments();
      if (comments != null) {
        for (final Iterator<PatchLineComment> ci = comments.iterator(); ci
            .hasNext();) {
          final PatchLineComment c = ci.next();
          table.insertRow(row);
          table.getCellFormatter().setStyleName(row, 0, S_ICON_CELL);
          bindComment(row, PC, c, !ci.hasNext());
          row++;
        }
      }
    }
  }

  private void appendLine(final SafeHtmlBuilder m, final PatchLine line) {
    m.openTr();

    m.openTd();
    m.setStyleName(S_ICON_CELL);
    m.nbsp();
    m.closeTd();

    switch (line.getType()) {
      case FILE_HEADER:
      case HUNK_HEADER:
        m.openTd();
        m.setStyleName("LineNumber");
        m.nbsp();
        m.closeTd();

        m.openTd();
        m.setStyleName("LineNumber");
        m.nbsp();
        m.closeTd();
        break;

      default:
        m.openTd();
        m.setStyleName("LineNumber");
        if (line.getOldLineNumber() != 0
            && (line.getType() == PatchLine.Type.CONTEXT || line.getType() == PatchLine.Type.PRE_IMAGE)) {
          m.append(line.getOldLineNumber());
        } else {
          m.nbsp();
        }
        m.closeTd();

        m.openTd();
        m.setStyleName("LineNumber");
        if (line.getNewLineNumber() != 0
            && (line.getType() == PatchLine.Type.CONTEXT || line.getType() == PatchLine.Type.POST_IMAGE)) {
          m.append(line.getNewLineNumber());
        } else {
          m.nbsp();
        }
        m.closeTd();
        break;
    }

    m.openTd();
    m.addStyleName("DiffText");
    m.addStyleName("DiffText-" + line.getType().name());
    if (!"".equals(line.getText())) {
      boolean showWhitespaceErrors = false;
      switch (line.getType()) {
        case POST_IMAGE:
          // Only show whitespace errors if the error was introduced.
          //
          showWhitespaceErrors = true;
          break;
      }
      m.append(PatchUtil.lineToSafeHtml(line.getText(), 0, showWhitespaceErrors));
    } else {
      m.nbsp();
    }
    m.closeTd();

    m.closeTr();
  }
}
