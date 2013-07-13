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
import com.google.gerrit.client.changes.ChangeInfo.GitPerson;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.ChangeInfo.MessageInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ReviewInput;
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
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.SubmitRecord.Label.Status;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
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
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.ToggleButton;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChangeScreen2 extends Screen {
  interface Binder extends UiBinder<HTMLPanel, ChangeScreen2> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  interface Styles extends CssResource {
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

  @UiField Styles style;
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

  @UiField Element commitName;
  @UiField Element authorNameEmail;
  @UiField Element authorDate;
  @UiField Element committerNameEmail;
  @UiField Element committerDate;
  @UiField Element commitMessageText;

  @UiField Actions actions;
  @UiField ListBox revisionList;
  @UiField FlowPanel history;
  @UiField Grid labels;
  @UiField FileTable files;

  @UiField Button reply;
  private ReplyAction replyAction;

  @UiField Button quickApprove;
  @UiField Element quickApproveText;
  private ReviewInput quickApproveInput;

  public ChangeScreen2(Change.Id changeId, String revision) {
    this.changeId = changeId;
    this.revision = revision != null && !revision.isEmpty() ? revision : null;
    add(uiBinder.createAndBindUi(this));
    star.setVisible(Gerrit.isSignedIn());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    setHeaderVisible(false);

    ChangeApi.detail(changeId.get(),
      EnumSet.of(
          ListChangesOption.ALL_REVISIONS,
          ListChangesOption.CURRENT_ACTIONS),
      new GerritCallback<ChangeInfo>() {
        @Override
        public void onSuccess(ChangeInfo info) {
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
          onToggleStar(null);
        }
      });
    }

    Resources r = GWT.create(Resources.class);
    r.style().ensureInjected();
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    keys.add(GlobalKey.add(this, keysNavigation));
    keys.add(GlobalKey.add(this, keysAction));
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

  @UiHandler("quickApprove")
  void onApprove(ClickEvent e) {
    ChangeApi.revision(changeId.get(), revision)
      .view("review")
      .post(quickApproveInput, new GerritCallback<ReviewInput>() {
        @Override
        public void onSuccess(ReviewInput result) {
          Gerrit.display(PageLinks.toChange2(changeId));
        }
      });
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
    boolean canSubmit = renderLabels(info);

    renderOwner(info);
    renderReviewers(info);
    renderActionTextDate(info);
    renderCommitInfo(info);
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
    reviewersText.setInnerSafeHtml(formatUserList(r.values()));
    ccText.setInnerSafeHtml(formatUserList(cc.values()));
  }

  private boolean renderLabels(ChangeInfo info) {
    List<String> names = new ArrayList<String>(info.labels());
    Collections.sort(names);

    boolean canSubmit = info.status().isOpen();
    labels.resize(names.size(), 2);

    for (int row = 0; row < names.size(); row++) {
      String name = names.get(row);
      LabelInfo label = info.label(name);
      labels.setText(row, 0, name);
      if (label.all() != null) {
        labels.setWidget(row, 1, renderUsers(label));
      }
      labels.getCellFormatter().setStyleName(row, 0, style.labelName());
      labels.getCellFormatter().addStyleName(row, 0, getStyleForLabel(label));

      if (canSubmit && info.status() == Change.Status.NEW) {
        switch (label.status()) {
          case NEED:
            statusText.setInnerText("Needs " + name);
            canSubmit = false;
            break;
          case REJECT:
          case IMPOSSIBLE:
            statusText.setInnerText("Not " + name);
            canSubmit = false;
            break;
          default:
            break;
          }
      }
      setupQuickApprove(info, name, label);
    }

    if (quickApproveInput == null) {
      quickApprove.setVisible(false);
    } else if (!quickApprove.isVisible()) {
      quickApprove = null;
    }
    return canSubmit;
  }

  private void setupQuickApprove(ChangeInfo info, String name, LabelInfo label) {
    if (info.has_permitted_labels()
        && info.permitted_labels().containsKey(name)
        && info.status().isOpen()
        && label.status() == Status.NEED) {
      JsArrayString values = info.permitted_values(name);
      if (values.length() > 0) {
        String vStr = values.get(values.length() - 1);
        short v = LabelInfo.parseValue(vStr);
        if (v > 0 && vStr.equals(label.max_value())) {
          if (quickApproveInput == null) {
            quickApproveInput = ReviewInput.create();
            quickApproveInput.label(name, v);
            quickApproveText.setInnerText(name + vStr);
          } else {
            quickApprove.setVisible(false);
          }
        }
      }
    }
  }

  private Widget renderUsers(LabelInfo label) {
    Map<Integer, List<ApprovalInfo>> m = new HashMap<Integer, List<ApprovalInfo>>(4);
    int approved = 0, rejected = 0;

    for (ApprovalInfo ai : Natives.asList(label.all())) {
      if (ai.value() != 0) {
        List<ApprovalInfo> l = m.get(Integer.valueOf(ai.value()));
        if (l == null) {
          l = new ArrayList<ApprovalInfo>(label.all().length());
          m.put(Integer.valueOf(ai.value()), l);
        }
        l.add(ai);

        if (isRejected(label, ai)) {
          rejected = ai.value();
        } else if (isApproved(label, ai)) {
          approved = ai.value();
        }
      }
    }

    SafeHtmlBuilder html = new SafeHtmlBuilder();
    for (Integer v : sort(m.keySet(), approved, rejected)) {
      if (!html.isEmpty()) {
        html.append("; ");
      }

      String val = LabelValue.formatValue(v.shortValue());
      html.openSpan();
      html.setAttribute("title", label.value_text(val));
      if (v.intValue() == approved) {
        html.setStyleName(style.label_ok());
      } else if (v.intValue() == rejected) {
        html.setStyleName(style.label_reject());
      }
      html.append(val).append(" ");
      html.append(formatUserList(m.get(v)));
      html.closeSpan();
    }
    return html.toBlockWidget();
  }

  private SafeHtml formatUserList(Collection<? extends AccountInfo> in) {
    List<AccountInfo> users = new ArrayList<AccountInfo>(in);
    Collections.sort(users, new Comparator<AccountInfo>() {
      @Override
      public int compare(AccountInfo a, AccountInfo b) {
        return name(a).compareTo(name(b));
      }

      private String name(AccountInfo a) {
        if (a.name() != null) {
          return a.name();
        } else if (a.email() != null) {
          return a.email();
        } else {
          return "";
        }
      }
    });

    SafeHtmlBuilder html = new SafeHtmlBuilder();
    Iterator<? extends AccountInfo> itr = users.iterator();
    while (itr.hasNext()) {
      AccountInfo ai = itr.next();
      html.openSpan();
      html.setStyleName(style.label_user());
      if (ai.name() != null) {
        html.append(ai.name());
      } else if (ai.email() != null) {
        html.append(ai.email());
      } else {
        html.append(ai._account_id());
      }
      html.closeSpan();
      if (itr.hasNext()) {
        html.append(", ");
      }
    }
    return html;
  }

  private static List<Integer> sort(Set<Integer> keySet, int a, int b) {
    List<Integer> r = new ArrayList<Integer>(keySet);
    Collections.sort(r);
    if (keySet.contains(a)) {
      r.remove(Integer.valueOf(a));
      r.add(0, a);
    } else if (keySet.contains(b)) {
      r.remove(Integer.valueOf(b));
      r.add(0, b);
    }
    return r;
  }

  private static boolean isApproved(LabelInfo label, ApprovalInfo ai) {
    return label.approved() != null
        && label.approved()._account_id() == ai._account_id();
  }

  private static boolean isRejected(LabelInfo label, ApprovalInfo ai) {
    return label.rejected() != null
        && label.rejected()._account_id() == ai._account_id();
  }

  private String getStyleForLabel(LabelInfo label) {
    switch (label.status()) {
      case OK:
        return style.label_ok();
      case NEED:
        return style.label_need();
      case REJECT:
      case IMPOSSIBLE:
        return style.label_reject();
      default:
      case MAY:
        return style.label_may();
    }
  }

  private void renderCommitInfo(ChangeInfo change) {
    RevisionInfo revInfo = change.revision(revision);
    CommitInfo commit = revInfo.commit();

    commitName.setInnerText(revision);
    format(commit.author(), authorNameEmail, authorDate);
    format(commit.committer(), committerNameEmail, committerDate);
    commitMessageText.setInnerSafeHtml(commentLinkProcessor.apply(
        new SafeHtmlBuilder().append(commit.message()).linkify()));
  }

  private void format(GitPerson person, Element name, Element date) {
    name.setInnerText(person.name() + " <" + person.email() + ">");
    date.setInnerText(FormatUtil.mediumFormat(person.date()));
  }

  private void renderRevisions(ChangeInfo info) {
    if (info.revisions().size() == 1) {
      revisionList.setVisible(false);
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
