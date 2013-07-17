// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.diff.FileInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwtexpui.progress.client.ProgressBar;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;

import java.util.Collections;
import java.util.Comparator;

class FileTable extends FlowPanel {
  private static final FileTableResources R = GWT
      .create(FileTableResources.class);

  interface FileTableResources extends ClientBundle {
    @Source("file_table.css")
    FileTableCss css();
  }

  interface FileTableCss extends CssResource {
    String pointer();
    String pathColumn();
    String deltaColumn1();
    String deltaColumn2();
    String inserted();
    String deleted();
  }

  private PatchSet.Id base;
  private PatchSet.Id curr;
  private MyTable table;
  private boolean register;

  @Override
  protected void onLoad() {
    super.onLoad();
    R.css().ensureInjected();
  }

  void setRevisions(PatchSet.Id base, PatchSet.Id curr) {
    this.base = base;
    this.curr = curr;
  }

  void setValue(NativeMap<FileInfo> fileMap) {
    JsArray<FileInfo> list = fileMap.values();
    Collections.sort(Natives.asList(list), new Comparator<FileInfo>() {
      @Override
      public int compare(FileInfo a, FileInfo b) {
        if (Patch.COMMIT_MSG.equals(a.path())) {
          return -1;
        } else if (Patch.COMMIT_MSG.equals(b.path())) {
          return 1;
        }
        return a.path().compareTo(b.path());
      }
    });

    DisplayCommand cmd = new DisplayCommand(list);
    if (cmd.execute()) {
      cmd.showProgressBar();
      Scheduler.get().scheduleIncremental(cmd);
    }
  }

  void registerKeys() {
    register = true;

    if (table != null) {
      table.setRegisterKeys(true);
    }
  }

  private void setTable(MyTable table) {
    clear();
    add(table);
    this.table = table;
    if (register) {
      table.setRegisterKeys(true);
    }
  }

  private String url(FileInfo info) {
    // TODO(sop): Switch to Dispatcher.toPatchSideBySide.
    Change.Id c = curr.getParentKey();
    StringBuilder p = new StringBuilder();
    p.append("/c/").append(c).append('/');
    if (base != null) {
      p.append(base.get()).append("..");
    }
    p.append(curr.get()).append('/').append(KeyUtil.encode(info.path()));
    p.append(info.binary() ? ",unified" : "cm");
    return p.toString();
  }

  private final class MyTable extends NavigationTable<FileInfo> {
    private final JsArray<FileInfo> list;

    MyTable(JsArray<FileInfo> list) {
      this.list = list;
      table.setWidth("");

      keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.patchTablePrev()));
      keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.patchTableNext()));
      keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.patchTableOpenDiff()));
      keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER,
          Util.C.patchTableOpenDiff()));

      setSavePointerId(
          (base != null ? base.toString() + ".." : "")
          + curr.toString());
    }

    @Override
    protected Object getRowItemKey(FileInfo item) {
      return item.path();
    }

    @Override
    protected FileInfo getRowItem(int row) {
      if (1 <= row && row <= list.length()) {
        return list.get(row - 1);
      }
      return null;
    }

    @Override
    protected void onOpenRow(int row) {
      if (1 <= row && row <= list.length()) {
        Gerrit.display(url(list.get(row - 1)));
      }
    }
  }

  private final class DisplayCommand implements RepeatingCommand {
    private final SafeHtmlBuilder sb = new SafeHtmlBuilder();
    private final MyTable table;
    private final JsArray<FileInfo> list;
    private boolean attached;
    private int row;
    private double start;
    private ProgressBar meter;

    private int inserted;
    private int deleted;

    private DisplayCommand(JsArray<FileInfo> list) {
      this.table = new MyTable(list);
      this.list = list;
    }

    public boolean execute() {
      boolean attachedNow = isAttached();
      if (!attached && attachedNow) {
        // Remember that we have been attached at least once. If
        // later we find we aren't attached we should stop running.
        attached = true;
      } else if (attached && !attachedNow) {
        // If the user navigated away, we aren't in the DOM anymore.
        // Don't continue to render.
        return false;
      }

      start = System.currentTimeMillis();
      if (row == 0) {
        header(sb);
        computeInsertedDeleted();
      }
      while (row < list.length()) {
        render(sb, list.get(row));
        if ((++row % 10) == 0 && longRunning()) {
          updateMeter();
          return true;
        }
      }
      footer(sb);
      table.resetHtml(sb);
      table.finishDisplay();
      setTable(table);
      return false;
    }

    private void computeInsertedDeleted() {
      inserted = 0;
      deleted = 0;
      for (int i = 0; i < list.length(); i++) {
        FileInfo info = list.get(i);
        if (!Patch.COMMIT_MSG.equals(info.path()) && !info.binary()) {
          inserted += info.lines_inserted();
          deleted += info.lines_deleted();
        }
      }
    }

    void showProgressBar() {
      if (meter == null) {
        meter = new ProgressBar(Util.M.loadingPatchSet(curr.get()));
        FileTable.this.clear();
        FileTable.this.add(meter);
      }
      updateMeter();
    }

    void updateMeter() {
      if (meter != null) {
        int n = list.length();
        meter.setValue((100 * row) / n);
      }
    }

    private boolean longRunning() {
      return System.currentTimeMillis() - start > 200;
    }

    private void header(SafeHtmlBuilder sb) {
      sb.openTr();
      sb.openTh().setStyleName(Gerrit.RESOURCES.css().iconCell()).closeTh();
      sb.openTh().append(Util.C.patchTableColumnName()).closeTh();
      sb.openTh()
        .setAttribute("colspan", 2)
        .append(Util.C.patchTableColumnSize())
        .closeTh();
      sb.closeTr();
    }

    private void render(SafeHtmlBuilder sb, FileInfo info) {
      sb.openTr();
      sb.openTd().setStyleName(R.css().pointer()).closeTd();
      columnPath(sb, info);
      columnDelta1(sb, info);
      columnDelta2(sb, info);
      sb.closeTr();
    }

    private void columnPath(SafeHtmlBuilder sb, FileInfo info) {
      // TODO(sop): Use JS to link, avoiding early URL update.
      sb.openTd()
        .setStyleName(R.css().pathColumn())
        .openAnchor()
        .setAttribute("href", "#" + url(info))
        .append(Patch.COMMIT_MSG.equals(info.path())
            ? Util.C.commitMessage()
            : info.path())
        .closeAnchor()
        .closeTd();
    }

    private void columnDelta1(SafeHtmlBuilder sb, FileInfo info) {
      sb.openTd().setStyleName(R.css().deltaColumn1());
      if (!Patch.COMMIT_MSG.equals(info.path()) && !info.binary()) {
        sb.append(info.lines_inserted() - info.lines_deleted());
      }
      sb.closeTd();
    }

    private void columnDelta2(SafeHtmlBuilder sb, FileInfo info) {
      sb.openTd().setStyleName(R.css().deltaColumn2());
      if (!Patch.COMMIT_MSG.equals(info.path()) && !info.binary()
          && (info.lines_inserted() != 0 || info.lines_deleted() != 0)) {
        int w = 80;
        int t = inserted + deleted;
        int i = Math.max(5, (int) (((double) w) * info.lines_inserted() / t));
        int d = Math.max(5, (int) (((double) w) * info.lines_deleted() / t));

        sb.setAttribute(
            "title",
            Util.M.patchTableSize_LongModify(info.lines_inserted(),
                info.lines_deleted()));

        if (0 < info.lines_inserted()) {
          sb.openDiv()
            .setStyleName(R.css().inserted())
            .setAttribute("style", "width:" + i + "px")
            .closeDiv();
        }
        if (0 < info.lines_deleted()) {
          sb.openDiv()
            .setStyleName(R.css().deleted())
            .setAttribute("style", "width:" + d + "px")
            .closeDiv();
        }
      }
      sb.closeTd();
    }

    private void footer(SafeHtmlBuilder sb) {
      sb.openTr();
      sb.openTd().setStyleName(Gerrit.RESOURCES.css().iconCell()).closeTd();
      sb.openTd().closeTd(); // path

      // delta1
      sb.openTh().setStyleName(R.css().deltaColumn1())
        .append(Util.M.patchTableSize_Modify(inserted, deleted))
        .closeTh();

      // delta2
      sb.openTh().setStyleName(R.css().deltaColumn2());
      int w = 80;
      int t = inserted + deleted;
      int i = Math.max(1, (int) (((double) w) * inserted / t));
      int d = Math.max(1, (int) (((double) w) * deleted / t));
      if (i + d > w && i > d) {
        i = w - d;
      } else if (i + d > w && d > i) {
        d = w - i;
      }
      if (0 < inserted) {
        sb.openDiv()
        .setStyleName(R.css().inserted())
        .setAttribute("style", "width:" + i + "px")
        .closeDiv();
      }
      if (0 < deleted) {
        sb.openDiv()
          .setStyleName(R.css().deleted())
          .setAttribute("style", "width:" + d + "px")
          .closeDiv();
      }
      sb.closeTh();

      sb.closeTr();
    }
  }
}
