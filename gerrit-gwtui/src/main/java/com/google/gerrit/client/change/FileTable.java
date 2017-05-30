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

import static com.google.gerrit.client.FormatUtil.formatAbsBytes;
import static com.google.gerrit.client.FormatUtil.formatAbsPercentage;
import static com.google.gerrit.client.FormatUtil.formatBytes;
import static com.google.gerrit.client.FormatUtil.formatPercentage;

import com.google.gerrit.client.DiffObject;
import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeEditApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.ReviewInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.FileInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.impl.HyperlinkImpl;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.progress.client.ProgressBar;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import java.sql.Timestamp;

public class FileTable extends FlowPanel {
  private static final FileTableResources R = GWT.create(FileTableResources.class);

  interface FileTableResources extends ClientBundle {
    @Source("file_table.css")
    FileTableCss css();
  }

  interface FileTableCss extends CssResource {
    String table();

    String nohover();

    String pointer();

    String reviewed();

    String status();

    String pathColumn();

    String commonPrefix();

    String renameCopySource();

    String draftColumn();

    String newColumn();

    String commentColumn();

    String deltaColumn1();

    String deltaColumn2();

    String inserted();

    String deleted();

    String restoreDelete();

    String error();
  }

  public enum Mode {
    REVIEW,
    EDIT
  }

  private static final String DELETE;
  private static final String RESTORE;
  private static final String REVIEWED;
  private static final String OPEN;
  private static final int C_PATH = 3;
  private static final HyperlinkImpl link = GWT.create(HyperlinkImpl.class);

  static {
    DELETE = DOM.createUniqueId().replace('-', '_');
    RESTORE = DOM.createUniqueId().replace('-', '_');
    REVIEWED = DOM.createUniqueId().replace('-', '_');
    OPEN = DOM.createUniqueId().replace('-', '_');
    init(DELETE, RESTORE, REVIEWED, OPEN);
  }

  private static native void init(String d, String t, String r, String o) /*-{
    $wnd[d] = $entry(function(e,i) {
      @com.google.gerrit.client.change.FileTable::onDelete(Lcom/google/gwt/dom/client/NativeEvent;I)(e,i)
    });
    $wnd[t] = $entry(function(e,i) {
      @com.google.gerrit.client.change.FileTable::onRestore(Lcom/google/gwt/dom/client/NativeEvent;I)(e,i)
    });
    $wnd[r] = $entry(function(e,i) {
      @com.google.gerrit.client.change.FileTable::onReviewed(Lcom/google/gwt/dom/client/NativeEvent;I)(e,i)
    });
    $wnd[o] = $entry(function(e,i) {
      return @com.google.gerrit.client.change.FileTable::onOpen(Lcom/google/gwt/dom/client/NativeEvent;I)(e,i);
    });
  }-*/;

  private static void onDelete(NativeEvent e, int idx) {
    MyTable t = getMyTable(e);
    if (t != null) {
      t.onDelete(idx);
    }
  }

  private static boolean onRestore(NativeEvent e, int idx) {
    MyTable t = getMyTable(e);
    if (t != null) {
      t.onRestore(idx);
      e.preventDefault();
      e.stopPropagation();
      return false;
    }
    return true;
  }

  private static void onReviewed(NativeEvent e, int idx) {
    MyTable t = getMyTable(e);
    if (t != null) {
      t.onReviewed(InputElement.as(Element.as(e.getEventTarget())), idx);
    }
  }

  private static boolean onOpen(NativeEvent e, int idx) {
    if (link.handleAsClick(e.<Event>cast())) {
      MyTable t = getMyTable(e);
      if (t != null) {
        t.onOpenRow(1 + idx);
        e.preventDefault();
        e.stopPropagation();
        return false;
      }
    }
    return true;
  }

  private static MyTable getMyTable(NativeEvent event) {
    Element e = event.getEventTarget().cast();
    for (e = DOM.getParent(e); e != null; e = DOM.getParent(e)) {
      EventListener l = DOM.getEventListener(e);
      if (l instanceof MyTable) {
        return (MyTable) l;
      }
    }
    return null;
  }

  private DiffObject base;
  private PatchSet.Id curr;
  private Project.NameKey project;
  private MyTable table;
  private boolean register;
  private JsArrayString reviewed;
  private String scrollToPath;
  private ChangeScreen.Style style;
  private Widget replyButton;
  private boolean editExists;
  private Mode mode;

  @Override
  protected void onLoad() {
    super.onLoad();
    R.css().ensureInjected();
  }

  public void set(
      DiffObject base,
      PatchSet.Id curr,
      Project.NameKey project,
      ChangeScreen.Style style,
      Widget replyButton,
      Mode mode,
      boolean editExists) {
    this.base = base;
    this.curr = curr;
    this.project = project;
    this.style = style;
    this.replyButton = replyButton;
    this.mode = mode;
    this.editExists = editExists;
  }

  void setValue(
      NativeMap<FileInfo> fileMap,
      Timestamp myLastReply,
      @Nullable NativeMap<JsArray<CommentInfo>> comments,
      @Nullable NativeMap<JsArray<CommentInfo>> drafts) {
    JsArray<FileInfo> list = fileMap.values();
    FileInfo.sortFileInfoByPath(list);

    DisplayCommand cmd = new DisplayCommand(fileMap, list, myLastReply, comments, drafts);
    if (cmd.execute()) {
      cmd.showProgressBar();
      Scheduler.get().scheduleIncremental(cmd);
    }
  }

  void showError(Throwable t) {
    clear();
    Label l = new Label(Resources.M.failedToLoadFileList(t.getMessage()));
    add(l);
    l.setStyleName(R.css().error());
  }

  void markReviewed(JsArrayString reviewed) {
    if (table != null) {
      table.markReviewed(reviewed);
    } else {
      this.reviewed = reviewed;
    }
  }

  void unregisterKeys() {
    register = false;

    if (table != null) {
      table.setRegisterKeys(false);
    }
  }

  void registerKeys() {
    register = true;

    if (table != null) {
      table.setRegisterKeys(true);
    }
  }

  void scrollToPath(String path) {
    if (table != null) {
      table.scrollToPath(path);
    } else {
      scrollToPath = path;
    }
  }

  void openAll() {
    if (table != null) {
      String self = Gerrit.selfRedirect(null);
      for (FileInfo info : Natives.asList(table.list)) {
        if (canOpen(info.path())) {
          Window.open(self + "#" + url(info), "_blank", null);
        }
      }
    }
  }

  private boolean canOpen(String path) {
    return mode != Mode.EDIT || !Patch.isMagic(path) || Patch.COMMIT_MSG.equals(path);
  }

  private void setTable(MyTable table) {
    clear();
    add(table);
    this.table = table;

    if (register) {
      table.setRegisterKeys(true);
    }
    if (reviewed != null) {
      table.markReviewed(reviewed);
      reviewed = null;
    }
    if (scrollToPath != null) {
      table.scrollToPath(scrollToPath);
      scrollToPath = null;
    }
  }

  private String url(FileInfo info) {
    return info.binary()
        ? Dispatcher.toUnified(project, base, curr, info.path())
        : mode == Mode.REVIEW
            ? Dispatcher.toPatch(project, base, curr, info.path())
            : Dispatcher.toEditScreen(project, curr, info.path());
  }

  private final class MyTable extends NavigationTable<FileInfo> {
    private final NativeMap<FileInfo> map;
    private final JsArray<FileInfo> list;

    MyTable(NativeMap<FileInfo> map, JsArray<FileInfo> list) {
      this.map = map;
      this.list = list;
      table.setWidth("");

      keysNavigation.add(
          new PrevKeyCommand(0, 'k', Util.C.patchTablePrev()),
          new NextKeyCommand(0, 'j', Util.C.patchTableNext()));
      keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.patchTableOpenDiff()));
      keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C.patchTableOpenDiff()));

      keysNavigation.add(
          new OpenFileCommand(list.length() - 1, 0, '[', Resources.C.openLastFile()),
          new OpenFileCommand(0, 0, ']', Resources.C.openCommitMessage()));

      keysAction.add(
          new KeyCommand(0, 'r', PatchUtil.C.toggleReviewed()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              int row = getCurrentRow();
              if (1 <= row && row <= MyTable.this.list.length()) {
                FileInfo info = MyTable.this.list.get(row - 1);
                InputElement b = getReviewed(info);
                boolean c = !b.isChecked();
                setReviewed(info, c);
                b.setChecked(c);
              }
            }
          });

      setSavePointerId((!base.isBase() ? base.asString() + ".." : "") + curr.toString());
    }

    void onDelete(int idx) {
      String path = list.get(idx).path();
      ChangeEditApi.delete(
          Project.NameKey.asStringOrNull(project),
          curr.getParentKey().get(),
          path,
          new AsyncCallback<VoidResult>() {
            @Override
            public void onSuccess(VoidResult result) {
              Gerrit.display(PageLinks.toChangeInEditMode(project, curr.getParentKey()));
            }

            @Override
            public void onFailure(Throwable caught) {}
          });
    }

    void onRestore(int idx) {
      String path = list.get(idx).path();
      ChangeEditApi.restore(
          Project.NameKey.asStringOrNull(project),
          curr.getParentKey().get(),
          path,
          new AsyncCallback<VoidResult>() {
            @Override
            public void onSuccess(VoidResult result) {
              Gerrit.display(PageLinks.toChangeInEditMode(project, curr.getParentKey()));
            }

            @Override
            public void onFailure(Throwable caught) {}
          });
    }

    void onReviewed(InputElement checkbox, int idx) {
      setReviewed(list.get(idx), checkbox.isChecked());
    }

    private void setReviewed(FileInfo info, boolean r) {
      RestApi api =
          ChangeApi.revision(Project.NameKey.asStringOrNull(project), curr)
              .view("files")
              .id(info.path())
              .view("reviewed");
      if (r) {
        api.put(CallbackGroup.<ReviewInfo>emptyCallback());
      } else {
        api.delete(CallbackGroup.<ReviewInfo>emptyCallback());
      }
    }

    void markReviewed(JsArrayString reviewed) {
      for (int i = 0; i < reviewed.length(); i++) {
        FileInfo info = map.get(reviewed.get(i));
        if (info != null) {
          getReviewed(info).setChecked(true);
        }
      }
    }

    private InputElement getReviewed(FileInfo info) {
      CellFormatter fmt = table.getCellFormatter();
      Element e = fmt.getElement(1 + info._row(), 1);
      return InputElement.as(e.getFirstChildElement());
    }

    void scrollToPath(String path) {
      FileInfo info = map.get(path);
      if (info != null) {
        movePointerTo(1 + info._row(), true);
      }
    }

    @Override
    protected Object getRowItemKey(FileInfo item) {
      return item.path();
    }

    @Override
    protected int findRow(Object id) {
      FileInfo info = map.get((String) id);
      return info != null ? 1 + info._row() : -1;
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
        FileInfo info = list.get(row - 1);
        if (canOpen(info.path())) {
          Gerrit.display(url(info));
        }
      }
    }

    @Override
    protected void onCellSingleClick(Event event, int row, int column) {
      if (column == C_PATH && link.handleAsClick(event)) {
        onOpenRow(row);
      } else {
        super.onCellSingleClick(event, row, column);
      }
    }

    private class OpenFileCommand extends KeyCommand {
      private final int index;

      OpenFileCommand(int index, int modifiers, char c, String helpText) {
        super(modifiers, c, helpText);
        this.index = index;
      }

      @Override
      public void onKeyPress(KeyPressEvent event) {
        FileInfo info = list.get(index);
        if (canOpen(info.path())) {
          Gerrit.display(url(info));
        }
      }
    }
  }

  private final class DisplayCommand implements RepeatingCommand {
    private final SafeHtmlBuilder sb = new SafeHtmlBuilder();
    private final MyTable myTable;
    private final JsArray<FileInfo> list;
    private final Timestamp myLastReply;
    private final NativeMap<JsArray<CommentInfo>> comments;
    private final NativeMap<JsArray<CommentInfo>> drafts;
    private final boolean hasUser;
    private final boolean showChangeSizeBars;
    private boolean attached;
    private int row;
    private double start;
    private ProgressBar meter;
    private String lastPath = "";

    private boolean hasBinaryFile;
    private boolean hasNonBinaryFile;
    private int inserted;
    private int deleted;
    private long binOldSize;
    private long bytesInserted;
    private long bytesDeleted;

    private DisplayCommand(
        NativeMap<FileInfo> map,
        JsArray<FileInfo> list,
        Timestamp myLastReply,
        @Nullable NativeMap<JsArray<CommentInfo>> comments,
        @Nullable NativeMap<JsArray<CommentInfo>> drafts) {
      this.myTable = new MyTable(map, list);
      this.list = list;
      this.myLastReply = myLastReply;
      this.comments = comments;
      this.drafts = drafts;
      this.hasUser = Gerrit.isSignedIn();
      this.showChangeSizeBars = Gerrit.getUserPreferences().sizeBarInChangeTable();
      myTable.addStyleName(R.css().table());
    }

    @Override
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
        FileInfo info = list.get(row);
        info._row(row);
        render(sb, info);
        if ((++row % 10) == 0 && longRunning()) {
          updateMeter();
          return true;
        }
      }
      footer(sb);
      myTable.resetHtml(sb);
      myTable.finishDisplay();
      setTable(myTable);
      return false;
    }

    private void computeInsertedDeleted() {
      inserted = 0;
      deleted = 0;
      binOldSize = 0;
      bytesInserted = 0;
      bytesDeleted = 0;
      for (int i = 0; i < list.length(); i++) {
        FileInfo info = list.get(i);
        if (!Patch.isMagic(info.path())) {
          if (!info.binary()) {
            hasNonBinaryFile = true;
            inserted += info.linesInserted();
            deleted += info.linesDeleted();
          } else {
            hasBinaryFile = true;
            binOldSize += info.size() - info.sizeDelta();
            if (info.sizeDelta() >= 0) {
              bytesInserted += info.sizeDelta();
            } else {
              bytesDeleted += info.sizeDelta();
            }
          }
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
      sb.openTr().setStyleName(R.css().nohover());
      sb.openTh().setStyleName(R.css().pointer()).closeTh();
      if (mode == Mode.REVIEW) {
        sb.openTh().setStyleName(R.css().reviewed()).closeTh();
      } else {
        sb.openTh().setStyleName(R.css().restoreDelete()).closeTh();
      }
      sb.openTh().setStyleName(R.css().status()).closeTh();
      sb.openTh().append(Util.C.patchTableColumnName()).closeTh();
      sb.openTh().setAttribute("colspan", 3).append(Util.C.patchTableColumnComments()).closeTh();
      sb.openTh().setAttribute("colspan", 2).append(Util.C.patchTableColumnSize()).closeTh();
      sb.closeTr();
    }

    private void render(SafeHtmlBuilder sb, FileInfo info) {
      sb.openTr();
      sb.openTd().setStyleName(R.css().pointer()).closeTd();
      if (mode == Mode.REVIEW) {
        columnReviewed(sb, info);
      } else {
        columnDeleteRestore(sb, info);
      }
      columnStatus(sb, info);
      columnPath(sb, info);
      columnComments(sb, info);
      columnDelta1(sb, info);
      columnDelta2(sb, info);
      sb.closeTr();
    }

    private void columnReviewed(SafeHtmlBuilder sb, FileInfo info) {
      sb.openTd().setStyleName(R.css().reviewed());
      if (hasUser) {
        sb.openElement("input")
            .setAttribute("title", Resources.C.reviewedFileTitle())
            .setAttribute("type", "checkbox")
            .setAttribute("onclick", REVIEWED + "(event," + info._row() + ")")
            .closeSelf();
      }
      sb.closeTd();
    }

    private void columnDeleteRestore(SafeHtmlBuilder sb, FileInfo info) {
      sb.openTd().setStyleName(R.css().restoreDelete());
      if (hasUser) {
        if (!Patch.isMagic(info.path())) {
          boolean editable = isEditable(info);
          sb.openDiv()
              .openElement("button")
              .setAttribute("title", Resources.C.restoreFileInline())
              .setAttribute("onclick", RESTORE + "(event," + info._row() + ")")
              .append(new ImageResourceRenderer().render(Gerrit.RESOURCES.editUndo()))
              .closeElement("button");
          if (editable) {
            sb.openElement("button")
                .setAttribute("title", Resources.C.removeFileInline())
                .setAttribute("onclick", DELETE + "(event," + info._row() + ")")
                .append(new ImageResourceRenderer().render(Gerrit.RESOURCES.redNot()))
                .closeElement("button");
          }
          sb.closeDiv();
        }
      }
      sb.closeTd();
    }

    private boolean isEditable(FileInfo info) {
      String status = info.status();
      return status == null || !ChangeType.DELETED.matches(status);
    }

    private void columnStatus(SafeHtmlBuilder sb, FileInfo info) {
      sb.openTd().setStyleName(R.css().status());
      if (!Patch.isMagic(info.path())
          && info.status() != null
          && !ChangeType.MODIFIED.matches(info.status())) {
        sb.append(info.status());
      }
      sb.closeTd();
    }

    private void columnPath(SafeHtmlBuilder sb, FileInfo info) {
      String path = info.path();

      sb.openTd().setStyleName(R.css().pathColumn());

      if (!canOpen(path)) {
        sb.openDiv();
        appendPath(path);
        sb.closeDiv();
        sb.closeTd();
        return;
      }

      sb.openAnchor();

      if (mode == Mode.EDIT && !isEditable(info)) {
        sb.setAttribute("onclick", RESTORE + "(event," + info._row() + ")");
      } else {
        sb.setAttribute("href", "#" + url(info))
            .setAttribute("onclick", OPEN + "(event," + info._row() + ")");
      }
      appendPath(path);
      sb.closeAnchor();
      if (info.oldPath() != null) {
        sb.br();
        sb.openSpan().setStyleName(R.css().renameCopySource()).append(info.oldPath()).closeSpan();
      }
      sb.closeTd();
    }

    private void appendPath(String path) {
      if (Patch.COMMIT_MSG.equals(path)) {
        sb.append(Util.C.commitMessage());
      } else if (Patch.MERGE_LIST.equals(path)) {
        sb.append(Util.C.mergeList());
      } else if (Gerrit.getUserPreferences().muteCommonPathPrefixes()) {
        int commonPrefixLen = commonPrefix(path);
        if (commonPrefixLen > 0) {
          sb.openSpan()
              .setStyleName(R.css().commonPrefix())
              .append(path.substring(0, commonPrefixLen))
              .closeSpan();
        }
        sb.append(path.substring(commonPrefixLen));
        lastPath = path;
      } else {
        sb.append(path);
      }
    }

    private int commonPrefix(String path) {
      for (int n = path.length(); n > 0; ) {
        int s = path.lastIndexOf('/', n);
        if (s < 0) {
          return 0;
        }

        String p = path.substring(0, s + 1);
        if (lastPath.startsWith(p)) {
          return s + 1;
        }
        n = s - 1;
      }
      return 0;
    }

    private void columnComments(SafeHtmlBuilder sb, FileInfo info) {
      JsArray<CommentInfo> cList = filterForParent(get(info.path(), comments));
      JsArray<CommentInfo> dList = filterForParent(get(info.path(), drafts));

      sb.openTd().setStyleName(R.css().draftColumn());
      if (dList.length() > 0) {
        sb.append("drafts: ").append(dList.length());
      }
      sb.closeTd();

      int cntAll = cList.length();
      int cntNew = 0;
      if (myLastReply != null) {
        for (int i = cntAll - 1; i >= 0; i--) {
          CommentInfo m = cList.get(i);
          if (m.updated().compareTo(myLastReply) > 0) {
            cntNew++;
          } else {
            break;
          }
        }
      }

      sb.openTd().setStyleName(R.css().newColumn());
      if (cntNew > 0) {
        sb.append("new: ").append(cntNew);
      }
      sb.closeTd();

      sb.openTd().setStyleName(R.css().commentColumn());
      if (cntAll - cntNew > 0) {
        sb.append("comments: ").append(cntAll - cntNew);
      }
      sb.closeTd();
    }

    private JsArray<CommentInfo> filterForParent(JsArray<CommentInfo> list) {
      JsArray<CommentInfo> result = JsArray.createArray().cast();
      for (CommentInfo c : Natives.asList(list)) {
        if (c.side() == Side.REVISION) {
          result.push(c);
        } else if (base.isBaseOrAutoMerge() && !c.hasParent()) {
          result.push(c);
        } else if (base.isParent() && c.parent() == base.getParentNum()) {
          result.push(c);
        }
      }
      return result;
    }

    private JsArray<CommentInfo> get(String p, NativeMap<JsArray<CommentInfo>> m) {
      JsArray<CommentInfo> r = null;
      if (m != null) {
        r = m.get(p);
      }
      if (r == null) {
        r = JsArray.createArray().cast();
      }
      return r;
    }

    private void columnDelta1(SafeHtmlBuilder sb, FileInfo info) {
      sb.openTd().setStyleName(R.css().deltaColumn1());
      if (!Patch.isMagic(info.path()) && !info.binary()) {
        if (showChangeSizeBars) {
          sb.append(info.linesInserted() + info.linesDeleted());
        } else if (!ChangeType.DELETED.matches(info.status())) {
          if (ChangeType.ADDED.matches(info.status())) {
            sb.append(info.linesInserted()).append(" lines");
          } else {
            sb.append("+").append(info.linesInserted()).append(", -").append(info.linesDeleted());
          }
        }
      } else if (info.binary()) {
        sb.append(formatBytes(info.sizeDelta()));
        long oldSize = info.size() - info.sizeDelta();
        if (oldSize != 0) {
          sb.append(" (").append(formatPercentage(oldSize, info.sizeDelta())).append(")");
        }
      }
      sb.closeTd();
    }

    private void columnDelta2(SafeHtmlBuilder sb, FileInfo info) {
      sb.openTd().setStyleName(R.css().deltaColumn2());
      if (showChangeSizeBars
          && !Patch.isMagic(info.path())
          && !info.binary()
          && (info.linesInserted() != 0 || info.linesDeleted() != 0)) {
        int w = 80;
        int t = inserted + deleted;
        int i = Math.max(5, (int) (((double) w) * info.linesInserted() / t));
        int d = Math.max(5, (int) (((double) w) * info.linesDeleted() / t));

        sb.setAttribute(
            "title", Util.M.patchTableSize_LongModify(info.linesInserted(), info.linesDeleted()));

        if (0 < info.linesInserted()) {
          sb.openDiv()
              .setStyleName(R.css().inserted())
              .setAttribute("style", "width:" + i + "px")
              .closeDiv();
        }
        if (0 < info.linesDeleted()) {
          sb.openDiv()
              .setStyleName(R.css().deleted())
              .setAttribute("style", "width:" + d + "px")
              .closeDiv();
        }
      }
      sb.closeTd();
    }

    private void footer(SafeHtmlBuilder sb) {
      sb.openTr().setStyleName(R.css().nohover());
      sb.openTh().setStyleName(R.css().pointer()).closeTh();
      if (mode == Mode.REVIEW) {
        sb.openTh().setStyleName(R.css().reviewed()).closeTh();
      } else {
        sb.openTh().setStyleName(R.css().restoreDelete()).closeTh();
      }
      sb.openTh().setStyleName(R.css().status()).closeTh();
      sb.openTd().closeTd(); // path
      sb.openTd().setAttribute("colspan", 3).closeTd(); // comments

      // delta1
      sb.openTh().setStyleName(R.css().deltaColumn1());
      if (hasNonBinaryFile) {
        sb.append(Util.M.patchTableSize_Modify(inserted, deleted));
      }
      if (hasBinaryFile) {
        if (hasNonBinaryFile) {
          sb.br();
        }
        if (binOldSize != 0) {
          sb.append(
              Util.M.patchTableSize_ModifyBinaryFilesWithPercentages(
                  formatAbsBytes(bytesInserted),
                  formatAbsPercentage(binOldSize, bytesInserted),
                  formatAbsBytes(bytesDeleted),
                  formatAbsPercentage(binOldSize, bytesDeleted)));
        } else {
          sb.append(
              Util.M.patchTableSize_ModifyBinaryFiles(
                  formatAbsBytes(bytesInserted), formatAbsBytes(bytesDeleted)));
        }
      }
      sb.closeTh();

      // delta2
      sb.openTh().setStyleName(R.css().deltaColumn2());
      if (showChangeSizeBars) {
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
      }
      sb.closeTh();

      sb.closeTr();
    }
  }
}
