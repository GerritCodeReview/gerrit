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

import com.google.gerrit.client.Link;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.PatchLink;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.IncrementalCommand;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwtexpui.progress.client.ProgressBar;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;

import java.util.List;

public class PatchTable extends Composite {
  private final FlowPanel myBody;
  private PatchSet.Id psid;
  private String savePointerId;

  public PatchTable() {
    myBody = new FlowPanel();
    initWidget(myBody);
  }

  public void display(final PatchSet.Id id, final List<Patch> list) {
    psid = id;
    final DisplayCommand cmd = new DisplayCommand(list);
    if (cmd.execute()) {
      cmd.initMeter();
      DeferredCommand.addCommand(cmd);
    } else {
      cmd.showTable();
    }
  }

  public void setSavePointerId(final String id) {
    savePointerId = id;
  }

  private class MyTable extends NavigationTable<Patch> {
    MyTable() {
      keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.patchTablePrev()));
      keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.patchTableNext()));
      keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.patchTableOpen()));
      keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
          .patchTableOpen()));

      table.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          final Cell cell = table.getCellForEvent(event);
          if (cell != null && cell.getRowIndex() > 0) {
            movePointerTo(cell.getRowIndex());
          }
        }
      });
      setSavePointerId(PatchTable.this.savePointerId);
    }

    @Override
    public void resetHtml(final SafeHtml html) {
      super.resetHtml(html);
    }

    void initializeRow(final int row, final Patch p) {
      final int C_PATH = 2;
      final int C_SIDEBYSIDE = 4;
      setRowItem(row, p);

      Widget nameCol;
      if (p.getPatchType() == Patch.PatchType.UNIFIED) {
        nameCol = new PatchLink.SideBySide(p.getFileName(), p.getKey());
      } else {
        nameCol = new PatchLink.Unified(p.getFileName(), p.getKey());
      }
      if (p.getSourceFileName() != null) {
        final String text;
        if (p.getChangeType() == Patch.ChangeType.RENAMED) {
          text = Util.M.renamedFrom(p.getSourceFileName());
        } else if (p.getChangeType() == Patch.ChangeType.COPIED) {
          text = Util.M.copiedFrom(p.getSourceFileName());
        } else {
          text = Util.M.otherFrom(p.getSourceFileName());
        }
        final Label line = new Label(text);
        line.setStyleName("SourceFilePath");
        final FlowPanel cell = new FlowPanel();
        cell.add(nameCol);
        cell.add(line);
        nameCol = cell;
      }
      table.setWidget(row, C_PATH, nameCol);

      int C_UNIFIED = C_SIDEBYSIDE + 1;
      if (p.getPatchType() == Patch.PatchType.UNIFIED) {
        table.setWidget(row, C_SIDEBYSIDE, new PatchLink.SideBySide(Util.C
            .patchTableDiffSideBySide(), p.getKey()));

      } else if (p.getPatchType() == Patch.PatchType.BINARY) {
        C_UNIFIED = C_SIDEBYSIDE + 2;
      }
      table.setWidget(row, C_UNIFIED, new PatchLink.Unified(Util.C
          .patchTableDiffUnified(), p.getKey()));
    }

    void appendHeader(final SafeHtmlBuilder m) {
      m.openTr();

      m.openTd();
      m.addStyleName(S_ICON_HEADER);
      m.addStyleName("LeftMostCell");
      m.nbsp();
      m.closeTd();

      m.openTd();
      m.setStyleName(S_ICON_HEADER);
      m.nbsp();
      m.closeTd();

      m.openTd();
      m.setStyleName(S_DATA_HEADER);
      m.append(Util.C.patchTableColumnName());
      m.closeTd();

      m.openTd();
      m.setStyleName(S_DATA_HEADER);
      m.append(Util.C.patchTableColumnComments());
      m.closeTd();

      m.openTd();
      m.setStyleName(S_DATA_HEADER);
      m.setAttribute("colspan", 3);
      m.append(Util.C.patchTableColumnDiff());
      m.closeTd();

      m.closeTr();
    }

    void appendRow(final SafeHtmlBuilder m, final Patch p) {
      m.openTr();

      m.openTd();
      m.addStyleName(S_ICON_CELL);
      m.addStyleName("LeftMostCell");
      m.nbsp();
      m.closeTd();

      m.openTd();
      m.setStyleName("ChangeTypeCell");
      m.append(p.getChangeType().getCode());
      m.closeTd();

      m.openTd();
      m.addStyleName(S_DATA_CELL);
      m.addStyleName("FilePathCell");
      m.closeTd();

      m.openTd();
      m.addStyleName(S_DATA_CELL);
      m.addStyleName("CommentCell");
      if (p.getCommentCount() > 0) {
        m.append(Util.M.patchTableComments(p.getCommentCount()));
      }
      if (p.getDraftCount() > 0) {
        if (p.getCommentCount() > 0) {
          m.append(", ");
        }
        m.openSpan();
        m.setStyleName("Drafts");
        m.append(Util.M.patchTableDrafts(p.getDraftCount()));
        m.closeSpan();
      }
      m.closeTd();

      switch (p.getPatchType()) {
        case UNIFIED:
          openlink(m, 2);
          m.closeTd();
          break;

        case BINARY: {
          String base = GWT.getHostPageBaseURL();
          base += "cat/" + KeyUtil.encode(p.getKey().toString());
          switch (p.getChangeType()) {
            case DELETED:
            case MODIFIED:
              openlink(m, 1);
              m.openAnchor();
              m.setAttribute("href", base + "^1");
              m.append(Util.C.patchTableDownloadPreImage());
              closelink(m);
              break;
            default:
              emptycell(m, 1);
              break;
          }
          switch (p.getChangeType()) {
            case MODIFIED:
            case ADDED:
              openlink(m, 1);
              m.openAnchor();
              m.setAttribute("href", base + "^0");
              m.append(Util.C.patchTableDownloadPostImage());
              closelink(m);
              break;
            default:
              emptycell(m, 1);
              break;
          }
          break;
        }

        default:
          emptycell(m, 2);
          break;
      }

      openlink(m, 1);
      m.closeTd();

      m.closeTr();
    }

    private void openlink(final SafeHtmlBuilder m, final int colspan) {
      m.openTd();
      m.addStyleName(S_DATA_CELL);
      m.addStyleName("DiffLinkCell");
      m.setAttribute("colspan", colspan);
    }

    private void closelink(final SafeHtmlBuilder m) {
      m.closeAnchor();
      m.closeTd();
    }

    private void emptycell(final SafeHtmlBuilder m, final int colspan) {
      m.openTd();
      m.addStyleName(S_DATA_CELL);
      m.addStyleName("DiffLinkCell");
      m.setAttribute("colspan", colspan);
      m.nbsp();
      m.closeTd();
    }

    @Override
    protected Object getRowItemKey(final Patch item) {
      return item.getKey();
    }

    @Override
    protected void onOpenItem(final Patch item) {
      History.newItem(Link.toPatchSideBySide(item.getKey()));
    }
  }

  private final class DisplayCommand implements IncrementalCommand {
    private final MyTable table;
    private final List<Patch> list;
    private boolean attached;
    private SafeHtmlBuilder nc = new SafeHtmlBuilder();
    private int stage = 0;
    private int row;
    private double start;
    private ProgressBar meter;

    private DisplayCommand(final List<Patch> list) {
      this.table = new MyTable();
      this.list = list;
    }

    @SuppressWarnings("fallthrough")
    public boolean execute() {
      final boolean attachedNow = isAttached();
      if (!attached && attachedNow) {
        // Remember that we have been attached at least once. If
        // later we find we aren't attached we should stop running.
        //
        attached = true;
      } else if (attached && !attachedNow) {
        // If the user navigated away, we aren't in the DOM anymore.
        // Don't continue to render.
        //
        return false;
      }

      start = System.currentTimeMillis();
      switch (stage) {
        case 0:
          if (row == 0) {
            table.appendHeader(nc);
          }
          while (row < list.size()) {
            table.appendRow(nc, list.get(row));
            if ((++row % 10) == 0 && longRunning()) {
              updateMeter();
              return true;
            }
          }
          table.resetHtml(nc);
          nc = null;
          stage = 1;
          row = 0;

        case 1:
          while (row < list.size()) {
            table.initializeRow(row + 1, list.get(row));
            if ((++row % 50) == 0 && longRunning()) {
              updateMeter();
              return true;
            }
          }
          updateMeter();
          showTable();
      }
      return false;
    }

    void showTable() {
      myBody.clear();
      myBody.add(table);
      table.finishDisplay();
    }

    void initMeter() {
      if (meter == null) {
        meter = new ProgressBar(Util.M.loadingPatchSet(psid.get()));
        myBody.clear();
        myBody.add(meter);
      }
      updateMeter();
    }

    void updateMeter() {
      if (meter != null) {
        final int n = list.size();
        meter.setValue(((100 * (stage * n + row)) / (2 * n)));
      }
    }

    private boolean longRunning() {
      return System.currentTimeMillis() - start > 200;
    }
  }
}
