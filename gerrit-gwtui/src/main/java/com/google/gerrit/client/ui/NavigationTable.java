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
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

public abstract class NavigationTable<RowItem> extends FancyFlexTable<RowItem> {
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

  protected NavigationTable() {
    pointer = Gerrit.ICONS.arrowRight().createImage();
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
      final Element cur = DOM.getParent(fmt.getElement(row, C_ARROW));
      final int cTop = cur.getAbsoluteTop();
      final int cEnd = cTop + cur.getOffsetHeight();

      if (cEnd < sTop) {
        row++;
      } else if (sEnd < cTop) {
        row--;
      } else if (getRowItem(row) != null) {
        break;
      }
    }

    if (init != row) {
      movePointerTo(row, false);
    }
  }

  protected void movePointerTo(final int newRow) {
    movePointerTo(newRow, true);
  }

  protected void movePointerTo(final int newRow, final boolean scroll) {
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
      if (scroll) {
        scrollIntoView(tr);
      }
    } else if (clear) {
      table.setWidget(currentRow, C_ARROW, null);
    }
    currentRow = newRow;
  }

  protected void scrollIntoView(final Element tr) {
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
      parentScrollPanel.ensureVisible(new UIObject() {
        {
          setElement(tr);
        }
      });
    } else {
      tr.scrollIntoView();
    }
  }

  protected void movePointerTo(final Object oldId) {
    final int row = findRow(oldId);
    if (0 <= row) {
      movePointerTo(row);
    }
  }

  protected int findRow(final Object oldId) {
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
  protected void resetHtml(SafeHtml body) {
    currentRow = -1;
    super.resetHtml(body);
  }

  public void finishDisplay() {
    if (saveId != null) {
      movePointerTo(savedPositions.get(saveId));
    }
    if (currentRow < 0) {
      onDown();
    }
  }

  public void setSavePointerId(final String id) {
    saveId = id;
  }

  public void setRegisterKeys(final boolean on) {
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

  public class PrevKeyCommand extends KeyCommand {
    public PrevKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      ensurePointerVisible();
      onUp();
    }
  }

  public class NextKeyCommand extends KeyCommand {
    public NextKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      ensurePointerVisible();
      onDown();
    }
  }

  public class OpenKeyCommand extends KeyCommand {
    public OpenKeyCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      ensurePointerVisible();
      onOpen();
    }
  }
}
