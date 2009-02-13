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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FocusListener;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.HasFocus;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.KeyboardListener;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public abstract class FancyFlexTable<RowItem> extends Composite implements
    HasFocus {
  protected static final String MY_STYLE = "gerrit-ChangeTable";
  protected static final String S_ICON_HEADER = "IconHeader";
  protected static final String S_DATA_HEADER = "DataHeader";
  protected static final String S_ICON_CELL = "IconCell";
  protected static final String S_DATA_CELL = "DataCell";
  protected static final String S_LEFT_MOST_CELL = "LeftMostCell";
  protected static final String S_ACTIVE_ROW = "ActiveRow";

  protected static final int C_ARROW = 0;

  private static final LinkedHashMap<String, Object> savedPositions =
      new LinkedHashMap<String, Object>(10, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<String, Object> eldest) {
          return size() >= 20;
        }
      };

  protected final MyFlexTable table;
  private final FocusPanel focusy;
  private final Image pointer;
  private String saveId;
  private int currentRow = -1;

  protected FancyFlexTable() {
    pointer = Gerrit.ICONS.arrowRight().createImage();
    table = createFlexTable();
    table.addStyleName(MY_STYLE);
    focusy = new FocusPanel(table);
    focusy.addKeyboardListener(new KeyboardListenerAdapter() {
      @Override
      public void onKeyPress(Widget sender, char keyCode, int modifiers) {
        if (FancyFlexTable.this.onKeyPress(keyCode, modifiers)) {
          final Event event = DOM.eventGetCurrentEvent();
          DOM.eventCancelBubble(event, true);
          DOM.eventPreventDefault(event);
        }
      }
    });
    focusy.addFocusListener(new FocusListener() {
      public void onFocus(final Widget sender) {
        if (currentRow < 0) {
          onDown();
        }
      }

      public void onLostFocus(final Widget sender) {
      }
    });
    initWidget(focusy);

    table.setText(0, C_ARROW, "");
    table.getCellFormatter().addStyleName(0, C_ARROW, S_ICON_HEADER);
  }

  protected MyFlexTable createFlexTable() {
    return new MyFlexTable();
  }

  protected RowItem getRowItem(final int row) {
    return FancyFlexTable.<RowItem> getRowItem(table.getCellFormatter()
        .getElement(row, 0));
  }

  protected void setRowItem(final int row, final RowItem item) {
    setRowItem(table.getCellFormatter().getElement(row, 0), item);
  }

  protected void resetHtml(final String body) {
    for (final Iterator<Widget> i = table.iterator(); i.hasNext();) {
      i.next();
      i.remove();
    }
    DOM.setInnerHTML(table.getBodyElement(), body);
  }

  protected boolean onKeyPress(final char keyCode, final int modifiers) {
    if (modifiers == 0) {
      switch (keyCode) {
        case 'k':
        case KeyboardListener.KEY_UP:
          onUp();
          return true;

        case 'j':
        case KeyboardListener.KEY_DOWN:
          onDown();
          return true;

        case 'o':
        case KeyboardListener.KEY_ENTER:
          onOpen();
          return true;
      }
    }
    return false;
  }

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

  protected void onOpen() {
    if (0 <= currentRow && currentRow < table.getRowCount()) {
      final RowItem item = getRowItem(currentRow);
      if (item != null) {
        onOpenItem(item);
      }
    }
  }

  protected void onOpenItem(final RowItem item) {
  }

  protected abstract Object getRowItemKey(RowItem item);

  protected int getCurrentRow() {
    return currentRow;
  }

  protected void movePointerTo(final int newRow) {
    final CellFormatter fmt = table.getCellFormatter();
    if (currentRow >= 0) {
      final Element tr = DOM.getParent(fmt.getElement(currentRow, C_ARROW));
      UIObject.setStyleName(tr, S_ACTIVE_ROW, false);
    }
    if (newRow >= 0) {
      table.setWidget(newRow, C_ARROW, pointer);
      final Element tr = DOM.getParent(fmt.getElement(newRow, C_ARROW));
      UIObject.setStyleName(tr, S_ACTIVE_ROW, true);
      tr.scrollIntoView();
    } else if (currentRow >= 0) {
      table.setWidget(currentRow, C_ARROW, null);
    }
    currentRow = newRow;
  }

  protected void movePointerTo(final Object oldId) {
    if (oldId != null) {
      final int max = table.getRowCount();
      for (int row = 0; row < max; row++) {
        final RowItem c = getRowItem(row);
        if (c != null && oldId.equals(getRowItemKey(c))) {
          movePointerTo(row);
          break;
        }
      }
    }
  }

  protected void applyDataRowStyle(final int newRow) {
    table.getCellFormatter().addStyleName(newRow, C_ARROW, S_ICON_CELL);
    table.getCellFormatter().addStyleName(newRow, C_ARROW, S_LEFT_MOST_CELL);
  }

  public void finishDisplay(final boolean requestFocus) {
    if (saveId != null) {
      final Object oldId = savedPositions.get(saveId);
      movePointerTo(oldId);
    }

    if (currentRow < 0) {
      onDown();
    }

    if (requestFocus && currentRow >= 0) {
      DeferredCommand.addCommand(new Command() {
        public void execute() {
          setFocus(true);
        }
      });
    }
  }

  public void setSavePointerId(final String id) {
    saveId = id;
  }

  @Override
  public void onUnload() {
    if (saveId != null && currentRow >= 0) {
      final RowItem c = getRowItem(currentRow);
      if (c != null) {
        savedPositions.put(saveId, getRowItemKey(c));
      }
    }
    super.onUnload();
  }

  public int getTabIndex() {
    return focusy.getTabIndex();
  }

  public void setAccessKey(char key) {
    focusy.setAccessKey(key);
  }

  public void setFocus(boolean focused) {
    focusy.setFocus(focused);
  }

  public void setTabIndex(int index) {
    focusy.setTabIndex(index);
  }

  public void addFocusListener(FocusListener listener) {
    focusy.addFocusListener(listener);
  }

  public void addKeyboardListener(KeyboardListener listener) {
    focusy.addKeyboardListener(listener);
  }

  public void removeFocusListener(FocusListener listener) {
    focusy.removeFocusListener(listener);
  }

  public void removeKeyboardListener(KeyboardListener listener) {
    focusy.removeKeyboardListener(listener);
  }

  protected static class MyFlexTable extends FlexTable {
    @Override
    public Element getBodyElement() {
      return super.getBodyElement();
    }
  }

  private static final native <ItemType> void setRowItem(Element td, ItemType c)/*-{ td["__gerritRowItem"] = c; }-*/;

  private static final native <ItemType> ItemType getRowItem(Element td)/*-{ return td["__gerritRowItem"]; }-*/;
}
