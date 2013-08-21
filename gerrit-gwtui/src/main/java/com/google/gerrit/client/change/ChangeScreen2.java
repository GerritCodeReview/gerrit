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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ActionInfo;
import com.google.gerrit.client.changes.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.ChangeInfo.MergeableInfo;
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
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.UserActivityMonitor;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ToggleButton;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtorm.client.KeyUtil;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangeScreen2 extends Screen {
  interface Binder extends UiBinder<HTMLPanel, ChangeScreen2> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String labelName();
    String label_user();
    String label_ok();
    String label_reject();
    String label_may();
    String label_need();
    String replyBox();
  }

  private final Change.Id changeId;
  private String revision;
  private ChangeInfo changeInfo;
  private CommentLinkProcessor commentLinkProcessor;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private List<HandlerRegistration> handlers = new ArrayList<HandlerRegistration>(4);
  private UpdateCheckTimer updateCheck;
  private Timestamp lastDisplayedUpdate;
  private UpdateAvailableBar updateAvailable;
  private boolean openReplyBox;

  @UiField Style style;
  @UiField ToggleButton star;
  @UiField Reload reload;
  @UiField AnchorElement permalink;

  @UiField Element reviewersText;
  @UiField Element ccText;
  @UiField Element changeIdText;
  @UiField Element ownerText;
  @UiField Element statusText;
  @UiField Element projectText;
  @UiField Element branchText;
  @UiField Element submitActionText;
  @UiField Element notMergeable;
  @UiField CopyableLabel idText;
  @UiField Topic topic;
  @UiField Element actionText;
  @UiField Element actionDate;

  @UiField Actions actions;
  @UiField Element revisionParent;
  @UiField ListBox revisionList;
  @UiField Labels labels;
  @UiField CommitBox commit;
  @UiField RelatedChanges related;
  @UiField FileTable files;
  @UiField FlowPanel history;

  @UiField Button reply;
  @UiField Button expandAll;
  @UiField Button collapseAll;
  @UiField Button editMessage;
  @UiField QuickApprove quickApprove;
  private ReplyAction replyAction;
  private EditMessageAction editMessageAction;

  public ChangeScreen2(Change.Id changeId, String revision, boolean openReplyBox) {
    this.changeId = changeId;
    this.revision = revision != null && !revision.isEmpty() ? revision : null;
    this.openReplyBox = openReplyBox;
    add(uiBinder.createAndBindUi(this));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    loadChangeInfo(true, new GerritCallback<ChangeInfo>() {
      @Override
      public void onSuccess(ChangeInfo info) {
        info.init();
        loadConfigInfo(info);
      }
    });
  }

  void loadChangeInfo(boolean fg, AsyncCallback<ChangeInfo> cb) {
    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(
        ListChangesOption.ALL_REVISIONS,
        ListChangesOption.CURRENT_ACTIONS));
    if (!fg) {
      call.background();
    }
    call.get(cb);
  }

  @Override
  protected void onUnload() {
    if (updateAvailable != null) {
      updateAvailable.hide(true);
      updateAvailable = null;
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
    labels.init(style, statusText);

    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysNavigation.add(new KeyCommand(0, 'u', Util.C.upToChangeList()) {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        Gerrit.displayLastChangeList();
      }
    });
    keysNavigation.add(new KeyCommand(0, 'R', Util.C.keyReload()) {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        reload.reload();
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
    if (Gerrit.isSignedIn()) {
      keysAction.add(new KeyCommand(0, 's', Util.C.changeTableStar()) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          star.setValue(!star.getValue(), true);
        }
      });
    }
  }

  private void initEditMessageAction() {
    NativeMap<ActionInfo> actions = changeInfo.revision(revision).actions();
    if (actions != null && actions.containsKey("message")) {
      editMessage.setVisible(true);
      editMessageAction = new EditMessageAction(
          changeInfo.legacy_id(),
          revision,
          changeInfo.revision(revision).commit().message(),
          style,
          editMessage,
          reply);
      keysAction.add(new KeyCommand(0, 'e', Util.C.keyEditMessage()) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          editMessageAction.onEdit();
        }
      });
    }
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    handlers.add(GlobalKey.add(this, keysNavigation));
    handlers.add(GlobalKey.add(this, keysAction));
    files.registerKeys();
    related.registerKeys();
  }

  @Override
  public void onShowView() {
    super.onShowView();

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

    startPoller();
  }

  private void scrollToPath(String token) {
    int s = token.indexOf('/');
    try {
      if (s < 0 || !changeId.equals(Change.Id.parse(token.substring(0, s)))) {
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

  @UiHandler("revisionList")
  void onChangeRevision(ChangeEvent e) {
    int idx = revisionList.getSelectedIndex();
    if (0 <= idx) {
      String n = revisionList.getValue(idx);
      revisionList.setEnabled(false);
      Gerrit.display(PageLinks.toChange2(changeId, n));
    }
  }

  @UiHandler("reply")
  void onReply(ClickEvent e) {
    onReply();
  }

  private void onReply() {
    if (Gerrit.isSignedIn()) {
      replyAction.onReply();
    } else {
      Gerrit.doSignIn(getToken());
    }
  }

  @UiHandler("editMessage")
  void onEditMessage(ClickEvent e) {
    editMessageAction.onEdit();
  }

  @UiHandler("expandAll")
  void onExpandAll(ClickEvent e) {
    int n = history.getWidgetCount();
    for (int i = 0; i < n; i++) {
      ((Message) history.getWidget(i)).setOpen(true);
    }
    expandAll.setVisible(false);
    collapseAll.setVisible(true);
  }

  @UiHandler("collapseAll")
  void onCollapseAll(ClickEvent e) {
    int n = history.getWidgetCount();
    for (int i = 0; i < n; i++) {
      ((Message) history.getWidget(i)).setOpen(false);
    }
    expandAll.setVisible(true);
    collapseAll.setVisible(false);
  }

  private void loadConfigInfo(final ChangeInfo info) {
    info.revisions().copyKeysIntoChildren("name");
    final RevisionInfo rev = resolveRevisionToDisplay(info);

    CallbackGroup group = new CallbackGroup();
    loadDiff(rev, myLastReply(info), group);
    loadCommit(rev, group);
    RevisionInfoCache.add(changeId, rev);
    ConfigInfoCache.add(info);
    ConfigInfoCache.get(info.project_name_key(),
      group.add(new ScreenLoadCallback<ConfigInfoCache.Entry>(this) {
        @Override
        protected void preDisplay(Entry result) {
          commentLinkProcessor = result.getCommentLinkProcessor();
          setTheme(result.getTheme());
          renderChangeInfo(info);
        }
      }));
    group.done();
  }

  private static Timestamp myLastReply(ChangeInfo info) {
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

  private void loadDiff(final RevisionInfo rev, final Timestamp myLastReply,
      CallbackGroup group) {
    final List<NativeMap<JsArray<CommentInfo>>> comments = loadComments(rev, group);
    final List<NativeMap<JsArray<CommentInfo>>> drafts = loadDrafts(rev, group);
    DiffApi.list(changeId.get(),
      rev.name(),
      group.add(new AsyncCallback<NativeMap<FileInfo>>() {
        @Override
        public void onSuccess(NativeMap<FileInfo> m) {
          files.setRevisions(null, new PatchSet.Id(changeId, rev._number()));
          files.setValue(m, myLastReply, comments.get(0), drafts.get(0));
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      }));

    if (Gerrit.isSignedIn()) {
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
    final List<NativeMap<JsArray<CommentInfo>>> r =
        new ArrayList<NativeMap<JsArray<CommentInfo>>>(1);
    ChangeApi.revision(changeId.get(), rev.name())
      .view("comments")
      .get(group.add(new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
        @Override
        public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
          r.add(result);
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      }));
    return r;
  }

  private List<NativeMap<JsArray<CommentInfo>>> loadDrafts(
      RevisionInfo rev, CallbackGroup group) {
    final List<NativeMap<JsArray<CommentInfo>>> r =
        new ArrayList<NativeMap<JsArray<CommentInfo>>>(1);
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
    ChangeApi.revision(changeId.get(), rev.name())
      .view("commit")
      .get(group.add(new AsyncCallback<CommitInfo>() {
        @Override
        public void onSuccess(CommitInfo info) {
          rev.set_commit(info);
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      }));
  }

  private void loadMergeable(final Change.Status status, final boolean canSubmit) {
    if (Gerrit.getConfig().testChangeMerge()) {
      ChangeApi.revision(changeId.get(), revision)
        .view("mergeable")
        .get(new AsyncCallback<MergeableInfo>() {
          @Override
          public void onSuccess(MergeableInfo result) {
            if (canSubmit) {
              actions.setSubmitEnabled(result.mergeable());
              if (status == Change.Status.NEW) {
                statusText.setInnerText(result.mergeable()
                    ? Util.C.readyToSubmit()
                    : Util.C.mergeConflict());
              }
            }
            setVisible(notMergeable, !result.mergeable());
            renderSubmitType(result.submit_type());
          }

          @Override
          public void onFailure(Throwable caught) {
            loadSubmitType(status, canSubmit);
          }
        });
    } else {
      loadSubmitType(status, canSubmit);
    }
  }

  private void loadSubmitType(final Change.Status status, final boolean canSubmit) {
    if (canSubmit) {
      actions.setSubmitEnabled(true);
      if (status == Change.Status.NEW) {
        statusText.setInnerText(Util.C.readyToSubmit());
      }
    }
    ChangeApi.revision(changeId.get(), revision)
      .view("submit_type")
      .get(new AsyncCallback<NativeString>() {
        @Override
        public void onSuccess(NativeString result) {
          renderSubmitType(result.asString());
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      });
  }

  private RevisionInfo resolveRevisionToDisplay(ChangeInfo info) {
    if (revision == null) {
      revision = info.current_revision();
    } else if (!info.revisions().containsKey(revision)) {
      JsArray<RevisionInfo> list = info.revisions().values();
      for (int i = 0; i < list.length(); i++) {
        RevisionInfo r = list.get(i);
        if (revision.equals(String.valueOf(r._number()))) {
          revision = r.name();
          break;
        }
      }
    }
    return info.revision(revision);
  }

  private void renderChangeInfo(ChangeInfo info) {
    changeInfo = info;
    lastDisplayedUpdate = info.updated();
    statusText.setInnerText(Util.toLongString(info.status()));
    boolean current = info.status().isOpen()
        && revision.equals(info.current_revision());
    boolean canSubmit = labels.set(info, current);

    renderOwner(info);
    renderReviewers(info);
    renderActionTextDate(info);
    renderRevisions(info);
    renderHistory(info);
    actions.display(info, revision);

    star.setValue(info.starred());
    permalink.setHref(ChangeLink.permalink(changeId));
    changeIdText.setInnerText(String.valueOf(info.legacy_id()));
    projectText.setInnerText(info.project());
    branchText.setInnerText(info.branch());
    idText.setText("Change-Id: " + info.change_id());
    idText.setPreviewText(info.change_id());
    reload.set(info);
    topic.set(info);
    commit.set(commentLinkProcessor, info, revision);
    related.set(info, revision);
    quickApprove.set(info, revision);

    if (Gerrit.isSignedIn()) {
      initEditMessageAction();
      replyAction = new ReplyAction(info, revision, style, reply);
      if (topic.canEdit()) {
        keysAction.add(new KeyCommand(0, 't', Util.C.keyEditTopic()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            topic.onEdit();
          }
        });
      }
    }
    if (current) {
      loadMergeable(info.status(), canSubmit);
    }

    StringBuilder sb = new StringBuilder();
    sb.append(Util.M.changeScreenTitleId(info.id_abbreviated()));
    if (info.subject() != null) {
      sb.append(": ");
      sb.append(info.subject());
    }
    setWindowTitle(sb.toString());
  }

  private void renderReviewers(ChangeInfo info) {
    // TODO Fix approximation of reviewers and CC list(s).
    Map<Integer, AccountInfo> r = new HashMap<Integer, AccountInfo>();
    Map<Integer, AccountInfo> cc = new HashMap<Integer, AccountInfo>();
    for (LabelInfo label : Natives.asList(info.all_labels().values())) {
      if (label.all() != null) {
        for (ApprovalInfo ai : Natives.asList(label.all())) {
          (ai.value() != 0 ? r : cc).put(ai._account_id(), ai);
        }
      }
    }
    for (Integer i : r.keySet()) {
      cc.remove(i);
    }
    r.remove(info.owner()._account_id());
    cc.remove(info.owner()._account_id());
    reviewersText.setInnerSafeHtml(labels.formatUserList(r.values()));
    ccText.setInnerSafeHtml(labels.formatUserList(cc.values()));
  }

  private void renderRevisions(ChangeInfo info) {
    if (info.revisions().size() == 1) {
      UIObject.setVisible(revisionParent, false);
      return;
    }

    JsArray<RevisionInfo> list = info.revisions().values();
    RevisionInfo.sortRevisionInfoByNumber(list);

    int selected = -1;
    for (int i = 0; i < list.length(); i++) {
      RevisionInfo r = list.get(i);
      revisionList.addItem(
          r._number() + ": " + r.name().substring(0, 6),
          "" + r._number());
      if (revision.equals(r.name())) {
        selected = i;
      }
    }
    if (0 <= selected) {
      revisionList.setSelectedIndex(selected);
    }
  }

  private void renderOwner(ChangeInfo info) {
    // TODO info card hover
    ownerText.setInnerText(info.owner().name() != null
        ? info.owner().name()
        : Gerrit.getConfig().getAnonymousCowardName());
  }

  private void renderSubmitType(String action) {
    try {
      SubmitType type = Project.SubmitType.valueOf(action);
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

  private void renderHistory(ChangeInfo info) {
    JsArray<MessageInfo> messages = info.messages();
    if (messages != null) {
      for (int i = 0; i < messages.length(); i++) {
        history.add(new Message(commentLinkProcessor, messages.get(i)));
      }
    }
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
          reload.reload();
        }

        void onIgnore(Timestamp newTime) {
          lastDisplayedUpdate = newTime;
        }
      };
      updateAvailable.addCloseHandler(new CloseHandler<PopupPanel>() {
        @Override
        public void onClose(CloseEvent<PopupPanel> event) {
          updateAvailable = null;
        }
      });
    }
    updateAvailable.set(
        Natives.asList(nm).subList(om.length(), nm.length()),
        newInfo.updated());
    if (!updateAvailable.isShowing()) {
      updateAvailable.popup();
    }
  }

  private void startPoller() {
    if (Gerrit.isSignedIn() && 0 < Gerrit.getConfig().getChangeUpdateDelay()) {
      updateCheck = new UpdateCheckTimer(this);
      updateCheck.schedule();
      handlers.add(UserActivityMonitor.addValueChangeHandler(updateCheck));
    }
  }
}
