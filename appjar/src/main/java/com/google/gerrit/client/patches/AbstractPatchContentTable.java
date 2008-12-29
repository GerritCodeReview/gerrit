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
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPatchContentTable extends FancyFlexTable<Object> {
  private static final long AGE = 7 * 24 * 60 * 60 * 1000L;
  protected AccountInfoCache accountCache = AccountInfoCache.empty();
  protected Patch.Key patchKey;
  private final Timestamp aged =
      new Timestamp(System.currentTimeMillis() - AGE);

  protected AbstractPatchContentTable() {
    table.setStyleName("gerrit-PatchContentTable");
  }

  @Override
  protected MyFlexTable createFlexTable() {
    return new DoubleClickFlexTable();
  }

  @Override
  protected Object getRowItemKey(final Object item) {
    return null;
  }

  /** Invoked when the user clicks on a table cell. */
  protected abstract void onCellDoubleClick(int row, int column);

  protected PatchLineComment newComment(final int line, final short side) {
    final PatchLineComment r =
        new PatchLineComment(new PatchLineComment.Key(patchKey, "blargh"), line,
            Gerrit.getUserAccount().getId());
    r.setSide(side);
    r.setMessage("");
    return r;
  }

  protected void createCommentEditor(final int row, final int column,
      final int line, final short side) {
    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(new AsyncCallback<VoidResult>() {
        public void onSuccess(final VoidResult result) {
          createCommentEditor(row, column, line, side);
        }

        public void onFailure(Throwable caught) {
        }
      });
      return;
    }

    final PatchLineComment newComment = newComment(line, side);
    table.insertRow(row);
    table.setWidget(row, column, new CommentEditorPanel(newComment) {
      @Override
      void onCancel() {
        final int n = table.getRowCount();
        for (int i = 0; i < n; i++) {
          if (column < table.getCellCount(i)
              && table.getWidget(i, column) == this) {
            table.removeRow(i);
            break;
          }
        }
      }
    });
  }

  @Override
  protected void onOpenItem(final Object item) {
    if (item instanceof CommentList) {
      for (final ComplexDisclosurePanel p : ((CommentList) item).panels) {
        p.setOpen(!p.isOpen());
      }
    }
  }

  public void setAccountInfoCache(final AccountInfoCache aic) {
    assert aic != null;
    accountCache = aic;
  }

  public void setPatchKey(final Patch.Key id) {
    patchKey = id;
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

    CommentList l = (CommentList) getRowItem(row);
    if (l == null) {
      l = new CommentList();
      setRowItem(row, l);
    }
    l.comments.add(line);
    l.panels.add(panel);
  }

  protected static class CommentList {
    final List<PatchLineComment> comments = new ArrayList<PatchLineComment>();
    final List<ComplexDisclosurePanel> panels =
        new ArrayList<ComplexDisclosurePanel>();
  }

  protected class DoubleClickFlexTable extends MyFlexTable {
    public DoubleClickFlexTable() {
      sinkEvents(Event.ONDBLCLICK);
    }

    @Override
    public void onBrowserEvent(final Event event) {
      switch (DOM.eventGetType(event)) {
        case Event.ONDBLCLICK: {
          // Find out which cell was actually clicked.
          Element td = getEventTargetCell(event);
          if (td == null) {
            return;
          }
          Element tr = DOM.getParent(td);
          Element body = DOM.getParent(tr);
          int row = DOM.getChildIndex(body, tr);
          int column = DOM.getChildIndex(tr, td);
          onCellDoubleClick(row, column);
          break;
        }
        default:
          super.onBrowserEvent(event);
      }
    }
  }
}
