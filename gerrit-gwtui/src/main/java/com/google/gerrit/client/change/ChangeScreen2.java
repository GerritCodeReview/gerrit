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
import com.google.gerrit.client.changes.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.ChangeInfo.MessageInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
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
import com.google.gwt.user.client.ui.ToggleButton;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
  private CommentLinkProcessor commentLinkProcessor;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private List<HandlerRegistration> keys = new ArrayList<HandlerRegistration>(2);

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
  @UiField FileTable files;
  @UiField FlowPanel history;

  @UiField Button reply;
  @UiField QuickApprove quickApprove;
  private ReplyAction replyAction;

  public ChangeScreen2(Change.Id changeId, String revision) {
    this.changeId = changeId;
    this.revision = revision != null && !revision.isEmpty() ? revision : null;
    add(uiBinder.createAndBindUi(this));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    ChangeApi.detail(changeId.get(),
      EnumSet.of(
          ListChangesOption.ALL_REVISIONS,
          ListChangesOption.CURRENT_ACTIONS),
      new GerritCallback<ChangeInfo>() {
        @Override
        public void onSuccess(ChangeInfo info) {
          info.init();
          loadConfigInfo(info);
        }
      });
  }

  @Override
  protected void onUnload() {
    for (HandlerRegistration h : keys) {
      h.removeHandler();
    }
    keys.clear();
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
    if (Gerrit.isSignedIn()) {
      keysAction.add(new KeyCommand(0, 'r', Util.C.keyPublishComments()) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          onReply(null);
        }
      });
      keysAction.add(new KeyCommand(0, 's', Util.C.changeTableStar()) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          star.setValue(!star.getValue(), true);
        }
      });
    }
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    keys.add(GlobalKey.add(this, keysNavigation));
    keys.add(GlobalKey.add(this, keysAction));
    files.registerKeys();
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
      Gerrit.display(
          PageLinks.toChange2(changeId, n),
          new ChangeScreen2(changeId, n));
    }
  }

  @UiHandler("reply")
  void onReply(ClickEvent e) {
    replyAction.onReply();
  }

  private void loadConfigInfo(final ChangeInfo info) {
    info.revisions().copyKeysIntoChildren("name");
    final RevisionInfo rev = resolveRevisionToDisplay(info);

    CallbackGroup group = new CallbackGroup();
    loadDiff(rev, group);
    loadCommit(rev, group);
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

    if (info.status().isOpen() && rev.name().equals(info.current_revision())) {
      loadSubmitAction(rev);
    }
  }

  private void loadDiff(final RevisionInfo rev, CallbackGroup group) {
    DiffApi.list(changeId.get(),
      rev.name(),
      group.add(new AsyncCallback<NativeMap<FileInfo>>() {
        @Override
        public void onSuccess(NativeMap<FileInfo> m) {
          files.setRevisions(null, new PatchSet.Id(changeId, rev._number()));
          files.setValue(m);
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      }));
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

  private void loadSubmitAction(final RevisionInfo rev) {
    // Submit action is less important than other data.
    // Defer so browser can start other requests first.
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        ChangeApi.revision(changeId.get(), rev.name())
          .view("submit_type")
          .get(new AsyncCallback<NativeString>() {
            @Override
            public void onSuccess(NativeString result) {
              String action = result.asString();
              try {
                SubmitType type = Project.SubmitType.valueOf(action);
                submitActionText.setInnerText(
                    com.google.gerrit.client.admin.Util.toLongString(type));
              } catch (IllegalArgumentException e) {
                submitActionText.setInnerText(action);
              }
            }

            @Override
            public void onFailure(Throwable caught) {
            }
          });
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
    statusText.setInnerText(Util.toLongString(info.status()));
    boolean canSubmit = labels.set(info);

    renderOwner(info);
    renderReviewers(info);
    renderActionTextDate(info);
    renderRevisions(info);
    renderHistory(info);
    actions.display(info, revision, canSubmit);

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
    quickApprove.set(info, revision);

    boolean hasConflict = Gerrit.getConfig().testChangeMerge() && !info.mergeable();
    setVisible(notMergeable, hasConflict);

    if (Gerrit.isSignedIn()) {
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
    reply.setVisible(replyAction != null);

    if (canSubmit && !hasConflict && actions.isSubmitEnabled()) {
      statusText.setInnerText(Util.C.readyToSubmit());
    } else if (canSubmit && hasConflict) {
      statusText.setInnerText(Util.C.mergeConflict());
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
    reviewersText.setInnerSafeHtml(labels.formatUserList(r.values()));
    ccText.setInnerSafeHtml(labels.formatUserList(cc.values()));
  }

  private void renderRevisions(ChangeInfo info) {
    if (info.revisions().size() == 1) {
      UIObject.setVisible(revisionParent, false);
      return;
    }

    JsArray<RevisionInfo> list = info.revisions().values();
    Collections.sort(Natives.asList(list), new Comparator<RevisionInfo>() {
      @Override
      public int compare(RevisionInfo a, RevisionInfo b) {
        return a._number() - b._number();
      }
    });

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
}
