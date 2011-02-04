// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.client.searches;

import com.google.gerrit.client.ui.AccountSuggestOracle;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.RPCSuggestOracle;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.SuggestUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.reviewdb.Owner;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;

public class OwnerScreen extends Screen {
  protected Owner.Id ownerId;

  protected HintTextBox owner;
  protected SuggestBox ownerSug;
  protected Button browse;

  private boolean submitOnSelection;

  protected OwnerScreen(final Owner.Id id) {
    super();
    ownerId = id;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    owner = createOwner();
    if (owner != null) {
      ownerSug = new SuggestBox(new RPCSuggestOracle(createOwnerOracle()), owner);
      ownerSug.addSelectionHandler(new SelectionHandler<Suggestion>() {
        @Override
        public void onSelection(SelectionEvent<Suggestion> event) {
          if (submitOnSelection) {
            submitOnSelection = false;
            onBrowse();
          }
        }
      });

      browse = new Button(Util.C.buttonBrowseSearches());
      browse.addClickHandler(new ClickHandler() {
        @Override
          public void onClick(ClickEvent event) {
            onBrowse();
          }
        });

      owner.addKeyPressHandler(new KeyPressHandler() {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            submitOnSelection = false;

            if (event.getCharCode() == KeyCodes.KEY_ENTER) {
              if (ownerSug.isSuggestionListShowing()) {
                submitOnSelection = true;
              } else {
                onBrowse();
              }
            }
          }
        });

      add(new Label(Util.C.headingBrowse()));
      add(ownerSug);
      add(browse);
    }
  }

  protected HintTextBox createOwner() {
    HintTextBox owner = new HintTextBox();
    switch(ownerId.getType()) {
      case USER:    owner.setHintText(Util.C.defaultAccountName());
        break;
      case GROUP:   owner.setHintText(Util.C.defaultAccountGroupName());
        break;
      case PROJECT: owner.setHintText(Util.C.defaultProjectName());
        break;
      case SITE:    owner = null;
        break;
    }
    return owner;
  }

  protected SuggestOracle createOwnerOracle() {
    switch(ownerId.getType()) {
      case USER:    return new AccountSuggestOracle();
      case GROUP:   return new AccountGroupSuggestOracle();
      case PROJECT: return new ProjectNameSuggestOracle();
    }
    return null;
  }

  public void onBrowse() {
    if (ownerId.getType() == Owner.Type.USER) {
      final String nameEmail = ownerSug.getText();
      if (nameEmail.length() == 0) {
        return;
      }
      SuggestUtil.SVC.getAccountInfo(nameEmail, new GerritCallback<AccountInfo>() {
          public void onSuccess(final AccountInfo a) {
            browse(a.getUserName());
          }
        });
    } else {
      browse(owner.getText());
    }
  }

  public void browse(String ownerForLink) {
  }
}