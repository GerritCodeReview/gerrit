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
import com.google.gerrit.client.data.PatchLine;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;

public class UnifiedDiffTable extends FancyFlexTable<Object> {
  private AccountInfoCache accountCache = AccountInfoCache.empty();

  public UnifiedDiffTable() {
    table.setStyleName("gerrit-UnifiedDiffTable");
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

  public void display(final List<PatchLine> list) {
    final int sz = list != null ? list.size() : 0;
    int dataRows = table.getRowCount() - 1;
    while (sz < dataRows) {
      table.removeRow(dataRows);
      dataRows--;
    }

    int row = 0;
    for (final PatchLine pLine : list) {
      if (dataRows <= row) {
        table.insertRow(++dataRows);
        applyDataRowStyle(row);
      }
      populate(row++, pLine);

      final List<PatchLineComment> comments = pLine.getComments();
      if (comments != null) {
        for (final Iterator<PatchLineComment> ci = comments.iterator(); ci
            .hasNext();) {
          final PatchLineComment c = ci.next();
          if (dataRows <= row) {
            table.insertRow(++dataRows);
            applyDataRowStyle(row);
          }
          populate(row++, c, !ci.hasNext());
        }
      }
    }
  }

  private void populate(final int row, final PatchLine line) {
    final CellFormatter fmt = table.getCellFormatter();
    table.setWidget(row, C_ARROW, null);
    table.setText(row, 1, line.getText());
    fmt.setStyleName(row, 1, "DiffText-" + line.getType().name());
    fmt.addStyleName(row, 1, "DiffText");
    setRowItem(row, line);
  }

  private void populate(final int row, final PatchLineComment line,
      final boolean isLast) {
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
    table.setWidget(row, C_ARROW, null);
    table.setWidget(row, 1, panel);

    final CellFormatter fmt = table.getCellFormatter();
    fmt.setStyleName(row, 1, "Comment");
    setRowItem(row, line);
  }
}
