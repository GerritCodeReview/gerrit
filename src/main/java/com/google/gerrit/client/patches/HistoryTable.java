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

import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.List;

class HistoryTable extends FancyFlexTable<Patch> {
  private final PatchScreen screen;
  final List<HistoryRadio> all = new ArrayList<HistoryRadio>();

  HistoryTable(final PatchScreen parent) {
    setStyleName("gerrit-PatchHistoryTable");
    screen = parent;
    table.addStyleName("gerrit-PatchHistoryTable");
    table.addTableListener(new TableListener() {
      public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
        if (row > 0) {
          movePointerTo(row);
        }
      }
    });
  }

  @Override
  protected Object getRowItemKey(final Patch item) {
    return item.getKey();
  }

  @Override
  protected boolean onKeyPress(final char keyCode, final int modifiers) {
    if (super.onKeyPress(keyCode, modifiers)) {
      return true;
    }
    if (modifiers == 0 && getCurrentRow() > 0) {
      switch (keyCode) {
        case 'o':
        case 'l': {
          final Widget w = table.getWidget(getCurrentRow(), radioCell(0));
          if (w != null) {
            fakeClick((HistoryRadio) w);
          }
          break;
        }

        case 'r':
        case 'n': {
          final Widget w = table.getWidget(getCurrentRow(), radioCell(1));
          if (w != null) {
            fakeClick((HistoryRadio) w);
          }
          break;
        }
      }
    }
    return false;
  }

  private void fakeClick(final HistoryRadio b) {
    if (!b.isChecked() && b.isEnabled()) {
      for (final HistoryRadio a : all) {
        if (a.isChecked() && a.getName().equals(b.getName())) {
          a.setChecked(false);
          break;
        }
      }
      b.setChecked(true);
      onClick(b);
    }
  }

  void onClick(final HistoryRadio b) {
    switch (b.file) {
      case 0:
        screen.idSideA = b.patchSetId;
        break;
      case 1:
        screen.idSideB = b.patchSetId;
        break;
      default:
        return;
    }

    enableAll(false);
    screen.refresh(false);
  }

  void enableAll(final boolean on) {
    for (final HistoryRadio a : all) {
      a.setEnabled(on);
    }
  }

  void display(final List<Patch> result) {
    final int cur = getCurrentRow();
    final Patch old = 0 < cur ? getRowItem(cur) : null;

    all.clear();

    final SafeHtmlBuilder nc = new SafeHtmlBuilder();
    appendHeader(nc);
    appendRow(nc, null);
    for (final Patch k : result) {
      appendRow(nc, k);
    }
    resetHtml(nc);

    int row = 1;
    {
      final Patch k = new Patch(new Patch.Key(null, ""));
      setRowItem(row, k);
      installRadio(row, k, 0, screen.idSideA);
      row++;
    }
    for (final Patch k : result) {
      setRowItem(row, k);
      installRadio(row, k, 0, screen.idSideA);
      installRadio(row, k, 1, screen.idSideB);
      row++;
    }
    if (old != null) {
      movePointerTo(getRowItemKey(old));
    }
  }

  private void installRadio(final int row, final Patch k, final int file,
      final PatchSet.Id cur) {
    final PatchSet.Id psid = k.getKey().getParentKey();
    final HistoryRadio b = new HistoryRadio(psid, file);
    b.setChecked(eq(cur, psid));

    final int cell = radioCell(file);
    table.setWidget(row, cell, b);
    table.getCellFormatter().setHorizontalAlignment(row, cell,
        HasHorizontalAlignment.ALIGN_CENTER);
    all.add(b);
  }

  private boolean eq(final PatchSet.Id cur, final PatchSet.Id psid) {
    if (cur == null && psid == null) {
      return true;
    }
    return psid != null && psid.equals(cur);
  }

  private int radioCell(final int file) {
    return 2 + file;
  }

  private void appendHeader(final SafeHtmlBuilder m) {
    m.openTr();

    m.openTd();
    m.addStyleName(S_ICON_HEADER);
    m.addStyleName("LeftMostCell");
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName(S_DATA_HEADER);
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName(S_DATA_HEADER);
    m.append(PatchUtil.C.patchHeaderOld());
    m.closeTd();

    m.openTd();
    m.setStyleName(S_DATA_HEADER);
    m.append(PatchUtil.C.patchHeaderNew());
    m.closeTd();

    m.openTd();
    m.setStyleName(S_DATA_HEADER);
    m.append(Util.C.patchTableColumnComments());
    m.closeTd();

    m.closeTr();
  }

  private void appendRow(final SafeHtmlBuilder m, final Patch k) {
    m.openTr();

    m.openTd();
    m.addStyleName(S_ICON_CELL);
    m.addStyleName("LeftMostCell");
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName(S_DATA_CELL);
    m.setAttribute("align", "right");
    if (k != null) {
      final PatchSet.Id psId = k.getKey().getParentKey();
      m.append(Util.M.patchSetHeader(psId.get()));
    } else {
      m.append("Base");
    }
    m.closeTd();

    m.openTd();
    m.setStyleName(S_DATA_CELL);
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName(S_DATA_CELL);
    m.nbsp();
    m.closeTd();

    m.openTd();
    m.setStyleName(S_DATA_CELL);
    if (k != null && k.getCommentCount() > 0) {
      m.append(Util.M.patchTableComments(k.getCommentCount()));
    } else {
      m.nbsp();
    }
    m.closeTd();

    m.closeTr();
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
