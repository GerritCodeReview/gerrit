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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.Link;
import com.google.gerrit.client.SignOutEvent;
import com.google.gerrit.client.SignOutHandler;
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.changes.PublishCommentScreen;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.PatchScript;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.KeyCommand;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPatchContentTable extends NavigationTable<Object> {
  private static final long AGE = 7 * 24 * 60 * 60 * 1000L;
  protected AccountInfoCache accountCache = AccountInfoCache.empty();
  protected Patch.Key patchKey;
  protected PatchSet.Id idSideA;
  protected PatchSet.Id idSideB;

  private final Timestamp aged =
      new Timestamp(System.currentTimeMillis() - AGE);
  private HandlerRegistration regSignOut;

  protected AbstractPatchContentTable() {
    keysNavigation.add(new UpToChangeCommand(0, 'u', PatchUtil.C.upToChange()));
    keysNavigation.add(new PrevKeyCommand(0, 'k', PatchUtil.C.linePrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', PatchUtil.C.lineNext()));

    if (Gerrit.isSignedIn()) {
      keysAction.add(new InsertCommentCommand(0, 'c', PatchUtil.C
          .commentInsert()));
      keysAction.add(new PublishCommentsKeyCommand(0, 'r', Util.C
          .keyPublishComments()));

      // See CommentEditorPanel
      //
      keysAction.add(new NoOpKeyCommand(KeyCommand.M_CTRL, 's', PatchUtil.C
          .commentSaveDraft()));
      keysAction.add(new NoOpKeyCommand(KeyCommand.M_CTRL, 'd', PatchUtil.C
          .commentDiscard()));
      keysAction.add(new NoOpKeyCommand(0, KeyCodes.KEY_ESCAPE, PatchUtil.C
          .commentCancelEdit()));
    }

    table.setStyleName("gerrit-PatchContentTable");
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (regSignOut == null && Gerrit.isSignedIn()) {
      regSignOut = Gerrit.addSignOutHandler(new SignOutHandler() {
        public void onSignOut(final SignOutEvent event) {
          // TODO we should confirm with the user before sign out starts
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
          regSignOut.removeHandler();
          regSignOut = null;
        }
      });
    }
  }

  @Override
  public void onUnload() {
    if (regSignOut != null) {
      regSignOut.removeHandler();
      regSignOut = null;
    }
    super.onUnload();
  }

  public void display(final Patch.Key k, final PatchSet.Id a,
      final PatchSet.Id b, final PatchScript s) {
    patchKey = k;
    idSideA = a;
    idSideB = b;
    render(s);
  }

  protected abstract void render(PatchScript script);

  protected abstract void onInsertComment(PatchLine pl);

  public abstract void display(CommentDetail comments);

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
    if (row < table.getRowCount() && column < table.getCellCount(row)
        && table.getWidget(row, column) instanceof CommentEditorPanel) {
      // Don't insert two editors on the same position, it doesn't make
      // any sense to the user.
      //
      ((CommentEditorPanel) table.getWidget(row, column)).setFocus(true);
      return;
    }

    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn();
      return;
    }

    final Patch.Key parentKey;
    final short side;
    switch (file) {
      case 0:
        if (idSideA == null) {
          parentKey = new Patch.Key(idSideB, patchKey.get());
          side = (short) 0;
        } else {
          parentKey = new Patch.Key(idSideA, patchKey.get());
          side = (short) 1;
        }
        break;
      case 1:
        parentKey = new Patch.Key(idSideB, patchKey.get());
        side = (short) 1;
        break;
      default:
        throw new RuntimeException("unexpected file id " + file);
    }

    final PatchLineComment newComment =
        new PatchLineComment(new PatchLineComment.Key(parentKey, null), line,
            Gerrit.getUserAccount().getId());
    newComment.setSide(side);
    newComment.setMessage("");

    final CommentEditorPanel ed = new CommentEditorPanel(newComment);
    boolean needInsert = true;
    if (row < table.getRowCount()) {
      for (int cell = 0; cell < table.getCellCount(row); cell++) {
        final Widget w = table.getWidget(row, cell);
        if (w instanceof CommentEditorPanel
            || w instanceof ComplexDisclosurePanel) {
          needInsert = false;
          break;
        }
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
      final CommentEditorPanel plc = new CommentEditorPanel(line);
      table.setWidget(row, col, plc);
      table.getFlexCellFormatter().setStyleName(row, col, "Comment");
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

  public class UpToChangeCommand extends KeyCommand {
    public UpToChangeCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      final Change.Id ck = patchKey.getParentKey().getParentKey();
      Gerrit.display(Link.toChange(ck), new ChangeScreen(ck));
    }
  }

  public static class NoOpKeyCommand extends NeedsSignInKeyCommand {
    public NoOpKeyCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
    }
  }

  public class InsertCommentCommand extends NeedsSignInKeyCommand {
    public InsertCommentCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      for (int row = getCurrentRow(); 0 <= row; row--) {
        final Object item = getRowItem(row);
        if (item instanceof PatchLine) {
          onInsertComment((PatchLine) item);
          return;
        } else if (item instanceof CommentList) {
          continue;
        } else {
          return;
        }
      }
    }
  }

  public class PublishCommentsKeyCommand extends NeedsSignInKeyCommand {
    public PublishCommentsKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      final PatchSet.Id id = patchKey.getParentKey();
      Gerrit.display("change,publish," + id.toString(),
          new PublishCommentScreen(id));
    }
  }
}
