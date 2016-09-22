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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.NotSignedInDialog;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.RemoteSuggestBox;
import com.google.gerrit.reviewdb.client.Change;
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
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.UIObject;

/**
 * Edit assignee using auto-completion.
 */
public class Assignee extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Assignee> {
  }

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField
  InlineHyperlink assigneeLink;
  @UiField
  Image editAssigneeIcon;
  @UiField
  Element form;
  @UiField
  Element error;
  @UiField(provided = true)
  RemoteSuggestBox suggestBox;

  private AssigneeSuggestOracle assigneeSuggestOracle;
  private Change.Id changeId;

  Assignee() {
    assigneeSuggestOracle = new AssigneeSuggestOracle();
    suggestBox = new RemoteSuggestBox(assigneeSuggestOracle);
    suggestBox.setVisibleLength(55);
    suggestBox.setHintText(Util.C.approvalTableEditAssigneeHint());
    suggestBox.addCloseHandler(new CloseHandler<RemoteSuggestBox>() {
      @Override
      public void onClose(CloseEvent<RemoteSuggestBox> event) {
        Assignee.this.onCancel(null);
      }
    });
    suggestBox.addSelectionHandler(new SelectionHandler<String>() {
      @Override
      public void onSelection(SelectionEvent<String> event) {
        editAssignee(event.getSelectedItem());
      }
    });

    initWidget(uiBinder.createAndBindUi(this));
    editAssigneeIcon.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        onOpenForm();
      }
    }, ClickEvent.getType());
  }

  void set(ChangeInfo info) {
    this.changeId = info.legacyId();
    assigneeLink.setText(info.assignee() != null ? info.assignee().name() : "");
    assigneeSuggestOracle.setChange(changeId);
    editAssigneeIcon.setVisible(Gerrit.isSignedIn());
  }

  void onOpenForm() {
    UIObject.setVisible(form, true);
    UIObject.setVisible(error, false);
    editAssigneeIcon.setVisible(false);
    suggestBox.setFocus(true);
    suggestBox.setText("");
  }

  void onCloseForm() {
    UIObject.setVisible(form, false);
    UIObject.setVisible(error, false);
    editAssigneeIcon.setVisible(true);
    suggestBox.setFocus(false);
  }

  @UiHandler("assign")
  void onEditAssignee(@SuppressWarnings("unused") ClickEvent e) {
    editAssignee(suggestBox.getText());
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    onCloseForm();
  }

  private void editAssignee(final String assignee) {
    if (assignee.isEmpty()) {
      ChangeApi.deleteAssignee(changeId.get(),
          new GerritCallback<AccountInfo>() {
            @Override
            public void onSuccess(AccountInfo result) {
              onCloseForm();
              assigneeLink.setText("");
            }

            @Override
            public void onFailure(Throwable err) {
              if (isSigninFailure(err)) {
                new NotSignedInDialog().center();
              } else {
                UIObject.setVisible(error, true);
                error.setInnerText(err instanceof StatusCodeException
                    ? ((StatusCodeException) err).getEncodedResponse()
                    : err.getMessage());
              }
            }
          });
    } else {
      ChangeApi.setAssignee(changeId.get(), assignee,
          new GerritCallback<AccountInfo>() {
            @Override
            public void onSuccess(AccountInfo result) {
              onCloseForm();
              assigneeLink.setText(result.name());
            }

            @Override
            public void onFailure(Throwable err) {
              if (isSigninFailure(err)) {
                new NotSignedInDialog().center();
              } else {
                UIObject.setVisible(error, true);
                error.setInnerText(err instanceof StatusCodeException
                    ? ((StatusCodeException) err).getEncodedResponse()
                    : err.getMessage());
              }
            }
          });
    }
  }
}
