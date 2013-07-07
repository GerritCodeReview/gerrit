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
import com.google.gerrit.client.UserPopupPanel;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.ChangeInfo.GitPerson;
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
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;
import com.google.gwt.user.client.ui.Widget;
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
  private static final String DATA_ACCOUNT_ID = "data-account-id";

  interface Binder extends UiBinder<HTMLPanel, ChangeScreen2> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  interface Styles extends CssResource {
    String labelName();
    String label_user();
    String label_ok();
    String label_reject();
    String label_may();
    String label_need();
  }

  private final Change.Id changeId;
  private CommentLinkProcessor commentLinkProcessor;
  private boolean starred;
  private Map<String, AccountInfo> accountCache;

  private UserPopupPanel userPopupPanel;
  private AccountInfo userPopupInfo;

  @UiField Styles style;
  @UiField Image starIcon;
  @UiField Image reload;
  @UiField AnchorElement permalink;

  @UiField Element reviewersText;
  @UiField Element ccText;
  @UiField Element changeIdText;
  @UiField Element ownerText;
  @UiField Element statusText;
  @UiField Element projectText;
  @UiField Element branchText;
  @UiField Element topicText;
  @UiField Element submitTypeText;
  @UiField Element notMergeable;
  @UiField Element idText;
  @UiField Element actionText;
  @UiField Element actionDate;

  @UiField Element commitName;
  @UiField Element authorNameEmail;
  @UiField Element authorDate;
  @UiField Element committerNameEmail;
  @UiField Element committerDate;
  @UiField Element commitMessageText;

  @UiField Actions actions;
  @UiField FlowPanel history;
  @UiField Grid labels;
  @UiField FileTable files;

  public ChangeScreen2(Change.Id changeId) {
    this.changeId = changeId;
    add(uiBinder.createAndBindUi(this));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    setHeaderVisible(false);
    ChangeApi.detail(changeId.get(),
      EnumSet.of(
        ListChangesOption.CURRENT_REVISION,
        ListChangesOption.CURRENT_COMMIT),
      new GerritCallback<ChangeInfo>() {
        @Override
        public void onSuccess(ChangeInfo info) {
          loadConfigInfo(info);
        }
      });
  }

  @UiHandler("starIcon")
  void onToggleStar(ClickEvent e) {
    boolean newState = !starred;
    StarredChanges.toggleStar(changeId, newState);
    renderStar(newState);
  }

  @UiHandler("reload")
  void onReload(ClickEvent e) {
    Gerrit.display(PageLinks.toChange2(changeId));
    e.preventDefault();
    e.stopPropagation();
  }

  void onLabelHover(MouseOverEvent event) {
    EventTarget target = event.getNativeEvent().getEventTarget();
    if (!Element.is(target) || true) {
      return;
    }

    final Element elem = Element.as(target);
    AccountInfo info = accountCache.get(elem.getAttribute(DATA_ACCOUNT_ID));
    if (info == null) {
      return;
    }

    if (userPopupInfo == info) {
      return;
    } else if (userPopupPanel != null) {
      userPopupPanel.hide();
    }

    final UserPopupPanel p = new UserPopupPanel(info, false, false);
    p.setPopupPositionAndShow(new PositionCallback() {
      public void setPosition(int offsetWidth, int offsetHeight) {
        p.setPopupPosition(elem.getAbsoluteLeft(), elem.getAbsoluteBottom() + 5);
      }
    });
    userPopupPanel = p;
    userPopupInfo = info;
  }

  private void loadConfigInfo(final ChangeInfo info) {
    CallbackGroup group = new CallbackGroup();
    DiffApi.list(changeId.get(), info.current_revision(),
      group.add(new AsyncCallback<NativeMap<FileInfo>>() {
        @Override
        public void onSuccess(NativeMap<FileInfo> m) {
          int c = info.revision(info.current_revision())._number();
          files.setRevisions(
              null,
              new PatchSet.Id(changeId, c));
          files.setValue(m);
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      }));
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

  private void renderChangeInfo(ChangeInfo info) {
    accountCache = new HashMap<String, AccountInfo>();
    statusText.setInnerText(Util.toLongString(info.status()));
    boolean canSubmit = renderLabels(info);

    renderStar(info.starred());
    renderOwner(info);
    renderActionTextDate(info);
    renderCommitInfo(info);
    renderHistory(info);
    actions.display(info, canSubmit);

    permalink.setHref(ChangeLink.permalink(changeId));
    changeIdText.setInnerText(String.valueOf(info.legacy_id()));
    projectText.setInnerText(info.project());
    branchText.setInnerText(info.branch());
    topicText.setInnerText(info.topic());
    idText.setInnerText(info.change_id());

    // TODO(sop) submit_type
    submitTypeText.setInnerText(
        com.google.gerrit.client.admin.Util.C.projectSubmitType_MERGE_IF_NECESSARY());

    boolean hasConflict = Gerrit.getConfig().testChangeMerge() && !info.mergeable();
    setVisible(notMergeable, hasConflict);

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

  private boolean renderLabels(ChangeInfo info) {
    List<String> names = new ArrayList<String>(info.labels());
    Collections.sort(names);

    Map<Integer, AccountInfo> r = new HashMap<Integer, AccountInfo>();
    Map<Integer, AccountInfo> cc = new HashMap<Integer, AccountInfo>();

    boolean canSubmit = info.status().isOpen();
    labels.resize(names.size(), 2);
    labels.addDomHandler(new MouseOverHandler() {
      @Override
      public void onMouseOver(MouseOverEvent event) {
        onLabelHover(event);
      }
    }, MouseOverEvent.getType());

    for (int row = 0; row < names.size(); row++) {
      String name = names.get(row);
      LabelInfo label = info.label(name);
      labels.setText(row, 0, name);
      if (label.all() != null) {
        // TODO Fix approximation of reviewers and CC list(s).
        for (ApprovalInfo ai : Natives.asList(label.all())) {
          (ai.value() != 0 ? r : cc).put(ai._account_id(), ai);
        }
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
    }

    for (Integer i : r.keySet()) {
      cc.remove(i);
    }
    reviewersText.setInnerSafeHtml(formatUserList(r.values()));
    ccText.setInnerSafeHtml(formatUserList(cc.values()));
    return canSubmit;
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
      String key = String.valueOf(ai._account_id());
      accountCache.put(key, ai);

      html.openSpan();
      html.setStyleName(style.label_user());
      html.setAttribute(DATA_ACCOUNT_ID, key);
      if (ai.name() != null) {
        html.append(ai.name());
      } else if (ai.email() != null) {
        html.append(ai.email());
      } else {
        html.append(key);
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
    String revision = change.current_revision();
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

  private void renderOwner(ChangeInfo info) {
    // TODO info card hover
    ownerText.setInnerText(info.owner().name() != null
        ? info.owner().name()
        : Gerrit.getConfig().getAnonymousCowardName());
  }

  private void renderStar(boolean starred) {
    Element img = starIcon.getElement();
    if (starred) {
      starIcon.setResource(Gerrit.RESOURCES.starFilled());
      img.setAttribute("aria-hidden", "false");
      img.getStyle().setVisibility(Visibility.VISIBLE);
    } else if (Gerrit.isSignedIn()) {
      starIcon.setResource(Gerrit.RESOURCES.starOpen());
      img.setAttribute("aria-hidden", "false");
      img.getStyle().setVisibility(Visibility.VISIBLE);
    } else {
      img.setAttribute("aria-hidden", "true");
      img.getStyle().setVisibility(Visibility.HIDDEN);
    }
    this.starred = starred;
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
