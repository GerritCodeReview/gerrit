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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.RadioButton;

import java.util.ArrayList;
import java.util.List;

/**
 * A table used to specify which two patch sets should be diff'ed.
 */
class HistoryTable extends FancyFlexTable<Patch> {
  private final PatchScreen screen;
  final List<HistoryRadio> all = new ArrayList<HistoryRadio>();

  HistoryTable(final PatchScreen parent) {
    setStyleName(Gerrit.RESOURCES.css().patchHistoryTable());
    screen = parent;
    table.setWidth("auto");
    table.addStyleName(Gerrit.RESOURCES.css().changeTable());
  }

  void onClick(final HistoryRadio b) {
    PatchSet.Id sideA = screen.idSideA;
    PatchSet.Id sideB = screen.idSideB;
    switch (b.file) {
      case 0:
        sideA = b.patchSetId;
        break;
      case 1:
        sideB = b.patchSetId;
        break;
      default:
        return;
    }
    enableAll(false);
    Patch.Key k = new Patch.Key(sideB, screen.getPatchKey().get());
    switch (screen.getPatchScreenType()) {
      case SIDE_BY_SIDE:
        Gerrit.display(Dispatcher.toPatchSideBySide(sideA, k));
        break;
      case UNIFIED:
        Gerrit.display(Dispatcher.toPatchUnified(sideA, k));
        break;
    }
  }

  void enableAll(final boolean on) {
    for (final HistoryRadio a : all) {
      a.setEnabled(on);
    }
  }

  void display(final List<Patch> result) {
    all.clear();
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    table.setText(0, 0, PatchUtil.C.patchHeaderPatchSet());
    fmt.setStyleName(0, 0, Gerrit.RESOURCES.css().dataHeader());
    table.setText(1, 0, PatchUtil.C.patchHeaderOld());
    fmt.setStyleName(1, 0, Gerrit.RESOURCES.css().dataHeader());
    table.setText(2, 0, PatchUtil.C.patchHeaderNew());
    fmt.setStyleName(2, 0, Gerrit.RESOURCES.css().dataHeader());
    table.setText(3, 0, Util.C.patchTableColumnComments());
    fmt.setStyleName(3, 0, Gerrit.RESOURCES.css().dataHeader());

    int col;
    if (screen.getPatchSetDetail().getInfo().getParents().size() > 1) {
      table.setText(0, 1, PatchUtil.C.patchBaseAutoMerge());
      table.setText(0, 2, PatchUtil.C.patchBaseCommonAncestor());
      fmt.setStyleName(0, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().topMostCell());
      fmt.setStyleName(1, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.setStyleName(2, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.setStyleName(3, 2, Gerrit.RESOURCES.css().dataCell());
      installRadio(1, 2, new PatchSet.Id(screen.idSideB.getParentKey(), 0),
          screen.idSideA, 0);
      col = 3;
    } else {
      table.setText(0, 1, PatchUtil.C.patchBase());
      col = 2;
    }
    fmt.setStyleName(0, 1, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topMostCell());
    fmt.setStyleName(1, 1, Gerrit.RESOURCES.css().dataCell());
    fmt.setStyleName(2, 1, Gerrit.RESOURCES.css().dataCell());
    fmt.setStyleName(3, 1, Gerrit.RESOURCES.css().dataCell());

    installRadio(1, 1, null, screen.idSideA, 0);

    for (final Patch k : result) {
      final PatchSet.Id psId = k.getKey().getParentKey();
      table.setText(0, col, String.valueOf(psId.get()));
      fmt.setStyleName(0, col, Gerrit.RESOURCES.css().patchHistoryTablePatchSetHeader());
      fmt.addStyleName(0, col, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(0, col, Gerrit.RESOURCES.css().topMostCell());

      installRadio(1, col, psId, screen.idSideA, 0);
      installRadio(2, col, psId, screen.idSideB, 1);

      fmt.setStyleName(3, col, Gerrit.RESOURCES.css().dataCell());
      if (k.getCommentCount() > 0) {
        table.setText(3, col, Integer.toString(k.getCommentCount()));
      }
      col++;
    }
  }

  private void installRadio(final int row, final int col, final PatchSet.Id psId,
      final PatchSet.Id cur, final int file) {
    final HistoryRadio b = new HistoryRadio(psId, file);
    b.setValue(eq(cur, psId));

    table.setWidget(row, col, b);
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setHorizontalAlignment(row, col, HasHorizontalAlignment.ALIGN_CENTER);
    fmt.setStyleName(row, col, Gerrit.RESOURCES.css().dataCell());
    all.add(b);
  }

  private boolean eq(final PatchSet.Id cur, final PatchSet.Id psid) {
    if (cur == null && psid == null) {
      return true;
    }
    return psid != null && psid.equals(cur);
  }

  private class HistoryRadio extends RadioButton {
    final PatchSet.Id patchSetId;
    final int file;

    HistoryRadio(final PatchSet.Id ps, final int f) {
      super(String.valueOf(f));
      sinkEvents(Event.ONCLICK);
      patchSetId = ps;
      file = f;
    }

    @Override
    public void onBrowserEvent(final Event event) {
      switch (DOM.eventGetType(event)) {
        case Event.ONCLICK:
          onClick(this);
          break;
        default:
          super.onBrowserEvent(event);
      }
    }
  }
}
