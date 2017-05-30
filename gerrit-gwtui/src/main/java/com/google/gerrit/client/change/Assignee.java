// Copyright (C) 2016 The Android Open Source Project
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
import com.google.gerrit.client.NotSignedInDialog;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.RemoteSuggestBox;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.UIObject;

/** Edit assignee using auto-completion. */
public class Assignee extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Assignee> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Element show;
  @UiField InlineHyperlink assigneeLink;
  @UiField Image editAssigneeIcon;
  @UiField Element form;
  @UiField Element error;

  @UiField(provided = true)
  RemoteSuggestBox suggestBox;

  private AssigneeSuggestOracle assigneeSuggestOracle;
  private Change.Id changeId;
  private Project.NameKey project;
  private boolean canEdit;
  private AccountInfo currentAssignee;

  Assignee() {
    assigneeSuggestOracle = new AssigneeSuggestOracle();
    suggestBox = new RemoteSuggestBox(assigneeSuggestOracle);
    suggestBox.setVisibleLength(55);
    suggestBox.setHintText(Util.C.approvalTableEditAssigneeHint());
    suggestBox.addCloseHandler(
        new CloseHandler<RemoteSuggestBox>() {
          @Override
          public void onClose(CloseEvent<RemoteSuggestBox> event) {
            Assignee.this.onCancel(null);
          }
        });
    suggestBox.addSelectionHandler(
        new SelectionHandler<String>() {
          @Override
          public void onSelection(SelectionEvent<String> event) {
            editAssignee(event.getSelectedItem());
          }
        });

    initWidget(uiBinder.createAndBindUi(this));
    editAssigneeIcon.addDomHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            onOpenForm();
          }
        },
        ClickEvent.getType());
  }

  void set(ChangeInfo info) {
    this.changeId = info.legacyId();
    this.project = info.projectNameKey();
    this.canEdit = info.hasActions() && info.actions().containsKey("assignee");
    setAssignee(info.assignee());
    editAssigneeIcon.setVisible(canEdit);
    if (!canEdit) {
      show.setTitle(null);
    }
  }

  void onOpenForm() {
    UIObject.setVisible(form, true);
    UIObject.setVisible(show, false);
    UIObject.setVisible(error, false);
    editAssigneeIcon.setVisible(false);
    suggestBox.setFocus(true);
    if (currentAssignee != null) {
      suggestBox.setText(FormatUtil.nameEmail(currentAssignee));
      suggestBox.selectAll();
    } else {
      suggestBox.setText("");
    }
  }

  void onCloseForm() {
    UIObject.setVisible(form, false);
    UIObject.setVisible(show, true);
    UIObject.setVisible(error, false);
    editAssigneeIcon.setVisible(true);
    suggestBox.setFocus(false);
  }

  @UiHandler("assign")
  void onEditAssignee(@SuppressWarnings("unused") ClickEvent e) {
    if (canEdit) {
      editAssignee(suggestBox.getText());
    }
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    onCloseForm();
  }

  private void editAssignee(final String assignee) {
    if (assignee.trim().isEmpty()) {
      ChangeApi.deleteAssignee(
          changeId.get(),
          Project.NameKey.asStringOrNull(project),
          new GerritCallback<AccountInfo>() {
            @Override
            public void onSuccess(AccountInfo result) {
              onCloseForm();
              setAssignee(null);
            }

            @Override
            public void onFailure(Throwable err) {
              if (isSigninFailure(err)) {
                new NotSignedInDialog().center();
              } else {
                UIObject.setVisible(error, true);
                error.setInnerText(
                    err instanceof StatusCodeException
                        ? ((StatusCodeException) err).getEncodedResponse()
                        : err.getMessage());
              }
            }
          });
    } else {
      ChangeApi.setAssignee(
          changeId.get(),
          Project.NameKey.asStringOrNull(project),
          assignee,
          new GerritCallback<AccountInfo>() {
            @Override
            public void onSuccess(AccountInfo result) {
              onCloseForm();
              setAssignee(result);
              Reviewers reviewers = getReviewers();
              if (reviewers != null) {
                reviewers.updateReviewerList();
              }
            }

            @Override
            public void onFailure(Throwable err) {
              if (isSigninFailure(err)) {
                new NotSignedInDialog().center();
              } else {
                UIObject.setVisible(error, true);
                error.setInnerText(
                    err instanceof StatusCodeException
                        ? ((StatusCodeException) err).getEncodedResponse()
                        : err.getMessage());
              }
            }
          });
    }
  }

  private void setAssignee(AccountInfo assignee) {
    currentAssignee = assignee;
    assigneeLink.setText(assignee != null ? getName(assignee) : null);
    assigneeLink.setTargetHistoryToken(
        assignee != null
            ? PageLinks.toAssigneeQuery(
                assignee.name() != null
                    ? assignee.name()
                    : assignee.email() != null
                        ? assignee.email()
                        : String.valueOf(assignee._accountId()))
            : "");
  }

  private Reviewers getReviewers() {
    Element e = DOM.getParent(getElement());
    for (e = DOM.getParent(e); e != null; e = DOM.getParent(e)) {
      EventListener l = DOM.getEventListener(e);
      if (l instanceof ChangeScreen) {
        ChangeScreen screen = (ChangeScreen) l;
        return screen.reviewers;
      }
    }
    return null;
  }

  private String getName(AccountInfo info) {
    if (info.name() != null) {
      return info.name();
    }
    if (info.email() != null) {
      return info.email();
    }
    return Gerrit.info().user().anonymousCowardName();
  }
}
