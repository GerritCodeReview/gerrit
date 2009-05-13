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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.HasBlurHandlers;
import com.google.gwt.event.dom.client.HasFocusHandlers;
import com.google.gwt.event.dom.client.HasKeyPressHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.user.client.UserAgent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public abstract class FancyFlexTable<RowItem> extends Composite implements
    Focusable, HasFocusHandlers, HasBlurHandlers, HasKeyPressHandlers {
  private static final FancyFlexTableImpl impl =
      GWT.create(FancyFlexTableImpl.class);

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
    focusy = UserAgent.wrapFocusPanel(table);
    if (focusy != null) {
      focusy.addKeyPressHandler(new KeyPressHandler() {
        @Override
        public void onKeyPress(final KeyPressEvent event) {
          if (FancyFlexTable.this.onKeyPress(event)) {
            event.stopPropagation();
            event.preventDefault();
          }
        }
      });
      focusy.addFocusHandler(new FocusHandler() {
        @Override
        public void onFocus(final FocusEvent event) {
          if (currentRow < 0) {
            onDown();
          }
        }
      });
    }
    initWidget(focusy != null ? focusy : table);

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

  protected void resetHtml(final SafeHtml body) {
    for (final Iterator<Widget> i = table.iterator(); i.hasNext();) {
      i.next();
      i.remove();
    }
    impl.resetHtml(table, body);
  }

  protected boolean onKeyPress(final KeyPressEvent event) {
    if (!event.isAnyModifierKeyDown()) {
      switch (event.getCharCode()) {
        case 'k':
        case KeyCodes.KEY_UP:
          onUp();
          return true;

        case 'j':
        case KeyCodes.KEY_DOWN:
          onDown();
          return true;

        case 'o':
        case KeyCodes.KEY_ENTER:
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
    final boolean clear = 0 <= currentRow && currentRow < table.getRowCount();
    if (clear) {
      final Element tr = DOM.getParent(fmt.getElement(currentRow, C_ARROW));
      UIObject.setStyleName(tr, S_ACTIVE_ROW, false);
    }
    if (newRow >= 0) {
      table.setWidget(newRow, C_ARROW, pointer);
      final Element tr = DOM.getParent(fmt.getElement(newRow, C_ARROW));
      UIObject.setStyleName(tr, S_ACTIVE_ROW, true);
      tr.scrollIntoView();
    } else if (clear) {
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
    return focusy != null ? focusy.getTabIndex() : 0;
  }

  public void setAccessKey(char key) {
    if (focusy != null) {
      focusy.setAccessKey(key);
    }
  }

  public void setFocus(boolean focused) {
    if (focusy != null) {
      focusy.setFocus(focused);
    }
  }

  public void setTabIndex(int index) {
    if (focusy != null) {
      focusy.setTabIndex(index);
    }
  }

  public HandlerRegistration addFocusHandler(FocusHandler handler) {
    if (focusy != null) {
      return focusy.addFocusHandler(handler);
    }
    return NoopRegistration.INSTANCE;
  }

  public HandlerRegistration addBlurHandler(BlurHandler handler) {
    if (focusy != null) {
      return focusy.addBlurHandler(handler);
    }
    return NoopRegistration.INSTANCE;
  }

  public HandlerRegistration addKeyPressHandler(KeyPressHandler handler) {
    if (focusy != null) {
      return focusy.addKeyPressHandler(handler);
    }
    return NoopRegistration.INSTANCE;
  }

  protected static class MyFlexTable extends FlexTable {
  }

  private static final class NoopRegistration implements HandlerRegistration {
    static final NoopRegistration INSTANCE = new NoopRegistration();

    @Override
    public void removeHandler() {
    }
  }

  private static final native <ItemType> void setRowItem(Element td, ItemType c)/*-{ td["__gerritRowItem"] = c; }-*/;

  private static final native <ItemType> ItemType getRowItem(Element td)/*-{ return td["__gerritRowItem"]; }-*/;
}
