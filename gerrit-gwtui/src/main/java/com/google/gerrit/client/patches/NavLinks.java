// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.reviewdb.Change;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;

class NavLinks extends Composite {
  private final Change.Id changeId;
  private final KeyCommandSet keys;
  private final Grid table;

  private InlineHyperlink prev;
  private InlineHyperlink next;

  private KeyCommand prevKey;
  private KeyCommand nextKey;

  NavLinks(KeyCommandSet kcs, Change.Id forChange) {
    changeId = forChange;
    keys = kcs;
    table = new Grid(1, 3);
    initWidget(table);

    final CellFormatter fmt = table.getCellFormatter();
    table.setStyleName(Gerrit.RESOURCES.css().sideBySideScreenLinkTable());
    fmt.setHorizontalAlignment(0, 0, HasHorizontalAlignment.ALIGN_LEFT);
    fmt.setHorizontalAlignment(0, 1, HasHorizontalAlignment.ALIGN_CENTER);
    fmt.setHorizontalAlignment(0, 2, HasHorizontalAlignment.ALIGN_RIGHT);

    final ChangeLink up = new ChangeLink("", changeId);
    SafeHtml.set(up, SafeHtml.asis(Util.C.upToChangeIconLink()));
    table.setWidget(0, 1, up);
  }

  void display(int patchIndex, PatchScreen.Type type, PatchTable fileList) {
    if (keys != null && prevKey != null) {
      keys.remove(prevKey);
      prevKey = null;
    }

    if (keys != null && nextKey != null) {
      keys.remove(nextKey);
      nextKey = null;
    }

    if (fileList != null) {
      prev = fileList.getPreviousPatchLink(patchIndex, type);
      next = fileList.getNextPatchLink(patchIndex, type);
    } else {
      prev = null;
      next = null;
    }

    if (prev != null) {
      if (keys != null) {
        prevKey = new KeyCommand(0, '[', PatchUtil.C.previousFileHelp()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            prev.go();
          }
        };
        keys.add(prevKey);
      }
      table.setWidget(0, 0, prev);
    } else {
      if (keys != null) {
        prevKey = new UpToChangeCommand(changeId, 0, '[');
        keys.add(prevKey);
      }
      table.clearCell(0, 0);
    }

    if (next != null) {
      if (keys != null) {
        nextKey = new KeyCommand(0, ']', PatchUtil.C.nextFileHelp()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            next.go();
          }
        };
        keys.add(nextKey);
      }
      table.setWidget(0, 2, next);
    } else {
      if (keys != null) {
        nextKey = new UpToChangeCommand(changeId, 0, ']');
        keys.add(nextKey);
      }
      table.clearCell(0, 2);
    }
  }
}
