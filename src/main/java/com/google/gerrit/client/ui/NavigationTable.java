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
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

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

  protected NavigationTable() {
    pointer = Gerrit.ICONS.arrowRight().createImage();
    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
  }

  protected abstract void onOpenItem(RowItem item);

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
      final RowItem item = getRowItem(currentRow);
      if (item != null) {
        onOpenItem(item);
      }
    }
  }

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
    if (on) {
      if (regNavigation == null) {
        regNavigation = GlobalKey.add(keysNavigation);
      }
      if (regAction == null) {
        regAction = GlobalKey.add(keysAction);
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
  public void onUnload() {
    setRegisterKeys(false);

    if (saveId != null && currentRow >= 0) {
      final RowItem c = getRowItem(currentRow);
      if (c != null) {
        savedPositions.put(saveId, getRowItemKey(c));
      }
    }

    super.onUnload();
  }

  public class PrevKeyCommand extends KeyCommand {
    public PrevKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onUp();
    }
  }

  public class NextKeyCommand extends KeyCommand {
    public NextKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onDown();
    }
  }

  public class OpenKeyCommand extends KeyCommand {
    public OpenKeyCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onOpen();
    }
  }
}
