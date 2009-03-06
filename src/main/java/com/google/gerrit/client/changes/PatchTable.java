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
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.IncrementalCommand;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwtexpui.progress.client.ProgressBar;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

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

  private class MyTable extends FancyFlexTable<Patch> {
    MyTable() {
      table.addTableListener(new TableListener() {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
          if (row > 0) {
            movePointerTo(row);
          }
        }
      });
      setSavePointerId(PatchTable.this.savePointerId);
    }

    @Override
    public void resetHtml(final SafeHtml html) {
      super.resetHtml(html);
    }

    @Override
    public void setRowItem(final int row, final Patch p) {
      super.setRowItem(row, p);
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
      m.setAttribute("colspan", 2);
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

      m.openAnchor();
      if (p.getPatchType() == Patch.PatchType.UNIFIED) {
        m.setAttribute("href", "#" + Link.toPatchSideBySide(p.getKey()));
      } else {
        m.setAttribute("href", "#" + Link.toPatchUnified(p.getKey()));
      }
      m.append(p.getFileName());
      m.closeAnchor();

      if (p.getSourceFileName() != null) {
        final String secondLine;
        if (p.getChangeType() == Patch.ChangeType.RENAMED) {
          secondLine = Util.M.renamedFrom(p.getSourceFileName());

        } else if (p.getChangeType() == Patch.ChangeType.COPIED) {
          secondLine = Util.M.copiedFrom(p.getSourceFileName());

        } else {
          secondLine = Util.M.otherFrom(p.getSourceFileName());
        }
        m.br();
        m.openSpan();
        m.setStyleName("SourceFilePath");
        m.append(secondLine);
        m.closeSpan();
      }
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

      m.openTd();
      m.addStyleName(S_DATA_CELL);
      m.addStyleName("DiffLinkCell");
      if (p.getPatchType() == Patch.PatchType.UNIFIED) {
        m.openAnchor();
        m.setAttribute("href", "#" + Link.toPatchSideBySide(p.getKey()));
        m.append(Util.C.patchTableDiffSideBySide());
        m.closeAnchor();
      } else {
        m.nbsp();
      }
      m.closeTd();

      m.openTd();
      m.addStyleName(S_DATA_CELL);
      m.addStyleName("DiffLinkCell");
      m.openAnchor();
      m.setAttribute("href", "#" + Link.toPatchUnified(p.getKey()));
      m.append(Util.C.patchTableDiffUnified());
      m.closeAnchor();
      m.closeTd();

      m.closeTr();
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
            table.setRowItem(row + 1, list.get(row));
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
      table.finishDisplay(false);
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
