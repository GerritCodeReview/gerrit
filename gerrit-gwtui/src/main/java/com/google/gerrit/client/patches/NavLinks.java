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
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;

class NavLinks extends Composite {
  public enum Nav {
    PREV (0, '[', PatchUtil.C.previousFileHelp(), 0),
    NEXT (2, ']', PatchUtil.C.nextFileHelp(), 1);

    public int col;      // Table Cell column to display link in
    public int key;      // key code shortcut to activate link
    public String help;  // help string for '?' popup
    public int cmd;      // index into cmds array

    Nav(int c, int k, String h, int i) {
      this.col = c;
      this.key = k;
      this.help = h;
      this.cmd = i;
    }
  }

  private final PatchSet.Id patchSetId;
  private final KeyCommandSet keys;
  private final Grid table;

  private KeyCommand cmds[] = new KeyCommand[2];

  NavLinks(KeyCommandSet kcs, PatchSet.Id forPatch) {
    patchSetId = forPatch;
    keys = kcs;
    table = new Grid(1, 3);
    initWidget(table);

    final CellFormatter fmt = table.getCellFormatter();
    table.setStyleName(Gerrit.RESOURCES.css().sideBySideScreenLinkTable());
    fmt.setHorizontalAlignment(0, 0, HasHorizontalAlignment.ALIGN_LEFT);
    fmt.setHorizontalAlignment(0, 1, HasHorizontalAlignment.ALIGN_CENTER);
    fmt.setHorizontalAlignment(0, 2, HasHorizontalAlignment.ALIGN_RIGHT);

    final ChangeLink up = new ChangeLink("", patchSetId);
    SafeHtml.set(up, SafeHtml.asis(Util.C.upToChangeIconLink()));
    table.setWidget(0, 1, up);
  }

  void display(int patchIndex, PatchScreen.Type type, PatchTable fileList) {
    if (fileList != null) {
      setupNav(Nav.PREV, fileList.getPreviousPatchLink(patchIndex, type));
      setupNav(Nav.NEXT, fileList.getNextPatchLink(patchIndex, type));
    } else {
      setupNav(Nav.PREV, null);
      setupNav(Nav.NEXT, null);
    }
  }

  protected void setupNav(final Nav nav, final InlineHyperlink link) {

    /* setup the cells */
    if (link != null) {
      table.setWidget(0, nav.col, link);
    } else {
      table.clearCell(0, nav.col);
    }

    /* setup the keys */
    if (keys != null) {

      if (cmds[nav.cmd] != null) {
        keys.remove(cmds[nav.cmd]);
      }

      if (link != null) {
        cmds[nav.cmd] = new KeyCommand(0, nav.key, nav.help) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            link.go();
          }
        };
      } else {
        cmds[nav.cmd] = new UpToChangeCommand(patchSetId, 0, nav.key);
      }

      keys.add(cmds[nav.cmd]);
    }
  }
}
