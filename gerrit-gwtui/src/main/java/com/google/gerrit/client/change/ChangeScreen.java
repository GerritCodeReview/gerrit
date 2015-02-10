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

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountInfo.AvatarInfo;
import com.google.gerrit.client.actions.ActionInfo;
import com.google.gerrit.client.api.ChangeGlue;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.ChangeInfo.EditInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.ChangeInfo.MessageInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.RevisionInfoCache;
import com.google.gerrit.client.changes.StarredChanges;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.diff.DiffApi;
import com.google.gerrit.client.diff.FileInfo;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.projects.ConfigInfoCache.Entry;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.UserActivityMonitor;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.ToggleButton;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;

import net.codemirror.lib.CodeMirror;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ChangeScreen extends Screen {
  interface Binder extends UiBinder<HTMLPanel, ChangeScreen> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String labelName();
    String avatar();
    String label_user();
    String label_ok();
    String label_reject();
    String label_may();
    String label_need();
    String replyBox();
    String selected();
    String hashtagName();
  }

  static ChangeScreen get(NativeEvent in) {
    Element e = in.getEventTarget().cast();
    for (e = DOM.getParent(e); e != null; e = DOM.getParent(e)) {
      EventListener l = DOM.getEventListener(e);
      if (l instanceof ChangeScreen) {
        return (ChangeScreen) l;
      }
    }
    return null;
  }

  private final Change.Id changeId;
  private String base;
  private String revision;
  private ChangeInfo changeInfo;
  private CommentLinkProcessor commentLinkProcessor;
  private EditInfo edit;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private List<HandlerRegistration> handlers = new ArrayList<>(4);
  private UpdateCheckTimer updateCheck;
  private Timestamp lastDisplayedUpdate;
  private UpdateAvailableBar updateAvailable;
  private boolean openReplyBox;
  private boolean loaded;
  private FileTable.Mode fileTableMode;

  @UiField HTMLPanel headerLine;
  @UiField Style style;
  @UiField ToggleButton star;
  @UiField Anchor permalink;

  @UiField Element ccText;
  @UiField Reviewers reviewers;
  @UiField Hashtags hashtags;
  @UiField Element hashtagTableRow;
  @UiField FlowPanel ownerPanel;
  @UiField InlineHyperlink ownerLink;
  @UiField Element statusText;
  @UiField Image projectSettings;
  @UiField AnchorElement projectSettingsLink;
  @UiField InlineHyperlink projectDashboard;
  @UiField InlineHyperlink branchLink;
  @UiField Element strategy;
  @UiField Element submitActionText;
  @UiField Element notMergeable;
  @UiField Topic topic;
  @UiField Element actionText;
  @UiField Element actionDate;

  @UiField Actions actions;
  @UiField Labels labels;
  @UiField CommitBox commit;
  @UiField RelatedChanges related;
  @UiField FileTable files;
  @UiField ListBox diffBase;
  @UiField History history;

  @UiField Button includedIn;
  @UiField Button patchSets;
  @UiField Element patchSetsText;
  @UiField Button download;
  @UiField Button reply;
  @UiField Button publishEdit;
  @UiField Button rebaseEdit;
  @UiField Button deleteEdit;
  @UiField Button publish;
  @UiField Button deleteChange;
  @UiField Button deleteRevision;
  @UiField Button openAll;
  @UiField Button editMode;
  @UiField Button reviewMode;
  @UiField Button addFile;
  @UiField Button deleteFile;
  @UiField Button expandAll;
  @UiField Button collapseAll;
  @UiField QuickApprove quickApprove;

  private ReplyAction replyAction;
  private IncludedInAction includedInAction;
  private PatchSetsAction patchSetsAction;
  private DownloadAction downloadAction;
  private AddFileAction addFileAction;
  private DeleteFileAction deleteFileAction;

  public ChangeScreen(Change.Id changeId, String base, String revision,
      boolean openReplyBox, FileTable.Mode mode) {
    this.changeId = changeId;
    this.base = normalize(base);
    this.revision = normalize(revision);
    this.openReplyBox = openReplyBox;
    this.fileTableMode = mode;
    add(uiBinder.createAndBindUi(this));
  }

  Change.Id getChangeId() {
    return changeId;
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    CallbackGroup group = new CallbackGroup();
    if (Gerrit.isSignedIn()) {
      ChangeApi.editWithFiles(changeId.get(), group.add(
          new AsyncCallback<EditInfo>() {
            @Override
            public void onSuccess(EditInfo result) {
              edit = result;
            }

            @Override
            public void onFailure(Throwable caught) {
            }
          }));
    }
    loadChangeInfo(true, group.addFinal(
        new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo info) {
            info.init();
            // Revision loading may be slower than the rest, so do it
            // asynchronous to have the rest fast.
            loadConfigInfo(info, base);
            loadRevisionInfo();
          }
        }));
  }

  void loadChangeInfo(boolean fg, AsyncCallback<ChangeInfo> cb) {
    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(
      ListChangesOption.CHANGE_ACTIONS,
      ListChangesOption.ALL_REVISIONS));
    if (!fg) {
      call.background();
    }
    call.get(cb);
  }

  void loadRevisionInfo() {
    RestApi call = ChangeApi.actions(changeId.get(), revision);
    call.background();
    call.get(new AsyncCallback<NativeMap<ActionInfo>>() {
      @Override
      public void onFailure(Throwable caught) {
      }

      @Override
      public void onSuccess(NativeMap<ActionInfo> result) {
        renderRevisionInfo(changeInfo, result);
      }
    });
  }

  @Override
  protected void onUnload() {
    if (replyAction != null) {
      replyAction.hide();
    }
    if (updateCheck != null) {
      updateCheck.cancel();
      updateCheck = null;
    }
    for (HandlerRegistration h : handlers) {
      h.removeHandler();
    }
    handlers.clear();
    super.onUnload();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setHeaderVisible(false);
    Resources.I.style().ensureInjected();
    star.setVisible(Gerrit.isSignedIn());
    labels.init(style);
    reviewers.init(style, ccText);
    hashtags.init(style);

    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysNavigation.add(new KeyCommand(0, 'u', Util.C.upToChangeList()) {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        Gerrit.displayLastChangeList();
      }
    });
    keysNavigation.add(new KeyCommand(0, 'R', Util.C.keyReloadChange()) {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        Gerrit.display(PageLinks.toChange(changeId));
      }
    });
    keysNavigation.add(new KeyCommand(0, 'n', Util.C.keyNextPatchSet()) {
        @Override
        public void onKeyPress(final KeyPressEvent event) {
          gotoSibling(1);
        }
      }, new KeyCommand(0, 'p', Util.C.keyPreviousPatchSet()) {
        @Override
        public void onKeyPress(final KeyPressEvent event) {
          gotoSibling(-1);
        }
      });

    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    keysAction.add(new KeyCommand(0, 'a', Util.C.keyPublishComments()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (Gerrit.isSignedIn()) {
          onReply(null);
        } else {
          Gerrit.doSignIn(getToken());
        }
      }
    });
    keysAction.add(new KeyCommand(0, 'x', Util.C.keyExpandAllMessages()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        onExpandAll(null);
      }
    });
    keysAction.add(new KeyCommand(0, 'z', Util.C.keyCollapseAllMessages()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        onCollapseAll(null);
      }
    });
    if (Gerrit.isSignedIn()) {
      keysAction.add(new KeyCommand(0, 's', Util.C.changeTableStar()) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          star.setValue(!star.getValue(), true);
        }
      });
      keysAction.add(new KeyCommand(0, 'c', Util.C.keyAddReviewers()) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          reviewers.onOpenForm();
        }
      });
    }
  }

  private void initReplyButton(ChangeInfo info, String revision) {
    if (!info.revision(revision).is_edit()) {
      reply.setTitle(Gerrit.getConfig().getReplyTitle());
      reply.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(Gerrit.getConfig().getReplyLabel())
        .closeDiv());
      reply.setVisible(true);
    }
  }

  private void gotoSibling(final int offset) {
    if (offset > 0 && changeInfo.current_revision().equals(revision)) {
      return;
    }

    if (offset < 0 && changeInfo.revision(revision)._number() == 1) {
      return;
    }

    JsArray<RevisionInfo> revisions = changeInfo.revisions().values();
    RevisionInfo.sortRevisionInfoByNumber(revisions);
    for (int i = 0; i < revisions.length(); i++) {
      if (revision.equals(revisions.get(i).name())) {
        if (0 <= i + offset && i + offset < revisions.length()) {
          Gerrit.display(PageLinks.toChange(
              new PatchSet.Id(changeInfo.legacy_id(),
              revisions.get(i + offset)._number())));
          return;
        }
        return;
      }
    }
  }

  private void initIncludedInAction(ChangeInfo info) {
    if (info.status() == Status.MERGED) {
      includedInAction = new IncludedInAction(
          info.legacy_id(),
          style, headerLine, includedIn);
      includedIn.setVisible(true);
    }
  }

  private void initChangeAction(ChangeInfo info, NativeMap<ActionInfo> actions) {
    if (info.status() == Status.DRAFT) {
      actions.copyKeysIntoChildren("id");
      if (actions.containsKey("/")) {
        deleteChange.setVisible(true);
        deleteChange.setTitle(actions.get("/").title());
      }
    }
  }

  private void initRevisionsAction(ChangeInfo info, String revision,
      NativeMap<ActionInfo> actions) {
    int currentPatchSet;
    if (info.current_revision() != null
        && info.revisions().containsKey(info.current_revision())) {
      currentPatchSet = info.revision(info.current_revision())._number();
    } else {
      JsArray<RevisionInfo> revList = info.revisions().values();
      RevisionInfo.sortRevisionInfoByNumber(revList);
      currentPatchSet = revList.get(revList.length() - 1)._number();
    }

    String currentlyViewedPatchSet;
    if (info.revision(revision).id().equals("edit")) {
      currentlyViewedPatchSet =
          Resources.M.editPatchSet(RevisionInfo.findEditParent(info.revisions()
              .values()));
      currentPatchSet = info.revisions().values().length() - 1;
    } else {
      currentlyViewedPatchSet = info.revision(revision).id();
    }
    patchSetsText.setInnerText(Resources.M.patchSets(
        currentlyViewedPatchSet, currentPatchSet));
    patchSetsAction = new PatchSetsAction(
        info.legacy_id(), revision,
        style, headerLine, patchSets);

    RevisionInfo revInfo = info.revision(revision);
    if (revInfo.draft()) {
      actions.copyKeysIntoChildren("id");

      if (actions.containsKey("publish")) {
        publish.setVisible(true);
        publish.setTitle(actions.get("publish").title());
      }
      if (actions.containsKey("/")) {
        deleteRevision.setVisible(true);
        deleteRevision.setTitle(actions.get("/").title());
      }
    }
  }

  private void initDownloadAction(ChangeInfo info, String revision) {
    downloadAction =
        new DownloadAction(info, revision, style, headerLine, download);
  }

  private void initProjectLinks(final ChangeInfo info) {
    projectSettingsLink.setHref(
        "#" + PageLinks.toProject(info.project_name_key()));
    projectSettings.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (Hyperlink.impl.handleAsClick((Event) event.getNativeEvent())) {
          event.stopPropagation();
          event.preventDefault();
          Gerrit.display(PageLinks.toProject(info.project_name_key()));
        }
      }
    }, ClickEvent.getType());
    projectDashboard.setText(info.project());
    projectDashboard.setTargetHistoryToken(
        PageLinks.toProjectDefaultDashboard(info.project_name_key()));
  }

  private void initBranchLink(ChangeInfo info) {
    branchLink.setText(info.branch());
    branchLink.setTargetHistoryToken(
        PageLinks.toChangeQuery(
            BranchLink.query(
                info.project_name_key(),
                info.status(),
                info.branch(),
                null)));
  }

  private void initEditMode(ChangeInfo info, String revision) {
    if (Gerrit.isSignedIn() && info.status().isOpen()) {
      RevisionInfo rev = info.revision(revision);
      if (isEditModeEnabled(info, rev)) {
        editMode.setVisible(fileTableMode == FileTable.Mode.REVIEW);
        addFile.setVisible(!editMode.isVisible());
        deleteFile.setVisible(!editMode.isVisible());
        reviewMode.setVisible(!editMode.isVisible());
        addFileAction = new AddFileAction(
            changeId, info.revision(revision),
            style, addFile);
        deleteFileAction = new DeleteFileAction(
            changeId, info.revision(revision),
            style, addFile);
      } else {
        editMode.setVisible(false);
        addFile.setVisible(false);
        reviewMode.setVisible(false);
      }

      if (rev.is_edit()) {
        if (info.hasEditBasedOnCurrentPatchSet()) {
          publishEdit.setVisible(true);
        } else {
          rebaseEdit.setVisible(true);
        }
        deleteEdit.setVisible(true);
      }
    }
  }

  private boolean isEditModeEnabled(ChangeInfo info, RevisionInfo rev) {
    if (rev.is_edit()) {
      return true;
    }
    if (edit == null) {
      return revision.equals(info.current_revision());
    }
    return rev._number() == RevisionInfo.findEditParent(
        info.revisions().values());
  }

  @UiHandler("publishEdit")
  void onPublishEdit(@SuppressWarnings("unused") ClickEvent e) {
    EditActions.publishEdit(changeId);
  }

  @UiHandler("rebaseEdit")
  void onRebaseEdit(@SuppressWarnings("unused") ClickEvent e) {
    EditActions.rebaseEdit(changeId);
  }

  @UiHandler("deleteEdit")
  void onDeleteEdit(@SuppressWarnings("unused") ClickEvent e) {
    if (Window.confirm(Resources.C.deleteChangeEdit())) {
      EditActions.deleteEdit(changeId);
    }
  }

  @UiHandler("publish")
  void onPublish(@SuppressWarnings("unused") ClickEvent e) {
    DraftActions.publish(changeId, revision);
  }

  @UiHandler("deleteRevision")
  void onDeleteRevision(@SuppressWarnings("unused") ClickEvent e) {
    if (Window.confirm(Resources.C.deleteDraftRevision())) {
      DraftActions.delete(changeId, revision);
    }
  }

  @UiHandler("deleteChange")
  void onDeleteChange(@SuppressWarnings("unused") ClickEvent e) {
    if (Window.confirm(Resources.C.deleteDraftChange())) {
      DraftActions.delete(changeId);
    }
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    handlers.add(GlobalKey.add(this, keysNavigation));
    handlers.add(GlobalKey.add(this, keysAction));
    files.registerKeys();
  }

  @Override
  public void onShowView() {
    super.onShowView();
    commit.onShowView();
    related.setMaxHeight(commit.getElement()
        .getParentElement()
        .getOffsetHeight());

    if (openReplyBox) {
      onReply();
    } else {
      String prior = Gerrit.getPriorView();
      if (prior != null && prior.startsWith("/c/")) {
        scrollToPath(prior.substring(3));
      }
    }

    ChangeGlue.fireShowChange(changeInfo, changeInfo.revision(revision));
    CodeMirror.preload();
    startPoller();
  }

  private void scrollToPath(String token) {
    int s = token.indexOf('/');
    try {
      String c = token.substring(0, s);
      int editIndex = c.indexOf(",edit");
      if (editIndex > 0) {
        c = c.substring(0, editIndex);
      }
      if (s < 0 || !changeId.equals(Change.Id.parse(c))) {
        return; // Unrelated URL, do not scroll.
      }
    } catch (IllegalArgumentException e) {
      return;
    }

    s = token.indexOf('/', s + 1);
    if (s < 0) {
      return; // URL does not name a file.
    }

    int c = token.lastIndexOf(',');
    if (0 <= c) {
      token = token.substring(s + 1, c);
    } else {
      token = token.substring(s + 1);
    }

    if (!token.isEmpty()) {
      files.scrollToPath(KeyUtil.decode(token));
    }
  }

  @UiHandler("star")
  void onToggleStar(ValueChangeEvent<Boolean> e) {
    StarredChanges.toggleStar(changeId, e.getValue());
  }

  @UiHandler("includedIn")
  void onIncludedIn(@SuppressWarnings("unused") ClickEvent e) {
    includedInAction.show();
  }

  @UiHandler("download")
  void onDownload(@SuppressWarnings("unused") ClickEvent e) {
    downloadAction.show();
  }

  @UiHandler("patchSets")
  void onPatchSets(@SuppressWarnings("unused") ClickEvent e) {
    patchSetsAction.show();
  }

  @UiHandler("reply")
  void onReply(@SuppressWarnings("unused") ClickEvent e) {
    onReply();
  }

  @UiHandler("permalink")
  void onReload(ClickEvent e) {
    e.preventDefault();
    Gerrit.display(PageLinks.toChange(changeId));
  }

  private void onReply() {
    if (Gerrit.isSignedIn()) {
      replyAction.onReply(null);
    } else {
      Gerrit.doSignIn(getToken());
    }
  }

  @UiHandler("openAll")
  void onOpenAll(@SuppressWarnings("unused") ClickEvent e) {
    files.openAll();
  }

  @UiHandler("editMode")
  void onEditMode(@SuppressWarnings("unused") ClickEvent e) {
    fileTableMode = FileTable.Mode.EDIT;
    refreshFileTable();
    editMode.setVisible(false);
    addFile.setVisible(true);
    deleteFile.setVisible(true);
    reviewMode.setVisible(true);
  }

  @UiHandler("reviewMode")
  void onReviewMode(@SuppressWarnings("unused") ClickEvent e) {
    fileTableMode = FileTable.Mode.REVIEW;
    refreshFileTable();
    editMode.setVisible(true);
    addFile.setVisible(false);
    deleteFile.setVisible(false);
    reviewMode.setVisible(false);
  }

  @UiHandler("addFile")
  void onAddFile(@SuppressWarnings("unused") ClickEvent e) {
    addFileAction.onEdit();
  }

  @UiHandler("deleteFile")
  void onDeleteFile(@SuppressWarnings("unused") ClickEvent e) {
    deleteFileAction.onDelete();
  }

  private void refreshFileTable() {
    int idx = diffBase.getSelectedIndex();
    if (0 <= idx) {
      String n = diffBase.getValue(idx);
      loadConfigInfo(changeInfo, !n.isEmpty() ? n : null);
    }
  }

  @UiHandler("expandAll")
  void onExpandAll(@SuppressWarnings("unused") ClickEvent e) {
    int n = history.getWidgetCount();
    for (int i = 0; i < n; i++) {
      ((Message) history.getWidget(i)).setOpen(true);
    }
    expandAll.setVisible(false);
    collapseAll.setVisible(true);
  }

  @UiHandler("collapseAll")
  void onCollapseAll(@SuppressWarnings("unused") ClickEvent e) {
    int n = history.getWidgetCount();
    for (int i = 0; i < n; i++) {
      ((Message) history.getWidget(i)).setOpen(false);
    }
    expandAll.setVisible(true);
    collapseAll.setVisible(false);
  }

  @UiHandler("diffBase")
  void onChangeRevision(@SuppressWarnings("unused") ChangeEvent e) {
    int idx = diffBase.getSelectedIndex();
    if (0 <= idx) {
      String n = diffBase.getValue(idx);
      loadConfigInfo(changeInfo, !n.isEmpty() ? n : null);
    }
  }

  private void loadConfigInfo(final ChangeInfo info, final String base) {
    info.revisions().copyKeysIntoChildren("name");
    if (edit != null) {
      edit.set_name(edit.commit().commit());
      info.set_edit(edit);
      if (edit.has_files()) {
        edit.files().copyKeysIntoChildren("path");
      }
      info.revisions().put(edit.name(), RevisionInfo.fromEdit(edit));
      JsArray<RevisionInfo> list = info.revisions().values();

      // Edit is converted to a regular revision (with number = 0) and
      // added to the list of revisions. Additionally under certain
      // circumstances change edit is assigned to be the current revision
      // and is selected to be shown on the change screen.
      // We have two different strategies to assign edit to the current ps:
      // 1. revision == null: no revision is selected, so use the edit only
      //    if it is based on the latest patch set
      // 2. edit was selected explicitly from ps drop down:
      //    use the edit regardless of which patch set it is based on
      if (revision == null) {
        RevisionInfo.sortRevisionInfoByNumber(list);
        RevisionInfo rev = list.get(list.length() - 1);
        if (rev.is_edit()) {
          info.set_current_revision(rev.name());
        }
      } else if (revision.equals("edit") || revision.equals("0")) {
        for (int i = 0; i < list.length(); i++) {
          RevisionInfo r = list.get(i);
          if (r.is_edit()) {
            info.set_current_revision(r.name());
            break;
          }
        }
      }
    }
    final RevisionInfo rev = resolveRevisionToDisplay(info);
    final RevisionInfo b = resolveRevisionOrPatchSetId(info, base, null);

    CallbackGroup group = new CallbackGroup();
    if (rev.is_edit()) {
      NativeMap<JsArray<CommentInfo>> emptyComment = NativeMap.create();
      files.set(
          b != null ? new PatchSet.Id(changeId, b._number()) : null,
          new PatchSet.Id(changeId, rev._number()),
          style, reply, fileTableMode, edit != null);
      files.setValue(info.edit().files(), myLastReply(info), emptyComment,
          emptyComment);
    } else {
      loadDiff(b, rev, myLastReply(info), group);
    }
    loadCommit(rev, group);

    if (loaded) {
      group.done();
      return;
    }

    RevisionInfoCache.add(changeId, rev);
    ConfigInfoCache.add(info);
    ConfigInfoCache.get(info.project_name_key(),
      group.addFinal(new ScreenLoadCallback<ConfigInfoCache.Entry>(this) {
        @Override
        protected void preDisplay(Entry result) {
          loaded = true;
          commentLinkProcessor = result.getCommentLinkProcessor();
          setTheme(result.getTheme());
          renderChangeInfo(info);
        }
      }));
  }

  static Timestamp myLastReply(ChangeInfo info) {
    if (Gerrit.isSignedIn() && info.messages() != null) {
      int self = Gerrit.getUserAccountInfo()._account_id();
      for (int i = info.messages().length() - 1; i >= 0; i--) {
        MessageInfo m = info.messages().get(i);
        if (m.author() != null && m.author()._account_id() == self) {
          return m.date();
        }
      }
    }
    return null;
  }

  private void loadDiff(final RevisionInfo base, final RevisionInfo rev,
      final Timestamp myLastReply, CallbackGroup group) {
    final List<NativeMap<JsArray<CommentInfo>>> comments = loadComments(rev, group);
    final List<NativeMap<JsArray<CommentInfo>>> drafts = loadDrafts(rev, group);
    DiffApi.list(changeId.get(),
      base != null ? base.name() : null,
      rev.name(),
      group.add(new AsyncCallback<NativeMap<FileInfo>>() {
        @Override
        public void onSuccess(NativeMap<FileInfo> m) {
          files.set(
              base != null ? new PatchSet.Id(changeId, base._number()) : null,
              new PatchSet.Id(changeId, rev._number()),
              style, reply, fileTableMode, edit != null);
          files.setValue(m, myLastReply, comments.get(0), drafts.get(0));
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      }));

    if (Gerrit.isSignedIn() && fileTableMode == FileTable.Mode.REVIEW) {
      ChangeApi.revision(changeId.get(), rev.name())
        .view("files")
        .addParameterTrue("reviewed")
        .get(group.add(new AsyncCallback<JsArrayString>() {
            @Override
            public void onSuccess(JsArrayString result) {
              files.markReviewed(result);
            }

            @Override
            public void onFailure(Throwable caught) {
            }
          }));
    }
  }

  private List<NativeMap<JsArray<CommentInfo>>> loadComments(
      RevisionInfo rev, CallbackGroup group) {
    final int id = rev._number();
    final List<NativeMap<JsArray<CommentInfo>>> r = new ArrayList<>(1);
    ChangeApi.revision(changeId.get(), rev.name())
      .view("comments")
      .get(group.add(new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
        @Override
        public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
          r.add(result);
          history.addComments(id, result);
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      }));
    return r;
  }

  private List<NativeMap<JsArray<CommentInfo>>> loadDrafts(
      RevisionInfo rev, CallbackGroup group) {
    final List<NativeMap<JsArray<CommentInfo>>> r = new ArrayList<>(1);
    if (Gerrit.isSignedIn()) {
      ChangeApi.revision(changeId.get(), rev.name())
        .view("drafts")
        .get(group.add(new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
          @Override
          public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
            r.add(result);
          }

          @Override
          public void onFailure(Throwable caught) {
          }
        }));
    } else {
      r.add(NativeMap.<JsArray<CommentInfo>> create());
    }
    return r;
  }

  private void loadCommit(final RevisionInfo rev, CallbackGroup group) {
    if (rev.is_edit()) {
      return;
    }

    ChangeApi.commitWithLinks(changeId.get(), rev.name(),
        group.add(new AsyncCallback<CommitInfo>() {
          @Override
          public void onSuccess(CommitInfo info) {
            rev.set_commit(info);
          }

          @Override
          public void onFailure(Throwable caught) {
          }
        }));
  }

  private void loadSubmitType(final Change.Status status, final boolean canSubmit) {
    if (canSubmit) {
      actions.setSubmitEnabled();
      if (status == Change.Status.NEW) {
        statusText.setInnerText(Util.C.readyToSubmit());
      }
    }
    ChangeApi.revision(changeId.get(), revision)
      .view("submit_type")
      .get(new AsyncCallback<NativeString>() {
        @Override
        public void onSuccess(NativeString result) {
          if (canSubmit) {
            if (status == Change.Status.NEW) {
              statusText.setInnerText(changeInfo.mergeable()
                  ? Util.C.readyToSubmit()
                  : Util.C.mergeConflict());
            }
          }
          setVisible(notMergeable, !changeInfo.mergeable());

          renderSubmitType(result.asString());
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      });
  }

  private RevisionInfo resolveRevisionToDisplay(ChangeInfo info) {
    RevisionInfo rev = resolveRevisionOrPatchSetId(info, revision,
        info.current_revision());
    if (rev != null) {
      revision = rev.name();
      return rev;
    }

    // the revision is not visible to the calling user (maybe it is a draft?)
    // or the change is corrupt, take the last revision that was returned,
    // if no revision was returned display an error
    JsArray<RevisionInfo> revisions = info.revisions().values();
    if (revisions.length() > 0) {
      RevisionInfo.sortRevisionInfoByNumber(revisions);
      rev = revisions.get(revisions.length() - 1);
      revision = rev.name();
      return rev;
    } else {
      new ErrorDialog(
          Resources.M.changeWithNoRevisions(info.legacy_id().get())).center();
      throw new IllegalStateException("no revision, cannot proceed");
    }
  }

  /**
   *
   * Resolve a revision or patch set id string to RevisionInfo.
   * When this view is created from the changes table, revision
   * is passed as a real revision.
   * When this view is created from side by side (by closing it with 'u')
   * patch set id is passed.
   *
   * @param info change info
   * @param revOrId revision or patch set id
   * @param defaultValue value returned when rev is null
   * @return resolved revision or default value
   */
  private RevisionInfo resolveRevisionOrPatchSetId(ChangeInfo info,
      String revOrId, String defaultValue) {
    if (revOrId == null) {
      revOrId = defaultValue;
    } else if (!info.revisions().containsKey(revOrId)) {
      JsArray<RevisionInfo> list = info.revisions().values();
      for (int i = 0; i < list.length(); i++) {
        RevisionInfo r = list.get(i);
        if (revOrId.equals(String.valueOf(r._number()))) {
          revOrId = r.name();
          break;
        }
      }
    }
    return revOrId != null ? info.revision(revOrId) : null;
  }

  private boolean isSubmittable(ChangeInfo info) {
    boolean canSubmit = info.status().isOpen();
    if (canSubmit && info.status() == Change.Status.NEW) {
      for (String name : info.labels()) {
        LabelInfo label = info.label(name);
        switch (label.status()) {
          case NEED:
            statusText.setInnerText("Needs " + name);
            canSubmit = false;
            break;
          case REJECT:
          case IMPOSSIBLE:
            if (label.blocking()) {
              statusText.setInnerText("Not " + name);
              canSubmit = false;
            }
            break;
          default:
            break;
          }
      }
    }
    return canSubmit;
  }

  private void renderChangeInfo(ChangeInfo info) {
    changeInfo = info;
    lastDisplayedUpdate = info.updated();

    labels.set(info);

    renderOwner(info);
    renderActionTextDate(info);
    renderDiffBaseListBox(info);
    initReplyButton(info, revision);
    initIncludedInAction(info);
    initDownloadAction(info, revision);
    initProjectLinks(info);
    initBranchLink(info);
    initEditMode(info, revision);
    actions.display(info, revision);

    star.setValue(info.starred());
    permalink.setHref(ChangeLink.permalink(changeId));
    permalink.setText(String.valueOf(info.legacy_id()));
    topic.set(info, revision);
    commit.set(commentLinkProcessor, info, revision);
    related.set(info, revision);
    reviewers.set(info);
    if (Gerrit.isNoteDbEnabled()) {
      hashtags.set(info);
    } else {
      setVisible(hashtagTableRow, false);
    }

    StringBuilder sb = new StringBuilder();
    sb.append(Util.M.changeScreenTitleId(info.id_abbreviated()));
    if (info.subject() != null) {
      sb.append(": ");
      sb.append(info.subject());
    }
    setWindowTitle(sb.toString());

    renderRevisionInfo(info, NativeMap.<ActionInfo> create());
  }

  void renderRevisionInfo(ChangeInfo info, NativeMap<ActionInfo> actionMap) {
    RevisionInfo revisionInfo = info.revision(revision);
    boolean current = info.status().isOpen()
        && revision.equals(info.current_revision())
        && !revisionInfo.is_edit();

    if (revisionInfo.is_edit()) {
      statusText.setInnerText(Util.C.changeEdit());
    } else if (!current && info.status() == Change.Status.NEW) {
      statusText.setInnerText(Util.C.notCurrent());
      labels.setVisible(false);
    } else {
      statusText.setInnerText(Util.toLongString(info.status()));
    }

    initChangeAction(info, actionMap);
    initRevisionsAction(info, revision, actionMap);

    if (Gerrit.isSignedIn()) {
      replyAction = new ReplyAction(info, revision,
          style, commentLinkProcessor, reply, quickApprove);
      if (topic.canEdit()) {
        keysAction.add(new KeyCommand(0, 't', Util.C.keyEditTopic()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            topic.onEdit();
          }
        });
      }
    }
    history.set(commentLinkProcessor, replyAction, changeId, info);

    if (current) {
      quickApprove.set(info, revision, replyAction);
      loadSubmitType(info.status(), isSubmittable(info));
    } else {
      quickApprove.setVisible(false);
      setVisible(strategy, false);
    }
  }

  private void renderOwner(ChangeInfo info) {
    // TODO info card hover
    String name = info.owner().name() != null
        ? info.owner().name()
        : Gerrit.getConfig().getAnonymousCowardName();

    if (info.owner().avatar(AvatarInfo.DEFAULT_SIZE) != null) {
      ownerPanel.insert(new AvatarImage(info.owner()), 0);
    }
    ownerLink.setText(name);
    ownerLink.setTitle(info.owner().email() != null
        ? info.owner().email()
        : name);
    ownerLink.setTargetHistoryToken(PageLinks.toAccountQuery(
        info.owner().name() != null
        ? info.owner().name()
        : info.owner().email() != null
        ? info.owner().email()
        : String.valueOf(info.owner()._account_id()), Change.Status.NEW));
  }

  private void renderSubmitType(String action) {
    try {
      SubmitType type = SubmitType.valueOf(action);
      submitActionText.setInnerText(
          com.google.gerrit.client.admin.Util.toLongString(type));
    } catch (IllegalArgumentException e) {
      submitActionText.setInnerText(action);
    }
  }

  private void renderActionTextDate(ChangeInfo info) {
    String action;
    if (info.created().equals(info.updated())) {
      action = Util.C.changeInfoBlockUploaded();
    } else {
      action = Util.C.changeInfoBlockUpdated();
    }
    actionText.setInnerText(action);
    actionDate.setInnerText(FormatUtil.relativeFormat(info.updated()));
  }

  private void renderDiffBaseListBox(ChangeInfo info) {
    JsArray<RevisionInfo> list = info.revisions().values();
    RevisionInfo.sortRevisionInfoByNumber(list);
    int selectedIdx = list.length();
    for (int i = list.length() - 1; i >= 0; i--) {
      RevisionInfo r = list.get(i);
      diffBase.addItem(
        r.id() + ": " + r.name().substring(0, 6),
        r.name());
      if (r.name().equals(revision)) {
        SelectElement.as(diffBase.getElement()).getOptions()
            .getItem(diffBase.getItemCount() - 1).setDisabled(true);
      }
      if (base != null && base.equals(String.valueOf(r._number()))) {
        selectedIdx = diffBase.getItemCount() - 1;
      }
    }

    RevisionInfo rev = info.revisions().get(revision);
    JsArray<CommitInfo> parents = rev.commit().parents();
    diffBase.addItem(
      parents.length() > 1 ? Util.C.autoMerge() : Util.C.baseDiffItem(),
      "");

    diffBase.setSelectedIndex(selectedIdx);
  }

  void showUpdates(ChangeInfo newInfo) {
    if (!isAttached() || newInfo.updated().equals(lastDisplayedUpdate)) {
      return;
    }

    JsArray<MessageInfo> om = changeInfo.messages();
    JsArray<MessageInfo> nm = newInfo.messages();

    if (om == null) {
      om = JsArray.createArray().cast();
    }
    if (nm == null) {
      nm = JsArray.createArray().cast();
    }

    if (updateAvailable == null) {
      updateAvailable = new UpdateAvailableBar() {
        @Override
        void onShow() {
          Gerrit.display(PageLinks.toChange(changeId));
        }

        @Override
        void onIgnore(Timestamp newTime) {
          lastDisplayedUpdate = newTime;
        }
      };
    }
    updateAvailable.set(
        Natives.asList(nm).subList(om.length(), nm.length()),
        newInfo.updated());
    if (!updateAvailable.isAttached()) {
      add(updateAvailable);
    }
  }

  private void startPoller() {
    if (Gerrit.isSignedIn() && 0 < Gerrit.getConfig().getChangeUpdateDelay()) {
      updateCheck = new UpdateCheckTimer(this);
      updateCheck.schedule();
      handlers.add(UserActivityMonitor.addValueChangeHandler(updateCheck));
    }
  }

  private static String normalize(String r) {
    return r != null && !r.isEmpty() ? r : null;
  }
}
