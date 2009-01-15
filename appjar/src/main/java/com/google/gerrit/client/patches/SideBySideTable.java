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
import com.google.gerrit.client.ui.DomUtil;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;

import java.util.Iterator;
import java.util.List;

public class SideBySideTable extends AbstractPatchContentTable {
  private int fileCnt;
  private int maxLineNumber;

  @Override
  protected void onCellDoubleClick(final int row, final int column) {
    if (column > 1 && getRowItem(row) instanceof SideBySideLineList) {
      final SideBySideLineList pl = (SideBySideLineList) getRowItem(row);
      final short file = (short) ((column - 1) / 2);
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
    int row = 0;
    for (final PatchLineComment c : drafts) {
      while (row < table.getRowCount()) {
        if (getRowItem(row) instanceof SideBySideLineList) {
          final SideBySideLineList pl = (SideBySideLineList) getRowItem(row);
          final SideBySideLine line = pl.lines.get(c.getSide());
          if (line != null && line.getLineNumber() >= c.getLine()) {
            break;
          }
        }
        row++;
      }
      table.insertRow(row + 1);
      bindComment(row + 1, 1 + c.getSide() * 2 + 1, c, true);
    }
  }

  public void display(final SideBySidePatchDetail detail) {
    setAccountInfoCache(detail.getAccounts());
    setPatchKey(detail.getPatch().getKey());
    fileCnt = detail.getFileCount();
    maxLineNumber = detail.getLineCount();

    List<SideBySideLine> prior = null;

    // Generate the table in HTML, because its quicker than by DOM.
    // This pass does not include the line comments; they need full
    // GWT widgets and are relatively infrequent. We do them later.
    //
    final StringBuilder nc = new StringBuilder();
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
    resetHtml(nc.toString());

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

      int nextComment = row;
      int lastComment = row;
      for (int fileId = 0; fileId < fileCnt; fileId++) {
        final SideBySideLine s = pLine.get(fileId);
        if (s == null) {
          continue;
        }

        final List<PatchLineComment> comments = s.getComments();
        if (comments == null) {
          continue;
        }

        for (Iterator<PatchLineComment> ci = comments.iterator(); ci.hasNext();) {
          final PatchLineComment c = ci.next();
          if (nextComment == lastComment) {
            lastComment++;
            table.insertRow(lastComment);
            table.getCellFormatter().setStyleName(lastComment, 0, S_ICON_CELL);
          }
          nextComment++;
          table.setWidget(nextComment, 1 + 2 * fileId, null);
          bindComment(nextComment, 1 + 2 * fileId + 1, c, !ci.hasNext());
        }
      }

      row = lastComment + 1;
    }
    final int skipCnt = skipped(prior, null);
    if (skipCnt > 0) {
      bindSkipLine(row, skipCnt);
      row++;
    }
  }

  private void appendHeader(final StringBuilder nc) {
    final String width = (100 / fileCnt) + "%";
    nc.append("<tr>");
    nc.append("<td class=\"FileColumnHeader " + S_ICON_CELL + "\">&nbsp;</td>");

    if (fileCnt == 2) {
      nc.append("<td class=\"FileColumnHeader LineNumber\">&nbsp;</td>");
      nc.append("<td class=\"FileColumnHeader\" width=\"");
      nc.append(width);
      nc.append("\">");
      nc.append(DomUtil.escape(PatchUtil.C.patchHeaderOld()));
      nc.append("</td>");
    } else {
      for (int fileId = 0; fileId < fileCnt - 1; fileId++) {
        nc.append("<td class=\"FileColumnHeader LineNumber\">&nbsp;</td>");
        nc.append("<td class=\"FileColumnHeader\" width=\"");
        nc.append(width);
        nc.append("\">");
        nc.append(DomUtil.escape(PatchUtil.M.patchHeaderAncestor(fileId + 1)));
        nc.append("</td>");
      }
    }

    nc.append("<td class=\"FileColumnHeader LineNumber\">&nbsp;</td>");
    nc.append("<td class=\"FileColumnHeader\" width=\"");
    nc.append(width);
    nc.append("\">");
    nc.append(DomUtil.escape(PatchUtil.C.patchHeaderNew()));
    nc.append("</td>");

    nc.append("</tr>");
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

  private void appendSkipLine(final StringBuilder body) {
    body.append("<tr>");
    body.append("<td class=\"" + S_ICON_CELL + "\">&nbsp;</td>");
    body.append("<td class=\"SkipLine\" colspan=\"");
    body.append(fileCnt * 2);
    body.append("\">");
    body.append("</td>");
    body.append("</tr>");
  }

  private void bindSkipLine(int row, final int skipCnt) {
    final FlowPanel skipPanel = new FlowPanel();
    skipPanel.add(new InlineLabel(PatchUtil.M.patchSkipRegion(skipCnt)));
    table.setWidget(row, 1, skipPanel);
  }

  private void appendFileLine(final StringBuilder nc,
      final List<SideBySideLine> line) {
    nc.append("<tr>");
    nc.append("<td class=\"" + S_ICON_CELL + "\">&nbsp;</td>");

    for (int fileId = 0; fileId < fileCnt; fileId++) {
      final SideBySideLine s = line.get(fileId);
      if (s != null) {
        nc.append("<td class=\"LineNumber\">");
        nc.append(s.getLineNumber());
        nc.append("</td>");

        nc.append("<td class=\"FileLine FileLine-");
        nc.append(s.getType().name());
        nc.append("\">");
        if (!"".equals(s.getText()))
          nc.append(PatchUtil.lineToHTML(s.getText()));
        else
          nc.append("&nbsp;");
        nc.append("</td>");
      } else {
        nc.append("<td class=\"LineNumber\">&nbsp;</td>");
        nc.append("<td class=\"FileLine FileLineNone\">&nbsp;</td>");
      }
    }

    nc.append("</tr>");
  }

  private static class SideBySideLineList {
    final List<SideBySideLine> lines;

    SideBySideLineList(final List<SideBySideLine> a) {
      lines = a;
    }
  }
}
