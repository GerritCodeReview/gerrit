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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.SideBySideLine;
import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.DomUtil;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;

public class SideBySideTable extends FancyFlexTable<Object> {
  private AccountInfoCache accountCache = AccountInfoCache.empty();
  private int fileCnt;
  private int maxLineNumber;

  public SideBySideTable() {
    table.setStyleName("gerrit-SideBySideTable");
  }

  @Override
  protected Object getRowItemKey(final Object item) {
    return null;
  }

  @Override
  protected void onOpenItem(final Object item) {
    if (item instanceof PatchLineComment) {
      final ComplexDisclosurePanel p =
          (ComplexDisclosurePanel) table.getWidget(getCurrentRow(), 1);
      p.setOpen(!p.isOpen());
    }
  }

  public void setAccountInfoCache(final AccountInfoCache aic) {
    assert aic != null;
    accountCache = aic;
  }

  public void display(final SideBySidePatchDetail detail) {
    accountCache = detail.getAccounts();
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

      setRowItem(row, pLine);

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
        nc.append((int) s.getLineNumber());
        nc.append("</td>");

        nc.append("<td class=\"DiffText DiffText-");
        nc.append(s.getType().name());
        nc.append("\">");
        if (!"".equals(s.getText()))
          nc.append(PatchUtil.lineToHTML(s.getText()));
        else
          nc.append("&nbsp;");
        nc.append("</td>");
      } else {
        nc.append("<td class=\"LineNumber\">&nbsp;</td>");
        nc.append("<td class=\"NoLineDiffText\">&nbsp;</td>");
      }
    }

    nc.append("</tr>");
  }

  private void bindComment(final int row, final int col,
      final PatchLineComment line, final boolean isLast) {
    final long AGE = 7 * 24 * 60 * 60 * 1000L;
    final Timestamp aged = new Timestamp(System.currentTimeMillis() - AGE);

    final LineCommentPanel mp = new LineCommentPanel(line);
    String panelHeader;
    final ComplexDisclosurePanel panel;

    if (line.getAuthor() != null) {
      panelHeader = FormatUtil.nameEmail(accountCache.get(line.getAuthor()));
    } else {
      panelHeader = Util.C.messageNoAuthor();
    }

    if (isLast) {
      mp.isRecent = true;
    } else {
      // TODO Instead of opening messages by strict age, do it by "unread"?
      mp.isRecent = line.getWrittenOn().after(aged);
    }

    panel = new ComplexDisclosurePanel(panelHeader, mp.isRecent);
    panel.getHeader().add(
        new InlineLabel(Util.M.messageWrittenOn(FormatUtil.mediumFormat(line
            .getWrittenOn()))));
    if (line.getStatus() == PatchLineComment.Status.DRAFT) {
      final InlineLabel d = new InlineLabel(PatchUtil.C.draft());
      d.setStyleName("CommentIsDraftFlag");
      panel.getHeader().add(d);
    }
    panel.setContent(mp);
    table.setWidget(row, col, panel);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setStyleName(row, col, "Comment");
    setRowItem(row, line);
  }
}
