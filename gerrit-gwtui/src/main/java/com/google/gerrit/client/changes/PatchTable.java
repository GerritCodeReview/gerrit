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
import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.PatchLink;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.Patch.ChangeType;
import com.google.gerrit.reviewdb.Patch.Key;
import com.google.gerrit.reviewdb.Patch.PatchType;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.progress.client.ProgressBar;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;

import java.util.ArrayList;
import java.util.List;

public class PatchTable extends Composite {
  private final FlowPanel myBody;
  private PatchSetDetail detail;
  private Command onLoadCommand;
  private MyTable myTable;
  private String savePointerId;
  private List<Patch> patchList;
  private ListenableAccountDiffPreference listenablePrefs;

  private List<ClickHandler> clickHandlers;
  private boolean active;
  private boolean registerKeys;

  public PatchTable(ListenableAccountDiffPreference prefs) {
    listenablePrefs = prefs;
    myBody = new FlowPanel();
    initWidget(myBody);
  }

  public PatchTable() {
    this(new ListenableAccountDiffPreference());
  }

  public int indexOf(Patch.Key patch) {
    for (int i = 0; i < patchList.size(); i++) {
      if (patchList.get(i).getKey().equals(patch)) {
        return i;
      }
    }
    return -1;
  }

  public void display(PatchSetDetail detail) {
    this.detail = detail;
    this.patchList = detail.getPatches();
    myTable = null;

    final DisplayCommand cmd = new DisplayCommand(patchList);
    if (cmd.execute()) {
      cmd.initMeter();
      Scheduler.get().scheduleIncremental(cmd);
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

  public void addClickHandler(final ClickHandler clickHandler) {
    if (myTable != null) {
      myTable.addClickHandler(clickHandler);
    } else {
      if (clickHandlers == null) {
        clickHandlers = new ArrayList<ClickHandler>(2);
      }
      clickHandlers.add(clickHandler);
    }
  }

  public void setRegisterKeys(final boolean on) {
    registerKeys = on;
    if (myTable != null) {
      myTable.setRegisterKeys(on);
    }
  }

  public void movePointerTo(final Patch.Key k) {
    myTable.movePointerTo(k);
  }

  public void setActive(boolean active) {
    this.active = active;
    if (myTable != null) {
      myTable.setActive(active);
    }
  }

  public void notifyDraftDelta(final Patch.Key k, final int delta) {
    if (myTable != null) {
      myTable.notifyDraftDelta(k, delta);
    }
  }

  private void setMyTable(MyTable table) {
    myBody.clear();
    myBody.add(table);
    myTable = table;

    if (clickHandlers != null) {
      for (ClickHandler ch : clickHandlers) {
        myTable.addClickHandler(ch);
      }
      clickHandlers = null;
    }

    if (active) {
      myTable.setActive(true);
      active = false;
    }

    if (registerKeys) {
      myTable.setRegisterKeys(registerKeys);
      registerKeys = false;
    }

    myTable.finishDisplay();
  }

  /**
   * @return a link to the previous file in this patch set, or null.
   */
  public InlineHyperlink getPreviousPatchLink(int index, PatchScreen.Type patchType) {
    for(index--; index > -1; index--) {
      InlineHyperlink link = createLink(index, patchType, SafeHtml.asis(Util.C
          .prevPatchLinkIcon()), null);
      if (link != null) {
        return link;
      }
    }
    return null;
  }

  /**
   * @return a link to the next file in this patch set, or null.
   */
  public InlineHyperlink getNextPatchLink(int index, PatchScreen.Type patchType) {
    for(index++; index < patchList.size(); index++) {
      InlineHyperlink link = createLink(index, patchType, null, SafeHtml.asis(Util.C
          .nextPatchLinkIcon()));
      if (link != null) {
        return link;
      }
    }
    return null;
  }

  /**
   * @return a link to the the given patch.
   * @param index The patch to link to
   * @param patchType The type of patch display
   * @param before A string to display at the beginning of the href text
   * @param after A string to display at the end of the href text
   */
  private PatchLink createLink(int index, PatchScreen.Type patchType,
      SafeHtml before, SafeHtml after) {
    Patch patch = patchList.get(index);
    if (( listenablePrefs.get().isSkipDeleted() &&
          patch.getChangeType().equals(ChangeType.DELETED) )
        ||
        ( listenablePrefs.get().isSkipUncommented() &&
          patch.getCommentCount() == 0 ) ) {
      return null;
    }

    Key thisKey = patch.getKey();
    PatchLink link;
    if (patchType == PatchScreen.Type.SIDE_BY_SIDE
        && patch.getPatchType() == Patch.PatchType.UNIFIED) {
      link = new PatchLink.SideBySide("", thisKey, index, detail, this);
    } else {
      link = new PatchLink.Unified("", thisKey, index, detail, this);
    }
    SafeHtmlBuilder text = new SafeHtmlBuilder();
    text.append(before);
    text.append(getFileNameOnly(patch));
    text.append(after);
    SafeHtml.set(link, text);
    return link;
  }

  private static String getFileNameOnly(Patch patch) {
    // Note: use '/' here and not File.pathSeparator since git paths
    // are always separated by /
    //
    String fileName = getDisplayFileName(patch);
    int s = fileName.lastIndexOf('/');
    if (s >= 0) {
      fileName = fileName.substring(s + 1);
    }
    return fileName;
  }

  public static String getDisplayFileName(Patch patch) {
    return getDisplayFileName(patch.getKey());
  }

  public static String getDisplayFileName(Patch.Key patchKey) {
    if (Patch.COMMIT_MSG.equals(patchKey.get())) {
      return Util.C.commitMessage();
    }
    return patchKey.get();
  }

  /**
   * Update the reviewed status for the given patch.
   */
  public void updateReviewedStatus(Patch.Key patchKey, boolean reviewed) {
    if (myTable != null) {
      myTable.updateReviewedStatus(patchKey, reviewed);
    }
  }

  public ListenableAccountDiffPreference getPreferences() {
    return listenablePrefs;
  }

  public void setPreferences(ListenableAccountDiffPreference prefs) {
    listenablePrefs = prefs;
  }

  private class MyTable extends NavigationTable<Patch> {
    private static final int C_PATH = 2;
    private static final int C_DRAFT = 3;
    private static final int C_SIZE = 4;
    private static final int C_SIDEBYSIDE = 5;
    private int activeRow = -1;

    MyTable() {
      keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.patchTablePrev()));
      keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.patchTableNext()));
      keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.patchTableOpenDiff()));
      keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
          .patchTableOpenDiff()));
      keysNavigation.add(new OpenUnifiedDiffKeyCommand(0, 'O', Util.C
          .patchTableOpenUnifiedDiff()));

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

    public void addClickHandler(final ClickHandler clickHandler) {
      table.addClickHandler(clickHandler);
    }

    void updateReviewedStatus(final Patch.Key patchKey, boolean reviewed) {
      final int row = findRow(patchKey);
      if (0 <= row) {
        final Patch patch = getRowItem(row);
        if (patch != null) {
          patch.setReviewedByCurrentUser(reviewed);

          int col = C_SIDEBYSIDE + 2;
          if (patch.getPatchType() == Patch.PatchType.BINARY) {
            col = C_SIDEBYSIDE + 3;
          }

          if (reviewed) {
            table.setWidget(row, col, new Image(Gerrit.RESOURCES.greenCheck()));
          } else {
            table.clearCell(row, col);
          }
        }
      }
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

    /** Activates / Deactivates the key navigation and the highlighting of the current row for this table */
    public void setActive(boolean active) {
      if (active) {
        if(activeRow > 0 && getCurrentRow() != activeRow) {
          super.movePointerTo(activeRow);
          activeRow = -1;
        }
      } else {
        if(getCurrentRow() > 0) {
          activeRow = getCurrentRow();
          super.movePointerTo(-1);
        }
      }
      setRegisterKeys(active);
    }

    void initializeRow(int row) {
      Patch patch = PatchTable.this.patchList.get(row - 1);
      setRowItem(row, patch);

      Widget nameCol;
      if (patch.getPatchType() == Patch.PatchType.UNIFIED) {
        nameCol =
            new PatchLink.SideBySide(getDisplayFileName(patch), patch.getKey(),
                row - 1, detail, PatchTable.this);
      } else {
        nameCol =
            new PatchLink.Unified(getDisplayFileName(patch), patch.getKey(),
                row - 1, detail, PatchTable.this);
      }
      if (patch.getSourceFileName() != null) {
        final String text;
        if (patch.getChangeType() == Patch.ChangeType.RENAMED) {
          text = Util.M.renamedFrom(patch.getSourceFileName());
        } else if (patch.getChangeType() == Patch.ChangeType.COPIED) {
          text = Util.M.copiedFrom(patch.getSourceFileName());
        } else {
          text = Util.M.otherFrom(patch.getSourceFileName());
        }
        final Label line = new Label(text);
        line.setStyleName(Gerrit.RESOURCES.css().sourceFilePath());
        final FlowPanel cell = new FlowPanel();
        cell.add(nameCol);
        cell.add(line);
        nameCol = cell;
      }
      table.setWidget(row, C_PATH, nameCol);

      int C_UNIFIED = C_SIDEBYSIDE + 1;
      if (patch.getPatchType() == Patch.PatchType.UNIFIED) {
        table.setWidget(row, C_SIDEBYSIDE, new PatchLink.SideBySide(Util.C
            .patchTableDiffSideBySide(), patch.getKey(), row - 1, detail,
            PatchTable.this));

      } else if (patch.getPatchType() == Patch.PatchType.BINARY) {
        C_UNIFIED = C_SIDEBYSIDE + 2;
      }
      table.setWidget(row, C_UNIFIED, new PatchLink.Unified(Util.C
          .patchTableDiffUnified(), patch.getKey(), row - 1, detail,
          PatchTable.this));
    }

    void appendHeader(final SafeHtmlBuilder m) {
      m.openTr();

      // Cursor
      m.openTd();
      m.addStyleName(Gerrit.RESOURCES.css().iconHeader());
      m.addStyleName(Gerrit.RESOURCES.css().leftMostCell());
      m.nbsp();
      m.closeTd();

      // Mode
      m.openTd();
      m.setStyleName(Gerrit.RESOURCES.css().iconHeader());
      m.nbsp();
      m.closeTd();

      // "File path"
      m.openTd();
      m.setStyleName(Gerrit.RESOURCES.css().dataHeader());
      m.append(Util.C.patchTableColumnName());
      m.closeTd();

      // "Comments"
      m.openTd();
      m.setStyleName(Gerrit.RESOURCES.css().dataHeader());
      m.append(Util.C.patchTableColumnComments());
      m.closeTd();

      // "Size"
      m.openTd();
      m.setStyleName(Gerrit.RESOURCES.css().dataHeader());
      m.append(Util.C.patchTableColumnSize());
      m.closeTd();

      // "Diff"
      m.openTd();
      m.setStyleName(Gerrit.RESOURCES.css().dataHeader());
      m.setAttribute("colspan", 3);
      m.append(Util.C.patchTableColumnDiff());
      m.closeTd();

      // "Reviewed"
      if (Gerrit.isSignedIn()) {
        m.openTd();
        m.setStyleName(Gerrit.RESOURCES.css().iconHeader());
        m.addStyleName(Gerrit.RESOURCES.css().dataHeader());
        m.append(Util.C.reviewed());
        m.closeTd();
      }

      m.closeTr();
    }

    void appendRow(final SafeHtmlBuilder m, final Patch p) {
      m.openTr();

      m.openTd();
      m.addStyleName(Gerrit.RESOURCES.css().iconCell());
      m.addStyleName(Gerrit.RESOURCES.css().leftMostCell());
      m.nbsp();
      m.closeTd();

      m.openTd();
      m.setStyleName(Gerrit.RESOURCES.css().changeTypeCell());
      if (Patch.COMMIT_MSG.equals(p.getFileName())) {
        m.nbsp();
      } else {
        m.append(p.getChangeType().getCode());
      }
      m.closeTd();

      m.openTd();
      m.addStyleName(Gerrit.RESOURCES.css().dataCell());
      m.addStyleName(Gerrit.RESOURCES.css().filePathCell());
      m.closeTd();

      m.openTd();
      m.addStyleName(Gerrit.RESOURCES.css().dataCell());
      m.addStyleName(Gerrit.RESOURCES.css().commentCell());
      appendCommentCount(m, p);
      m.closeTd();

      m.openTd();
      m.addStyleName(Gerrit.RESOURCES.css().dataCell());
      m.addStyleName(Gerrit.RESOURCES.css().patchSizeCell());
      appendSize(m, p);
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

      // Green check mark if the user is logged in and they reviewed that file
      if (Gerrit.isSignedIn()) {
        m.openTd();
        m.setStyleName(Gerrit.RESOURCES.css().dataCell());
        if (p.isReviewedByCurrentUser()) {
          m.openDiv();
          m.setStyleName(Gerrit.RESOURCES.css().greenCheckClass());
          m.closeSelf();
        }
        m.closeTd();
      }

      m.closeTr();
    }

    void appendTotals(final SafeHtmlBuilder m, int ins, int dels) {
      m.openTr();

      m.openTd();
      m.addStyleName(Gerrit.RESOURCES.css().iconCell());
      m.addStyleName(Gerrit.RESOURCES.css().noborder());
      m.nbsp();
      m.closeTd();

      m.openTd();
      m.setAttribute("colspan", C_SIZE - 1);
      m.closeTd();

      m.openTd();
      m.addStyleName(Gerrit.RESOURCES.css().dataCell());
      m.addStyleName(Gerrit.RESOURCES.css().patchSizeCell());
      m.addStyleName(Gerrit.RESOURCES.css().leftMostCell());
      m.append(Util.M.patchTableSize_Modify(ins, dels));
      m.closeTd();

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
        m.setStyleName(Gerrit.RESOURCES.css().drafts());
        m.append(Util.M.patchTableDrafts(p.getDraftCount()));
        m.closeSpan();
      }
    }

    void appendSize(final SafeHtmlBuilder m, final Patch p) {
      if (Patch.COMMIT_MSG.equals(p.getFileName())) {
        m.nbsp();
        return;
      }

      if (p.getPatchType() == PatchType.UNIFIED) {
        int ins = p.getInsertions();
        int dels = p.getDeletions();

        switch (p.getChangeType()) {
          case ADDED:
            m.append(Util.M.patchTableSize_Lines(ins));
            break;
          case DELETED:
            m.nbsp();
            break;
          case MODIFIED:
          case COPIED:
          case RENAMED:
            m.append(Util.M.patchTableSize_Modify(ins, dels));
            break;
        }
      } else {
        m.nbsp();
      }
    }

    private void openlink(final SafeHtmlBuilder m, final int colspan) {
      m.openTd();
      m.addStyleName(Gerrit.RESOURCES.css().dataCell());
      m.addStyleName(Gerrit.RESOURCES.css().diffLinkCell());
      m.setAttribute("colspan", colspan);
    }

    private void closelink(final SafeHtmlBuilder m) {
      m.closeAnchor();
      m.closeTd();
    }

    private void emptycell(final SafeHtmlBuilder m, final int colspan) {
      m.openTd();
      m.addStyleName(Gerrit.RESOURCES.css().dataCell());
      m.addStyleName(Gerrit.RESOURCES.css().diffLinkCell());
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
      if (link instanceof InlineHyperlink) {
        ((InlineHyperlink) link).go();
      }
    }

    private final class OpenUnifiedDiffKeyCommand extends KeyCommand {

      public OpenUnifiedDiffKeyCommand(int mask, char key, String help) {
        super(mask, key, help);
      }

      @Override
      public void onKeyPress(KeyPressEvent event) {
        Widget link = table.getWidget(getCurrentRow(), C_PATH);
        if (link instanceof FlowPanel) {
          link = ((FlowPanel) link).getWidget(0);
        }
        if (link instanceof PatchLink.Unified) {
          ((InlineHyperlink) link).go();
        } else {
          link = table.getWidget(getCurrentRow(), C_SIDEBYSIDE + 1);
          if (link instanceof PatchLink.Unified) {
            ((InlineHyperlink) link).go();
          }
        }
      }
    }
  }

  private final class DisplayCommand implements RepeatingCommand {
    private final MyTable table;
    private final List<Patch> list;
    private boolean attached;
    private SafeHtmlBuilder nc = new SafeHtmlBuilder();
    private int stage = 0;
    private int row;
    private double start;
    private ProgressBar meter;

    private int insertions;
    private int deletions;

    private DisplayCommand(final List<Patch> list) {
      this.table = new MyTable();
      this.list = list;
    }

    /**
     * Add the files contained in the list of patches to the table, one per row.
     */
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
            table.appendRow(nc, list.get(row++));
          }
          while (row < list.size()) {
            Patch p = list.get(row);
            insertions += p.getInsertions();
            deletions += p.getDeletions();
            table.appendRow(nc, p);
            if ((++row % 10) == 0 && longRunning()) {
              updateMeter();
              return true;
            }
          }
          table.appendTotals(nc, insertions, deletions);
          table.resetHtml(nc);
          nc = null;
          stage = 1;
          row = 0;

        case 1:
          while (row < list.size()) {
            table.initializeRow(row + 1);
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
      setMyTable(table);

      if (PatchTable.this.onLoadCommand != null) {
        PatchTable.this.onLoadCommand.execute();
        PatchTable.this.onLoadCommand = null;
      }
    }

    void initMeter() {
      if (meter == null) {
        meter = new ProgressBar(Util.M.loadingPatchSet(detail.getPatchSet().getId().get()));
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
