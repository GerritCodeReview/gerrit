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
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.sql.Timestamp;

public abstract class AbstractPatchContentTable extends FancyFlexTable<Object> {
  private static final long AGE = 7 * 24 * 60 * 60 * 1000L;
  protected AccountInfoCache accountCache = AccountInfoCache.empty();
  private final Timestamp aged =
      new Timestamp(System.currentTimeMillis() - AGE);

  protected AbstractPatchContentTable() {
    table.setStyleName("gerrit-PatchContentTable");
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

  protected void bindComment(final int row, final int col,
      final PatchLineComment line, final boolean isLast) {
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
