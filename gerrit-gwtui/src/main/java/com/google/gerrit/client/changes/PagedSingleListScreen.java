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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwtexpui.globalkey.client.KeyCommand;

public abstract class PagedSingleListScreen extends Screen {
  protected final int pageSize;
  protected final int start;
  private final String anchorPrefix;

  protected ChangeList changes;
  private ChangeTable table;
  private ChangeTable.Section section;
  private Hyperlink prev;
  private Hyperlink next;

  protected PagedSingleListScreen(String anchorToken, int start) {
    anchorPrefix = anchorToken;
    this.start = start;
    pageSize = Gerrit.getUserPreferences().changesPerPage();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    prev = new Hyperlink(Util.C.pagedChangeListPrev(), true, "");
    prev.setVisible(false);

    next = new Hyperlink(Util.C.pagedChangeListNext(), true, "");
    next.setVisible(false);

    table =
        new ChangeTable() {
          {
            keysNavigation.add(
                new DoLinkCommand(0, 'p', Util.C.changeTablePagePrev(), prev),
                new DoLinkCommand(0, 'n', Util.C.changeTablePageNext(), next));

            keysNavigation.add(
                new DoLinkCommand(0, '[', Util.C.changeTablePagePrev(), prev),
                new DoLinkCommand(0, ']', Util.C.changeTablePageNext(), next));

            keysNavigation.add(
                new KeyCommand(0, 'R', Util.C.keyReloadSearch()) {
                  @Override
                  public void onKeyPress(KeyPressEvent event) {
                    Gerrit.display(getToken());
                  }
                });
          }
        };
    section = new ChangeTable.Section();
    table.addSection(section);
    table.setSavePointerId(anchorPrefix);
    add(table);

    final HorizontalPanel buttons = new HorizontalPanel();
    buttons.setStyleName(Gerrit.RESOURCES.css().changeTablePrevNextLinks());
    buttons.add(prev);
    buttons.add(next);
    add(buttons);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  protected AsyncCallback<ChangeList> loadCallback() {
    return new ScreenLoadCallback<ChangeList>(this) {
      @Override
      protected void preDisplay(ChangeList result) {
        display(result);
      }
    };
  }

  protected void display(ChangeList result) {
    changes = result;
    if (changes.length() != 0) {
      if (start > 0) {
        int p = start - pageSize;
        prev.setTargetHistoryToken(anchorPrefix + (p > 0 ? "," + p : ""));
        prev.setVisible(true);
      } else {
        prev.setVisible(false);
      }

      int n = start + changes.length();
      next.setTargetHistoryToken(anchorPrefix + "," + n);
      next.setVisible(changes.get(changes.length() - 1)._more_changes());
    }
    table.updateColumnsForLabels(result);
    section.display(result);
    table.finishDisplay();
  }

  private static final class DoLinkCommand extends KeyCommand {
    private final Hyperlink link;

    private DoLinkCommand(int mask, char key, String help, Hyperlink l) {
      super(mask, key, help);
      link = l;
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
      if (link.isVisible()) {
        History.newItem(link.getTargetHistoryToken());
      }
    }
  }
}
