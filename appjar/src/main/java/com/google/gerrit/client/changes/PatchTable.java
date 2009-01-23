// Copyright 2008 Google Inc.
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
import com.google.gerrit.client.ui.DomUtil;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.ProgressMeter;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.IncrementalCommand;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;

import java.util.List;

public class PatchTable extends FancyFlexTable<Patch> {
  private PatchSet.Id psid;

  public PatchTable() {
    table.addTableListener(new TableListener() {
      public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
        if (row > 0) {
          movePointerTo(row);
        }
      }
    });
  }

  public void display(final PatchSet.Id id, final List<Patch> list) {
    psid = id;
    final DisplayCommand cmd = new DisplayCommand(list);
    if (cmd.execute()) {
      cmd.initMeter();
      DeferredCommand.addCommand(cmd);
    }
  }

  private void appendHeader(final StringBuilder nc) {
    nc.append("<tr>");
    nc.append("<td class=\"" + S_ICON_HEADER + " LeftMostCell\">&nbsp;</td>");
    nc.append("<td class=\"" + S_ICON_HEADER + "\">&nbsp;</td>");

    nc.append("<td class=\"" + S_DATA_HEADER + "\">");
    nc.append(Util.C.patchTableColumnName());
    nc.append("</td>");

    nc.append("<td class=\"" + S_DATA_HEADER + "\">");
    nc.append(Util.C.patchTableColumnComments());
    nc.append("</td>");

    nc.append("<td class=\"" + S_DATA_HEADER + "\" colspan=\"2\">");
    nc.append(Util.C.patchTableColumnDiff());
    nc.append("</td>");

    nc.append("</tr>");
  }

  private void appendRow(final StringBuilder nc, final Patch p) {
    nc.append("<tr>");
    nc.append("<td class=\"" + S_ICON_CELL + " LeftMostCell\">&nbsp;</td>");

    nc.append("<td class=\"ChangeTypeCell\">");
    nc.append(p.getChangeType().getCode());
    nc.append("</td>");

    nc.append("<td class=\"" + S_DATA_CELL + " FilePathCell\">");
    nc.append("<a href=\"#");
    if (p.getPatchType() == Patch.PatchType.UNIFIED) {
      nc.append(Link.toPatchSideBySide(p.getKey()));
    } else {
      nc.append(Link.toPatchUnified(p.getKey()));
    }
    nc.append("\">");
    nc.append(DomUtil.escape(p.getFileName()));
    nc.append("</a>");
    if (p.getSourceFileName() != null) {
      final String secondLine;
      if (p.getChangeType() == Patch.ChangeType.RENAMED) {
        secondLine = Util.M.renamedFrom(p.getSourceFileName());

      } else if (p.getChangeType() == Patch.ChangeType.COPIED) {
        secondLine = Util.M.copiedFrom(p.getSourceFileName());

      } else {
        secondLine = Util.M.otherFrom(p.getSourceFileName());
      }
      nc.append("<br>");
      nc.append("<span class=\"SourceFilePath\">");
      nc.append(DomUtil.escape(secondLine));
      nc.append("</span>");
    }
    nc.append("</td>");

    nc.append("<td class=\"" + S_DATA_CELL + " CommentCell\">");
    if (p.getCommentCount() > 0) {
      nc.append(Util.M.patchTableComments(p.getCommentCount()));
    }
    if (p.getDraftCount() > 0) {
      if (p.getCommentCount() > 0) {
        nc.append(", ");
      }
      nc.append("<span class=\"Drafts\">");
      nc.append(Util.M.patchTableDrafts(p.getDraftCount()));
      nc.append("</span>");
    }
    nc.append("</td>");

    nc.append("<td class=\"" + S_DATA_CELL + " DiffLinkCell\">");
    if (p.getPatchType() == Patch.PatchType.UNIFIED) {
      nc.append("<a href=\"#");
      nc.append(Link.toPatchSideBySide(p.getKey()));
      nc.append("\">");
      nc.append(Util.C.patchTableDiffSideBySide());
      nc.append("</a>");
    } else {
      nc.append("&nbsp;");
    }
    nc.append("</td>");

    nc.append("<td class=\"" + S_DATA_CELL + " DiffLinkCell\">");
    nc.append("<a href=\"#");
    nc.append(Link.toPatchUnified(p.getKey()));
    nc.append("\">");
    nc.append(Util.C.patchTableDiffUnified());
    nc.append("</a>");
    nc.append("</td>");

    nc.append("</tr>");
  }

  @Override
  protected Object getRowItemKey(final Patch item) {
    return item.getKey();
  }

  @Override
  protected void onOpenItem(final Patch item) {
    History.newItem(Link.toPatchSideBySide(item.getKey()));
  }

  private final class DisplayCommand implements IncrementalCommand {
    private final List<Patch> list;
    private StringBuilder nc = new StringBuilder();
    private int stage;
    private int row;
    private double start;
    private ProgressMeter meter;

    private DisplayCommand(final List<Patch> list) {
      this.list = list;
    }

    @SuppressWarnings("fallthrough")
    public boolean execute() {
      start = System.currentTimeMillis();
      switch (stage) {
        case 0:
          if (row == 0) {
            appendHeader(nc);
          }
          while (row < list.size()) {
            appendRow(nc, list.get(row));
            if ((++row % 10) == 0 && longRunning()) {
              updateMeter();
              return true;
            }
          }
          resetHtml(nc.toString());
          nc = null;
          meter = null;

          stage = 1;
          row = 0;

        case 1:
          while (row < list.size()) {
            setRowItem(row + 1, list.get(row));
            if ((++row % 10) == 0 && longRunning()) {
              return true;
            }
          }
          finishDisplay(false);
      }
      return false;
    }

    void initMeter() {
      if (meter == null) {
        resetHtml("<tr><td></td></tr>");
        meter = new ProgressMeter(Util.M.loadingPatchSet(psid.get()));
        table.setWidget(0, 0, meter);
      }
      updateMeter();
    }

    void updateMeter() {
      if (meter != null) {
        meter.setValue((100 * row / list.size()));
      }
    }

    private boolean longRunning() {
      return System.currentTimeMillis() - start > 200;
    }
  }
}
