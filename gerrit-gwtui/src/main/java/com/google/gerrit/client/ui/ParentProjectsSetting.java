// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.Util;
import com.google.gerrit.client.admin.ParentSettingHandler;
import com.google.gerrit.client.admin.ParentSettingOnCloseEvent;
import com.google.gerrit.client.admin.ParentSettingOnResultEvent;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.List;

/** It creates UI for setting projects parent. */
public class ParentProjectsSetting {
  final private HandlerManager handlerManager = new HandlerManager(this);

  private VerticalPanel parentSettingPanel;
  private SuggestBox parentNameTxt;
  private NpTextBox parentNameTxtBox;
  private Button buttonSet;
  private Button buttonCancel;
  private List<Project.NameKey> childProjects;
  private boolean submitOnSelection;

  /**
   * Constructor - Creates and set UI elements.
   */
  public ParentProjectsSetting() {
    createParentSettingUI();
  }

  public void setChildProjects(List<String> selectedProjects) {
    this.childProjects = new ArrayList<Project.NameKey>();

    for (String p : selectedProjects) {
      childProjects.add(new Project.NameKey(p));
    }
  }

  public VerticalPanel getParentSettingPanel() {
    return parentSettingPanel;
  }

  private void createParentSettingUI() {
    parentSettingPanel = new VerticalPanel();
    parentSettingPanel.setStyleName(Gerrit.RESOURCES.css().setParentPanel());
    parentSettingPanel.add(new SmallHeading(Util.C.headingSetParentPanel()
        + ":"));
    parentSettingPanel.setWidth("34%");
    parentSettingPanel.setVisible(false);

    /* SuggestBox - Projects to be set as parent */
    parentNameTxtBox = new NpTextBox();

    parentNameTxt =
        new SuggestBox(new ProjectNameSuggestOracle(), parentNameTxtBox);

    parentNameTxtBox.setVisibleLength(50);
    parentNameTxtBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        submitOnSelection = false;

        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          if (parentNameTxt.isSuggestionListShowing()) {
            submitOnSelection = true;
          } else {
            doSetParent();
          }
        }
      }
    });

    parentNameTxt.addSelectionHandler(new SelectionHandler<Suggestion>() {
      @Override
      public void onSelection(SelectionEvent<Suggestion> event) {
        if (submitOnSelection) {
          submitOnSelection = false;
          doSetParent();
        }
      }
    });

    parentSettingPanel.add(parentNameTxt);

    final HorizontalPanel setParentButtonsPanel = new HorizontalPanel();

    buttonSet = new Button(Util.C.buttonSetParent());
    buttonSet.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doSetParent();
      }
    });

    setParentButtonsPanel.add(buttonSet);

    buttonCancel = new Button(Util.C.buttonCancelSetParent());
    buttonCancel.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hideParentSettingPanel();

        handlerManager.fireEvent(new ParentSettingOnCloseEvent());
      }
    });

    setParentButtonsPanel.add(buttonCancel);

    parentSettingPanel.add(setParentButtonsPanel);
  }

  public void hideParentSettingPanel() {
    parentNameTxt.setText("");
    parentSettingPanel.setVisible(false);
  }

  private void doSetParent() {
    if (!"".equals(parentNameTxt.getText())) {
      com.google.gerrit.client.admin.Util.PROJECT_SVC.setParentProject(
          parentNameTxt.getText(), childProjects,
          new GerritCallback<VoidResult>() {
            @Override
            public void onSuccess(VoidResult result) {
              hideParentSettingPanel();
              handlerManager.fireEvent(new ParentSettingOnCloseEvent());
              handlerManager.fireEvent(new ParentSettingOnResultEvent());
            }
          });
    }
  }

  public void addParentSettingHandler(ParentSettingHandler handler) {
    handlerManager.addHandler(ParentSettingOnCloseEvent.getType(), handler);
    handlerManager.addHandler(ParentSettingOnResultEvent.getType(), handler);
  }
}
