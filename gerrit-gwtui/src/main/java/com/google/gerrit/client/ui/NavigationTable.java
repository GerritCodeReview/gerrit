// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public abstract class NavigationTable<RowItem> extends FancyFlexTable<RowItem> {
  protected class MyFlexTable extends FancyFlexTable.MyFlexTable {
    public MyFlexTable() {
      sinkEvents(Event.ONDBLCLICK | Event.ONCLICK);
    }

    @Override
    public void onBrowserEvent(Event event) {
      switch (DOM.eventGetType(event)) {
        case Event.ONCLICK:
          {
            // Find out which cell was actually clicked.
            final Element td = getEventTargetCell(event);
            if (td == null) {
              break;
            }
            final int row = rowOf(td);
            if (getRowItem(row) != null) {
              onCellSingleClick(event, rowOf(td), columnOf(td));
              return;
            }
            break;
          }
        case Event.ONDBLCLICK:
          {
            // Find out which cell was actually clicked.
            Element td = getEventTargetCell(event);
            if (td == null) {
              return;
            }
            onCellDoubleClick(rowOf(td), columnOf(td));
            return;
          }
      }
      super.onBrowserEvent(event);
    }
  }

  @SuppressWarnings("serial")
  private static final LinkedHashMap<String, Object> savedPositions =
      new LinkedHashMap<String, Object>(10, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<String, Object> eldest) {
          return size() >= 20;
        }
      };

  private final Image pointer;
  protected final KeyCommandSet keysNavigation;
  protected final KeyCommandSet keysAction;
  private HandlerRegistration regNavigation;
  private HandlerRegistration regAction;
  private int currentRow = -1;
  private String saveId;

  private boolean computedScrollType;
  private ScrollPanel parentScrollPanel;

  protected NavigationTable(String itemHelpName) {
    this();
    keysNavigation.add(
        new PrevKeyCommand(0, 'k', Util.M.helpListPrev(itemHelpName)),
        new NextKeyCommand(0, 'j', Util.M.helpListNext(itemHelpName)));
    keysNavigation.add(new OpenKeyCommand(0, 'o', Util.M.helpListOpen(itemHelpName)));
    keysNavigation.add(
        new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.M.helpListOpen(itemHelpName)));
  }

  protected NavigationTable() {
    pointer = new Image(Gerrit.RESOURCES.arrowRight());
    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
  }

  protected abstract void onOpenRow(int row);

  protected abstract Object getRowItemKey(RowItem item);

  private void onUp() {
    for (int row = currentRow - 1; row >= 0; row--) {
      if (getRowItem(row) != null) {
        movePointerTo(row);
        break;
      }
    }
  }

  private void onDown() {
    final int max = table.getRowCount();
    for (int row = currentRow + 1; row < max; row++) {
      if (getRowItem(row) != null) {
        movePointerTo(row);
        break;
      }
    }
  }

  private void onOpen() {
    if (0 <= currentRow && currentRow < table.getRowCount()) {
      if (getRowItem(currentRow) != null) {
        onOpenRow(currentRow);
      }
    }
  }

  /**
   * Invoked when the user double clicks on a table cell.
   *
   * @param row row number.
   * @param column column number.
   */
  protected void onCellDoubleClick(int row, int column) {
    onOpenRow(row);
  }

  /**
   * Invoked when the user clicks on a table cell.
   *
   * @param event click event.
   * @param row row number.
   * @param column column number.
   */
  protected void onCellSingleClick(Event event, int row, int column) {
    movePointerTo(row);
  }

  protected int getCurrentRow() {
    return currentRow;
  }

  protected void ensurePointerVisible() {
    final int max = table.getRowCount();
    int row = currentRow;
    final int init = row;
    if (row < 0) {
      row = 0;
    } else if (max <= row) {
      row = max - 1;
    }

    final CellFormatter fmt = table.getCellFormatter();
    final int sTop = Document.get().getScrollTop();
    final int sEnd = sTop + Document.get().getClientHeight();

    while (0 <= row && row < max) {
      final Element cur = fmt.getElement(row, C_ARROW).getParentElement();
      final int cTop = cur.getAbsoluteTop();
      final int cEnd = cTop + cur.getOffsetHeight();

      if (cEnd < sTop) {
        row++;
      } else if (sEnd < cTop) {
        row--;
      } else {
        break;
      }
    }

    if (init != row) {
      movePointerTo(row, false);
    }
  }

  protected void movePointerTo(int newRow) {
    movePointerTo(newRow, true);
  }

  protected void movePointerTo(int newRow, boolean scroll) {
    final CellFormatter fmt = table.getCellFormatter();
    final boolean clear = 0 <= currentRow && currentRow < table.getRowCount();
    if (clear) {
      final Element tr = fmt.getElement(currentRow, C_ARROW).getParentElement();
      UIObject.setStyleName(tr, Gerrit.RESOURCES.css().activeRow(), false);
    }
    if (0 <= newRow && newRow < table.getRowCount() && getRowItem(newRow) != null) {
      table.setWidget(newRow, C_ARROW, pointer);
      final Element tr = fmt.getElement(newRow, C_ARROW).getParentElement();
      UIObject.setStyleName(tr, Gerrit.RESOURCES.css().activeRow(), true);
      if (scroll && isAttached()) {
        scrollIntoView(tr);
      }
    } else if (clear) {
      table.setWidget(currentRow, C_ARROW, null);
      pointer.removeFromParent();
    }
    currentRow = newRow;
  }

  protected void scrollIntoView(Element tr) {
    if (!computedScrollType) {
      parentScrollPanel = null;
      Widget w = getParent();
      while (w != null) {
        if (w instanceof ScrollPanel) {
          parentScrollPanel = (ScrollPanel) w;
          break;
        }
        w = w.getParent();
      }
      computedScrollType = true;
    }

    if (parentScrollPanel != null) {
      parentScrollPanel.ensureVisible(
          new UIObject() {
            {
              setElement(tr);
            }
          });
    } else {
      int rt = tr.getAbsoluteTop();
      int rl = tr.getAbsoluteLeft();
      int rb = tr.getAbsoluteBottom();

      int wt = Window.getScrollTop();
      int wl = Window.getScrollLeft();

      int wh = Window.getClientHeight();
      int ww = Window.getClientWidth();
      int wb = wt + wh;

      // If the row is partially or fully obscured, scroll:
      //
      // rl < wl: Row left edge is off screen to left.
      // rt < wt: Row top is above top of window.
      // wb < rt: Row top is below bottom of window.
      // wb < rb: Row bottom is below bottom of window.
      if (rl < wl || rt < wt || wb < rt || wb < rb) {
        if (rl < wl) {
          // Left edge needs to move to make it visible.
          // If the row fully fits in the window, set 0.
          if (tr.getAbsoluteRight() < ww) {
            wl = 0;
          } else {
            wl = Math.max(tr.getAbsoluteLeft() - 5, 0);
          }
        }

        // Vertically center the row in the window.
        int h = (wh - (rb - rt)) / 2;
        Window.scrollTo(wl, Math.max(rt - h, 0));
      }
    }
  }

  protected void movePointerTo(Object oldId) {
    final int row = findRow(oldId);
    if (0 <= row) {
      movePointerTo(row);
    }
  }

  protected int findRow(Object oldId) {
    if (oldId != null) {
      final int max = table.getRowCount();
      for (int row = 0; row < max; row++) {
        final RowItem c = getRowItem(row);
        if (c != null && oldId.equals(getRowItemKey(c))) {
          return row;
        }
      }
    }
    return -1;
  }

  @Override
  public void resetHtml(SafeHtml body) {
    currentRow = -1;
    super.resetHtml(body);
  }

  public void finishDisplay() {
    if (currentRow >= table.getRowCount()) {
      currentRow = -1;
    }
    if (saveId != null) {
      movePointerTo(savedPositions.get(saveId));
    }
    if (currentRow < 0) {
      onDown();
    }
  }

  public void setSavePointerId(String id) {
    saveId = id;
  }

  public void setRegisterKeys(boolean on) {
    if (on && isAttached()) {
      if (regNavigation == null) {
        regNavigation = GlobalKey.add(this, keysNavigation);
      }
      if (regAction == null) {
        regAction = GlobalKey.add(this, keysAction);
      }
    } else {
      if (regNavigation != null) {
        regNavigation.removeHandler();
        regNavigation = null;
      }
      if (regAction != null) {
        regAction.removeHandler();
        regAction = null;
      }
    }
  }

  @Override
  protected void onLoad() {
    computedScrollType = false;
    parentScrollPanel = null;
  }

  @Override
  protected void onUnload() {
    setRegisterKeys(false);

    if (saveId != null && currentRow >= 0) {
      final RowItem c = getRowItem(currentRow);
      if (c != null) {
        savedPositions.put(saveId, getRowItemKey(c));
      }
    }

    computedScrollType = false;
    parentScrollPanel = null;
    super.onUnload();
  }

  @Override
  protected MyFlexTable createFlexTable() {
    return new MyFlexTable();
  }

  public class PrevKeyCommand extends KeyCommand {
    public PrevKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
      ensurePointerVisible();
      onUp();
    }
  }

  public class NextKeyCommand extends KeyCommand {
    public NextKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
      ensurePointerVisible();
      onDown();
    }
  }

  public class OpenKeyCommand extends KeyCommand {
    public OpenKeyCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
      ensurePointerVisible();
      onOpen();
    }
  }
}
