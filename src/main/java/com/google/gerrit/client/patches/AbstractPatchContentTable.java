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
import com.google.gerrit.client.SignedInListener;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.client.VoidResult;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPatchContentTable extends FancyFlexTable<Object> {
  private static final long AGE = 7 * 24 * 60 * 60 * 1000L;
  protected AccountInfoCache accountCache = AccountInfoCache.empty();
  protected Patch.Key patchKey;
  protected List<PatchSet.Id> versions;
  private final Timestamp aged =
      new Timestamp(System.currentTimeMillis() - AGE);
  private final SignedInListener signedInListener = new SignedInListener() {
    public void onSignIn() {
      if (patchKey != null) {
        PatchUtil.DETAIL_SVC.myDrafts(patchKey,
            new GerritCallback<List<PatchLineComment>>() {
              public void onSuccess(final List<PatchLineComment> result) {
                if (!result.isEmpty()) {
                  bindDrafts(result);
                }
              }
            });
      }
    }

    public void onSignOut() {
      // TODO we should probably confirm with the user before sign out starts
      // that its OK to sign out if any of our editors are unsaved.
      // (bug GERRIT-16)
      //
      for (int row = 0; row < table.getRowCount();) {
        final int nCells = table.getCellCount(row);
        int inc = 1;
        for (int cell = 0; cell < nCells; cell++) {
          if (table.getWidget(row, cell) instanceof CommentEditorPanel) {
            destroyEditor(table, row, cell);
            inc = 0;
          }
        }
        row += inc;
      }
    }
  };

  protected AbstractPatchContentTable() {
    table.setStyleName("gerrit-PatchContentTable");
  }

  @Override
  public void onLoad() {
    super.onLoad();
    Gerrit.addSignedInListener(signedInListener);
  }

  @Override
  public void onUnload() {
    Gerrit.removeSignedInListener(signedInListener);
    super.onUnload();
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

  protected abstract void bindDrafts(List<PatchLineComment> drafts);

  protected void initVersions(int fileCnt) {
    if (versions == null) {
      versions = new ArrayList<PatchSet.Id>();
      for (int file = 0; file < fileCnt - 1; file++) {
        versions.add(PatchSet.BASE);
      }
      versions.add(patchKey.getParentKey());
    }
  }

  protected List<PatchSet.Id> getVersions() {
    return versions;
  }

  protected PatchSet.Id getVersion(final int fileId) {
    return versions.get(fileId);
  }

  protected void setVersion(final int fileId, final PatchSet.Id v) {
    versions.set(fileId, v);
  }

  protected int fileFor(final PatchLineComment c) {
    int fileId;
    for (fileId = 0; fileId < versions.size(); fileId++) {
      final PatchSet.Id i = versions.get(fileId);
      if (PatchSet.BASE.equals(i) && c.getSide() == fileId
          && patchKey.equals(c.getKey().getParentKey())) {
        break;
      }
      if (c.getSide() == versions.size() - 1
          && i.equals(c.getKey().getParentKey().getParentKey())) {
        break;
      }
    }
    return fileId;
  }

  protected void createCommentEditor(final int suggestRow, final int column,
      final int line, final short file) {
    int row = suggestRow;
    int spans[] = new int[column + 1];
    OUTER: while (row < table.getRowCount()) {
      int col = 0;
      for (int cell = 0; cell < table.getCellCount(row); cell++) {
        while (col < column && 0 < spans[col]) {
          spans[col++]--;
        }
        spans[col] = table.getFlexCellFormatter().getRowSpan(row, cell);
        if (col == column) {
          if (table.getWidget(row, cell) instanceof ComplexDisclosurePanel) {
            row++;
          } else {
            break OUTER;
          }
        }
      }
    }
    if (column < table.getCellCount(row)
        && table.getWidget(row, column) instanceof CommentEditorPanel) {
      // Don't insert two editors on the same position, it doesn't make
      // any sense to the user.
      //
      ((CommentEditorPanel) table.getWidget(row, column)).setFocus(true);
      return;
    }

    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(new AsyncCallback<VoidResult>() {
        public void onSuccess(final VoidResult result) {
          createCommentEditor(suggestRow, column, line, file);
        }

        public void onFailure(Throwable caught) {
        }
      });
      return;
    }

    final Patch.Key parentKey;
    final short side;
    if (PatchSet.BASE.equals(getVersion(file))) {
      parentKey = patchKey;
      side = file;
    } else {
      parentKey = new Patch.Key(getVersion(file), patchKey.get());
      side = (short) 1;
    }
    final PatchLineComment newComment =
        new PatchLineComment(new PatchLineComment.Key(parentKey, null), line,
            Gerrit.getUserAccount().getId());
    newComment.setSide(side);
    newComment.setMessage("");

    final CommentEditorPanel ed = new CommentEditorPanel(newComment);
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
    table.setWidget(row, column, ed);
    table.getFlexCellFormatter().setStyleName(row, column, "Comment");

    int span = 1;
    for (int r = row + 1; r < table.getRowCount(); r++) {
      boolean hasComment = false;
      for (int c = 0; c < table.getCellCount(r); c++) {
        final Widget w = table.getWidget(r, c);
        if (w instanceof ComplexDisclosurePanel
            || w instanceof CommentEditorPanel) {
          hasComment = true;
          break;
        }
      }
      if (hasComment) {
        table.removeCell(r, column);
        span++;
      } else {
        break;
      }
    }
    if (span > 1) {
      table.getFlexCellFormatter().setRowSpan(row, column, span);
    }

    for (int r = row - 1; r > 0; r--) {
      if (getRowItem(r) instanceof CommentList) {
        continue;
      } else if (getRowItem(r) != null) {
        movePointerTo(r);
        break;
      }
    }

    ed.setFocus(true);
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

  static void destroyEditor(final FlexTable table, final int row, final int col) {
    table.clearCell(row, col);
    final int span = table.getFlexCellFormatter().getRowSpan(row, col);
    boolean removeRow = true;
    final int nCells = table.getCellCount(row);
    for (int cell = 0; cell < nCells; cell++) {
      if (table.getWidget(row, cell) != null) {
        removeRow = false;
        break;
      }
    }
    if (removeRow) {
      for (int r = row - 1; 0 <= r; r--) {
        boolean data = false;
        for (int c = 0; c < table.getCellCount(r); c++) {
          data |= table.getWidget(r, c) != null;
          final int s = table.getFlexCellFormatter().getRowSpan(r, c) - 1;
          if (r + s == row) {
            table.getFlexCellFormatter().setRowSpan(r, c, s);
          }
        }
        if (!data) {
          break;
        }
      }
      table.removeRow(row);
    } else if (span != 1) {
      table.getFlexCellFormatter().setRowSpan(row, col, 1);
      for (int r = row + 1; r < row + span; r++) {
        table.insertCell(r, col + 1);
      }
    }
  }

  protected void bindComment(final int row, final int col,
      final PatchLineComment line, final boolean isLast) {
    if (line.getStatus() == PatchLineComment.Status.DRAFT) {
      boolean takeFocus = false;
      if (row + 1 < table.getRowCount() && col < table.getCellCount(row + 1)
          && table.getWidget(row + 1, col) instanceof CommentEditorPanel
          && ((CommentEditorPanel) table.getWidget(row + 1, col)).isNew()) {
        // Assume the second panel is a new one created while logging in;
        // this line is from the myDrafts callback and we discovered we
        // already have a draft at this location. We want to destroy the
        // dummy editor and keep the real one.
        //
        destroyEditor(table, row + 1, col);
        takeFocus = true;
      }
      final CommentEditorPanel plc = new CommentEditorPanel(line);
      table.setWidget(row, col, plc);
      table.getFlexCellFormatter().setStyleName(row, col, "Comment");
      if (takeFocus) {
        plc.setFocus(true);
      }
      return;
    }

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
    panel.setContent(mp);
    table.setWidget(row, col, panel);
    table.getFlexCellFormatter().setStyleName(row, col, "Comment");

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
      sinkEvents(Event.ONDBLCLICK | Event.ONCLICK);
    }

    @Override
    public void onBrowserEvent(final Event event) {
      switch (DOM.eventGetType(event)) {
        case Event.ONCLICK: {
          // Find out which cell was actually clicked.
          final Element td = getEventTargetCell(event);
          if (td == null) {
            break;
          }
          final Element tr = DOM.getParent(td);
          final Element body = DOM.getParent(tr);
          final int row = DOM.getChildIndex(body, tr);
          if (getRowItem(row) != null) {
            movePointerTo(row);
            return;
          }
          break;
        }
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
          return;
        }
      }
      super.onBrowserEvent(event);
    }
  }
}
