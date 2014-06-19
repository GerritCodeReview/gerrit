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
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.PreselectDiffAgainst;
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
import com.google.gwtorm.client.KeyUtil;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ChangeScreen2 extends Screen {
  interface Binder extends UiBinder<HTMLPanel, ChangeScreen2> {}
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
  }

  static ChangeScreen2 get(NativeEvent in) {
    Element e = in.getEventTarget().cast();
    for (e = DOM.getParent(e); e != null; e = DOM.getParent(e)) {
      EventListener l = DOM.getEventListener(e);
      if (l instanceof ChangeScreen2) {
        return (ChangeScreen2) l;
      }
    }
    return null;
  }

  private final Change.Id changeId;
  private String base;
  private String basename;
  private String basename1;
  private String revision;
  private ChangeInfo changeInfo;
  private CommentLinkProcessor commentLinkProcessor;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private List<HandlerRegistration> handlers = new ArrayList<>(4);
  private UpdateCheckTimer updateCheck;
  private Timestamp lastDisplayedUpdate;
  private UpdateAvailableBar updateAvailable;
  private boolean openReplyBox;
  private boolean diffChange;
  private String panel;

  @UiField HTMLPanel headerLine;
  @UiField Style style;
  @UiField ToggleButton star;
  @UiField Anchor permalink;

  @UiField Element ccText;
  @UiField Reviewers reviewers;
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
  @UiField Button openAll;
  @UiField Button expandAll;
  @UiField Button collapseAll;
  @UiField Button editMessage;
  @UiField QuickApprove quickApprove;

  private ReplyAction replyAction;
  private EditMessageAction editMessageAction;
  private IncludedInAction includedInAction;
  private PatchSetsAction patchSetsAction;
  private DownloadAction downloadAction;

  public ChangeScreen2(Change.Id changeId, String base, String revision,
      boolean openReplyBox, String panel) {
    this.changeId = changeId;
    this.base = normalize(base);
    this.revision = normalize(revision);
    this.openReplyBox = openReplyBox;
    this.panel = panel;
    add(uiBinder.createAndBindUi(this));
  }

  Change.Id getChangeId() {
    return changeId;
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    loadChangeInfo(true, new GerritCallback<ChangeInfo>() {
      @Override
      public void onSuccess(ChangeInfo info) {
        info.init();
        ConfigInfoCache.add(info);
        loadConfigInfo(info, base);
      }
    });
  }

  void loadChangeInfo(boolean fg, AsyncCallback<ChangeInfo> cb) {
    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(
      ListChangesOption.CURRENT_ACTIONS,
      ListChangesOption.ALL_REVISIONS,
      ListChangesOption.WEB_LINKS));
    if (!fg) {
      call.background();
    }
    call.get(cb);
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
    labels.init(style, statusText);
    reviewers.init(style, ccText);

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

  private void initRevisionsAction(ChangeInfo info, String revision) {
    int currentPatchSet;
    if (info.current_revision() != null
        && info.revisions().containsKey(info.current_revision())) {
      currentPatchSet = info.revision(info.current_revision())._number();
    } else {
      JsArray<RevisionInfo> revList = info.revisions().values();
      RevisionInfo.sortRevisionInfoByNumber(revList);
      currentPatchSet = revList.get(revList.length() - 1)._number();
    }

    int currentlyViewedPatchSet = info.revision(revision)._number();
    patchSetsText.setInnerText(Resources.M.patchSets(
        currentlyViewedPatchSet, currentPatchSet));
    patchSetsAction = new PatchSetsAction(
        info.legacy_id(), revision,
        style, headerLine, patchSets);
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

  private void initEditMessageAction(ChangeInfo info, String revision) {
    NativeMap<ActionInfo> actions = info.revision(revision).actions();
    if (actions != null && actions.containsKey("message")) {
      editMessage.setVisible(true);
      editMessageAction = new EditMessageAction(
          info.legacy_id(),
          revision,
          info.revision(revision).commit().message(),
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
    startPoller();
    if (NewChangeScreenBar.show()) {
      add(new NewChangeScreenBar(changeId));
    }
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

  @UiHandler("includedIn")
  void onIncludedIn(ClickEvent e) {
    includedInAction.show();
  }

  @UiHandler("download")
  void onDownload(ClickEvent e) {
    downloadAction.show();
  }

  @UiHandler("patchSets")
  void onPatchSets(ClickEvent e) {
    patchSetsAction.show();
  }

  @UiHandler("reply")
  void onReply(ClickEvent e) {
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

  @UiHandler("editMessage")
  void onEditMessage(ClickEvent e) {
    editMessageAction.onEdit();
  }

  @UiHandler("openAll")
  void onOpenAll(ClickEvent e) {
    files.openAll();
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

  @UiHandler("diffBase")
  void onChangeRevision(ChangeEvent e) {
    diffChange = true;
    int idx = diffBase.getSelectedIndex();
    if (0 <= idx) {
      String n = diffBase.getValue(idx);
      RevisionInfo base = resolveRevisionOrPatchSetId(
          changeInfo,
          !n.isEmpty() ? n : null,
          null);
      RevisionInfoCache.add(changeId, base);
      MessageInfo last = myLastReply(changeInfo);
      CallbackGroup group = new CallbackGroup();
      loadDiff(base, changeInfo.revision(revision), last, group);
      group.done();
    }
  }

  private void loadConfigInfo(final ChangeInfo info, String baseName) {
    info.revisions().copyKeysIntoChildren("name");
    final MessageInfo last = myLastReply(info);
    final RevisionInfo rev = resolveRevisionToDisplay(info);
    PreselectDiffAgainst preselectRevision =
        Gerrit.getUserAccount().getGeneralPreferences().getPreselectRevision();
    if (preselectRevision == PreselectDiffAgainst.PREVIOUS_REVISION
        && panel == null && !diffChange) {
      JsArray<RevisionInfo> list = info.revisions().values();
      RevisionInfo.sortRevisionInfoByNumber(list);
      if (list.length() > 1) {
        for (int i = list.length() - 1; i >= 0; i--) {
          RevisionInfo r = list.get(i);
          if (r.name().equals(revision)) {
            basename = base = list.get(i - 1).name();
            break;
          }
        }
      }
    }

    if (preselectRevision == PreselectDiffAgainst.PRIOR_REVISION_I_LAST_COMMENTED_ON
        && last != null
        && 0 < last._revisionNumber() && last._revisionNumber() < rev._number()
        && panel == null && !diffChange) {
      basename1 = base = Integer.toString(last._revisionNumber());
    }
    diffChange = false;
    CallbackGroup group = new CallbackGroup();
    final RevisionInfo base;
    boolean loadDiff = true;
    if (baseName != null) {
      base = resolveRevisionOrPatchSetId(info, baseName, null);
    } else if (last != null
        && 0 < last._revisionNumber()
        && last._revisionNumber() < rev._number()) {
      base = resolveRevisionOrPatchSetId(info,
          Integer.toString(last._revisionNumber()), null);
      if (base != null) {
        loadDiff = false;
        loadCommit(base, group);
      }
    } else {
      base = null;
    }

    loadCommit(rev, group);
    RevisionInfoCache.add(changeId, base);
    RevisionInfoCache.add(changeId, rev);

    final ScreenLoadCallback<ConfigInfoCache.Entry> display =
      new ScreenLoadCallback<ConfigInfoCache.Entry>(this) {
        @Override
        protected void preDisplay(ConfigInfoCache.Entry result) {
          commentLinkProcessor = result.getCommentLinkProcessor();
          setTheme(result.getTheme());
          renderChangeInfo(info);
        }
      };

    final AsyncCallback<ConfigInfoCache.Entry> cb;
    if (loadDiff) {
      loadDiff(base, rev, last, group);
      cb = display;
    } else {
      cb =  new GerritCallback<ConfigInfoCache.Entry>() {
        @Override
        public void onSuccess(ConfigInfoCache.Entry result) {
          CallbackGroup group = new CallbackGroup();
          RevisionInfo b = null;
          if (sameParents(base, rev)) {
            String baseId = Integer.toString(base._number());
            ChangeScreen2.this.base = baseId;
            setToken(PageLinks.toChange(changeId, baseId,
                Integer.toString(rev._number())));
            b = base;
          }
          loadDiff(b, rev, last, group);
          group.addFinal(display).onSuccess(result);
        }
      };
    }
    ConfigInfoCache.get(info.project_name_key(), group.addFinal(cb));
  }

  static MessageInfo myLastReply(ChangeInfo info) {
    if (Gerrit.isSignedIn() && info.messages() != null) {
      int self = Gerrit.getUserAccountInfo()._account_id();
      for (int i = info.messages().length() - 1; i >= 0; i--) {
        MessageInfo m = info.messages().get(i);
        if (m.author() != null && m.author()._account_id() == self) {
          return m;
        }
      }
    }
    return null;
  }

  private void loadDiff(final RevisionInfo base, final RevisionInfo rev,
      final MessageInfo myLastReply, CallbackGroup group) {
    final List<NativeMap<JsArray<CommentInfo>>> comments = loadComments(rev, group);
    final List<NativeMap<JsArray<CommentInfo>>> drafts = loadDrafts(rev, group);
    DiffApi.list(changeId.get(),
      base != null ? base.name() : null,
      rev.name(),
      group.add(new AsyncCallback<NativeMap<FileInfo>>() {
        @Override
        public void onSuccess(NativeMap<FileInfo> m) {
          files.setRevisions(
              base != null ? new PatchSet.Id(changeId, base._number()) : null,
              new PatchSet.Id(changeId, rev._number()));
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
    if (rev.commit() == null) {
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

  private void renderChangeInfo(ChangeInfo info) {
    changeInfo = info;
    lastDisplayedUpdate = info.updated();
    boolean current = info.status().isOpen()
        && revision.equals(info.current_revision());

    if (!current && info.status() == Change.Status.NEW) {
      statusText.setInnerText(Util.C.notCurrent());
      labels.setVisible(false);
    } else {
      statusText.setInnerText(Util.toLongString(info.status()));
    }
    boolean canSubmit = labels.set(info, current);

    renderOwner(info);
    renderActionTextDate(info);
    renderDiffBaseListBox(info);
    initIncludedInAction(info);
    initRevisionsAction(info, revision);
    initDownloadAction(info, revision);
    initProjectLinks(info);
    initBranchLink(info);
    actions.display(info, revision);

    star.setValue(info.starred());
    permalink.setHref(ChangeLink.permalink(changeId));
    permalink.setText(String.valueOf(info.legacy_id()));
    topic.set(info, revision);
    commit.set(commentLinkProcessor, info, revision);
    related.set(info, revision);
    reviewers.set(info);

    if (Gerrit.isSignedIn()) {
      initEditMessageAction(info, revision);
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
      loadSubmitType(info.status(), canSubmit);
    } else {
      quickApprove.setVisible(false);
      setVisible(strategy, false);
    }

    StringBuilder sb = new StringBuilder();
    sb.append(Util.M.changeScreenTitleId(info.id_abbreviated()));
    if (info.subject() != null) {
      sb.append(": ");
      sb.append(info.subject());
    }
    setWindowTitle(sb.toString());
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
    PreselectDiffAgainst preselectRevision =
        Gerrit.getUserAccount().getGeneralPreferences().getPreselectRevision();
    JsArray<RevisionInfo> list = info.revisions().values();
    RevisionInfo.sortRevisionInfoByNumber(list);
    int selectedIdx = list.length();
    for (int i = list.length() - 1; i >= 0; i--) {
      RevisionInfo r = list.get(i);
      String id = String.valueOf(r._number());
      diffBase.addItem(id + ": " + r.name().substring(0, 6), r.name());
      if (r.name().equals(revision)) {
        SelectElement.as(diffBase.getElement()).getOptions()
            .getItem(diffBase.getItemCount() - 1).setDisabled(true);
      }
      if (basename != null
          && preselectRevision == PreselectDiffAgainst.PREVIOUS_REVISION
          && r.name().equals(revision)) {
        selectedIdx = diffBase.getItemCount();
      }
      if (basename1 != null
          && preselectRevision == PreselectDiffAgainst.PRIOR_REVISION_I_LAST_COMMENTED_ON
          && String.valueOf(r._number()).equals(basename1)) {
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

  private static boolean sameParents(RevisionInfo a, RevisionInfo b) {
    if (a != null && b != null && a.commit() != null && b.commit() != null) {
      JsArray<CommitInfo> aParents = a.commit().parents();
      JsArray<CommitInfo> bParents = b.commit().parents();
      if (aParents != null && bParents != null
          && aParents.length() == bParents.length()) {
        for (int i = 0; i < aParents.length(); i++) {
          if (!aParents.get(i).commit().equals(bParents.get(i).commit())) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }
}
