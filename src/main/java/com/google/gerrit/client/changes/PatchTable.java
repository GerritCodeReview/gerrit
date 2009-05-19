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
import com.google.gerrit.client.Link;
import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.ui.DirectScreenLink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.IncrementalCommand;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwtexpui.progress.client.ProgressBar;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;

import java.util.List;

public class PatchTable extends Composite {
  private static final EventListener patchLinkClick = new EventListener() {
    @Override
    public void onBrowserEvent(final Event event) {
      final Element elem = event.getCurrentEventTarget().cast();
      final PatchTable table = getPatchTable(elem);
      if (table != null) {
        final String token = elem.getAttribute("href").substring(1); // skip "#"
        final Screen screen = dispatch(token, table);
        if (screen != null) {
          event.stopPropagation();
          event.preventDefault();
          Gerrit.display(token, screen);
        }
      }
    }
  };

  private static Screen dispatch(final String token, final PatchTable table) {
    String p;

    p = "patch,sidebyside,";
    if (token.startsWith(p))
      return new PatchScreen.SideBySide(parse(token, p), table);

    p = "patch,unified,";
    if (token.startsWith(p))
      new PatchScreen.SideBySide(parse(token, p), table);

    GWT.log("Unexpected history token: " + token, null);
    return null;
  }

  private static Patch.Key parse(final String token, String prefix) {
    return Patch.Key.parse(token.substring(prefix.length()));
  }

  private static PatchTable getPatchTable(final Element a) {
    Element obj = DOM.getParent(a);
    while (obj != null) {
      if (DOM.getEventListener(obj) instanceof PatchTable) {
        return (PatchTable) DOM.getEventListener(obj);
      }
      obj = DOM.getParent(obj);
    }
    return null;
  }

  private final FlowPanel myBody;
  private PatchSet.Id psid;
  private Command onLoadCommand;
  private MyTable myTable;
  private String savePointerId;

  public PatchTable() {
    myBody = new FlowPanel();
    initWidget(myBody);
  }

  public void display(final PatchSet.Id id, final List<Patch> list) {
    psid = id;
    myTable = null;

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

  public boolean isLoaded() {
    return myTable != null;
  }

  public void onTableLoaded(final Command cmd) {
    if (myTable != null) {
      cmd.execute();
    } else {
      onLoadCommand = cmd;
    }
  }

  public void setRegisterKeys(final boolean on) {
    myTable.setRegisterKeys(on);
  }

  public void movePointerTo(final Patch.Key k) {
    myTable.movePointerTo(k);
  }

  public void notifyDraftDelta(final Patch.Key k, final int delta) {
    if (myTable != null) {
      myTable.notifyDraftDelta(k, delta);
    }
  }

  private class MyTable extends NavigationTable<Patch> {
    private static final int C_PATH = 2;
    private static final int C_DRAFT = 3;

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

    void notifyDraftDelta(final Patch.Key key, final int delta) {
      final int row = findRow(key);
      if (0 <= row) {
        final Patch p = getRowItem(row);
        if (p != null) {
          p.setDraftCount(p.getDraftCount() + delta);
          final SafeHtmlBuilder m = new SafeHtmlBuilder();
          appendCommentCount(m, p);
          SafeHtml.set(table, row, C_DRAFT, m);
        }
      }
    }

    @Override
    public void resetHtml(final SafeHtml html) {
      super.resetHtml(html);
    }

    @Override
    public void movePointerTo(Object oldId) {
      super.movePointerTo(oldId);
    }

    void initializeRow(final int row, final Patch item) {
      setRowItem(row, item);
      link(DOM.getParent(table.getCellFormatter().getElement(row, C_PATH)));
    }

    void link(final Element tr) {
      final NodeList<com.google.gwt.dom.client.Element> list =
          tr.getElementsByTagName("a");
      for (int i = 0; i < list.getLength(); i++) {
        final Element a = list.getItem(i).cast();
        final String href = a.getAttribute("href");
        if (href.startsWith("#patch,")) {
          DOM.sinkEvents(a, Event.getTypeInt(ClickEvent.getType().getName()));
          DOM.setEventListener(a, patchLinkClick);
        }
      }
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
      appendCommentCount(m, p);
      m.closeTd();

      switch (p.getPatchType()) {
        case UNIFIED:
          openlink(m, 2);
          m.setAttribute("href", "#" + Link.toPatchSideBySide(p.getKey()));
          m.append(Util.C.patchTableDiffSideBySide());
          closelink(m);
          break;

        case BINARY: {
          String base = GWT.getHostPageBaseURL();
          base += "cat/" + KeyUtil.encode(p.getKey().toString());
          switch (p.getChangeType()) {
            case DELETED:
            case MODIFIED:
              openlink(m, 1);
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
      m.setAttribute("href", "#" + Link.toPatchUnified(p.getKey()));
      m.append(Util.C.patchTableDiffUnified());
      closelink(m);

      m.closeTr();
    }

    void appendCommentCount(final SafeHtmlBuilder m, final Patch p) {
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
    }

    private void openlink(final SafeHtmlBuilder m, final int colspan) {
      m.openTd();
      m.addStyleName(S_DATA_CELL);
      m.addStyleName("DiffLinkCell");
      m.setAttribute("colspan", colspan);
      m.openAnchor();
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
    protected void onOpenRow(final int row) {
      Widget link = table.getWidget(row, C_PATH);
      if (link instanceof FlowPanel) {
        link = ((FlowPanel) link).getWidget(0);
      }
      if (link instanceof DirectScreenLink) {
        ((DirectScreenLink) link).go();
      }
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
      PatchTable.this.myBody.clear();
      PatchTable.this.myBody.add(table);
      PatchTable.this.myTable = table;
      table.finishDisplay();
      if (PatchTable.this.onLoadCommand != null) {
        PatchTable.this.onLoadCommand.execute();
        PatchTable.this.onLoadCommand = null;
      }
    }

    void initMeter() {
      if (meter == null) {
        meter = new ProgressBar(Util.M.loadingPatchSet(psid.get()));
        PatchTable.this.myBody.clear();
        PatchTable.this.myBody.add(meter);
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
