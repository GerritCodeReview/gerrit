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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NoDifferencesException;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtjsonrpc.client.RemoteJsonException;

import java.util.ArrayList;
import java.util.List;

public class PatchSideBySideScreen extends PatchScreen {
  private DisclosurePanel historyPanel;
  private HistoryTable historyTable;
  private FlowPanel sbsPanel;
  private SideBySideTable sbsTable;

  public PatchSideBySideScreen(final Patch.Key id) {
    super(id);
  }

  @Override
  public void onLoad() {
    if (sbsTable == null) {
      initUI();
    }

    super.onLoad();

    PatchUtil.DETAIL_SVC.sideBySidePatchDetail(patchId, null,
        new ScreenLoadCallback<SideBySidePatchDetail>(this) {
          @Override
          protected void prepare(final SideBySidePatchDetail r) {
            display(r);
          }
        });
  }

  private void initUI() {
    historyTable = new HistoryTable();
    historyPanel = new DisclosurePanel(PatchUtil.C.patchHistoryTitle());
    historyPanel.setContent(historyTable);
    historyPanel.setVisible(false);
    add(historyPanel);

    sbsPanel = new FlowPanel();
    sbsPanel.setStyleName("gerrit-SideBySideScreen-SideBySideTable");
    sbsTable = new SideBySideTable();
    sbsPanel.add(sbsTable);
    add(sbsPanel);
  }

  private void display(final SideBySidePatchDetail detail) {
    showSideBySide(detail);
    if (detail.getHistory() != null && detail.getHistory().size() > 1) {
      historyTable.display(detail.getHistory());
      historyPanel.setOpen(false);
      historyPanel.setVisible(true);
    } else {
      historyPanel.setVisible(false);
    }
  }

  private void showSideBySide(final SideBySidePatchDetail r) {
    sbsTable.setAccountInfoCache(r.getAccounts());
    sbsTable.display(r);
    sbsTable.finishDisplay(true);
  }

  private class HistoryTable extends FancyFlexTable<Patch> {
    final List<HistoryRadio> all = new ArrayList<HistoryRadio>();

    HistoryTable() {
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
            final int fileCnt = sbsTable.getFileCount();
            final Widget w =
                table.getWidget(getCurrentRow(), radioCell(fileCnt - 1));
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

    public void onClick(final HistoryRadio b) {
      sbsTable.setVersion(b.file, b.patchSetId);
      boolean diff = false;
      PatchSet.Id last = sbsTable.getVersion(0);
      for (int i = 1; i < sbsTable.getFileCount(); i++) {
        if (!last.equals(sbsTable.getVersion(i))) {
          diff = true;
        }
      }

      enable(false);
      PatchUtil.DETAIL_SVC.sideBySidePatchDetail(patchId, sbsTable
          .getVersions(), new GerritCallback<SideBySidePatchDetail>() {
        public void onSuccess(final SideBySidePatchDetail r) {
          enable(true);
          sbsPanel.setVisible(true);
          showSideBySide(r);
        }

        @Override
        public void onFailure(final Throwable caught) {
          enable(true);
          if (isNoDifferences(caught)) {
            sbsPanel.setVisible(false);
          } else {
            super.onFailure(caught);
          }
        }

        boolean isNoDifferences(final Throwable caught) {
          if (caught instanceof NoDifferencesException) {
            return true;
          }
          return caught instanceof RemoteJsonException
              && caught.getMessage().equals(NoDifferencesException.MESSAGE);
        }
      });
    }

    private void enable(final boolean on) {
      for (final HistoryRadio a : all) {
        a.setEnabled(on);
      }
    }

    void display(final List<Patch> result) {
      all.clear();

      final SafeHtmlBuilder nc = new SafeHtmlBuilder();
      appendHeader(nc);
      for (int p = result.size() - 1; p >= 0; p--) {
        final Patch k = result.get(p);
        appendRow(nc, k);
      }
      appendRow(nc, null);
      resetHtml(nc);

      final int fileCnt = sbsTable.getFileCount();
      int row = 1;
      for (int p = result.size() - 1; p >= 0; p--, row++) {
        final Patch k = result.get(p);
        setRowItem(row, k);
        for (int file = 0; file < fileCnt; file++) {
          final PatchSet.Id psid = k.getKey().getParentKey();
          final HistoryRadio b = new HistoryRadio(psid, file);
          b.setChecked(psid.equals(sbsTable.getVersion(file)));
          installRadio(row, file, b);
        }
      }
      for (int file = 0; file < fileCnt - 1; file++) {
        setRowItem(row, new Patch(new Patch.Key(PatchSet.BASE, "")));
        final HistoryRadio b = new HistoryRadio(PatchSet.BASE, file);
        b.setChecked(b.patchSetId.equals(sbsTable.getVersion(file)));
        installRadio(row, file, b);
      }
    }

    private void installRadio(final int row, final int file,
        final HistoryRadio b) {
      final int cell = radioCell(file);
      table.setWidget(row, cell, b);
      table.getCellFormatter().setHorizontalAlignment(row, cell,
          HasHorizontalAlignment.ALIGN_CENTER);
      all.add(b);
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

      for (int file = 0; file < sbsTable.getFileCount(); file++) {
        m.openTd();
        m.setStyleName(S_DATA_HEADER);
        m.append(sbsTable.getFileTitle(file));
        m.closeTd();
      }

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

      for (int file = 0; file < sbsTable.getFileCount(); file++) {
        m.openTd();
        m.setStyleName(S_DATA_CELL);
        m.nbsp();
        m.closeTd();
      }

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
}
