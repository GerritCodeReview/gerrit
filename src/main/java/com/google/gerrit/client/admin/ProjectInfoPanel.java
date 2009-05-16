// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

public class ProjectInfoPanel extends Composite {
  private Project.Id projectId;

  private Panel ownerPanel;
  private NpTextBox ownerTxtBox;
  private SuggestBox ownerTxt;
  private Button saveOwner;

  private Panel submitTypePanel;
  private ListBox submitType;
  private Project.SubmitType currentSubmitType;

  private NpTextArea descTxt;
  private Button saveDesc;

  public ProjectInfoPanel(final Project.Id toShow) {
    final FlowPanel body = new FlowPanel();
    initOwner(body);
    initDescription(body);
    initSubmitType(body);
    initWidget(body);

    projectId = toShow;
  }

  @Override
  public void onLoad() {
    enableForm(false);
    saveOwner.setEnabled(false);
    saveDesc.setEnabled(false);
    super.onLoad();

    Util.PROJECT_SVC.projectDetail(projectId,
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            enableForm(true);
            saveOwner.setEnabled(false);
            saveDesc.setEnabled(false);
            display(result);
          }
        });
  }

  private void enableForm(final boolean on) {
    submitType.setEnabled(on);
    ownerTxtBox.setEnabled(on);
    descTxt.setEnabled(on);
  }

  private void initOwner(final Panel body) {
    ownerPanel = new VerticalPanel();
    ownerPanel.add(new SmallHeading(Util.C.headingOwner()));

    ownerTxtBox = new NpTextBox();
    ownerTxtBox.setVisibleLength(60);
    ownerTxt = new SuggestBox(new AccountGroupSuggestOracle(), ownerTxtBox);
    ownerPanel.add(ownerTxt);

    saveOwner = new Button(Util.C.buttonChangeGroupOwner());
    saveOwner.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final String newOwner = ownerTxt.getText().trim();
        if (newOwner.length() > 0) {
          Util.PROJECT_SVC.changeProjectOwner(projectId, newOwner,
              new GerritCallback<VoidResult>() {
                public void onSuccess(final VoidResult result) {
                  saveOwner.setEnabled(false);
                }
              });
        }
      }
    });
    ownerPanel.add(saveOwner);
    body.add(ownerPanel);

    new TextSaveButtonListener(ownerTxtBox, saveOwner);
  }

  private void initDescription(final Panel body) {
    final VerticalPanel vp = new VerticalPanel();
    vp.add(new SmallHeading(Util.C.headingDescription()));

    descTxt = new NpTextArea();
    descTxt.setVisibleLines(6);
    descTxt.setCharacterWidth(60);
    vp.add(descTxt);

    saveDesc = new Button(Util.C.buttonSaveDescription());
    saveDesc.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final String txt = descTxt.getText().trim();
        Util.PROJECT_SVC.changeProjectDescription(projectId, txt,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                saveDesc.setEnabled(false);
              }
            });
      }
    });
    vp.add(saveDesc);
    body.add(vp);

    new TextSaveButtonListener(descTxt, saveDesc);
  }

  private void initSubmitType(final Panel body) {
    submitTypePanel = new VerticalPanel();
    submitTypePanel.add(new SmallHeading(Util.C.headingSubmitType()));

    submitType = new ListBox();
    for (final Project.SubmitType type : Project.SubmitType.values()) {
      submitType.addItem(Util.toLongString(type), type.name());
    }
    submitType.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        final int i = submitType.getSelectedIndex();
        if (i < 0) {
          return;
        }
        final Project.SubmitType newSubmitType =
            Project.SubmitType.valueOf(submitType.getValue(i));
        submitType.setEnabled(false);
        Util.PROJECT_SVC.changeProjectSubmitType(projectId, newSubmitType,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                currentSubmitType = newSubmitType;
                submitType.setEnabled(true);
              }

              @Override
              public void onFailure(final Throwable caught) {
                submitType.setEnabled(false);
                setSubmitType(currentSubmitType);
                super.onFailure(caught);
              }
            });
      }
    });
    submitTypePanel.add(submitType);
    body.add(submitTypePanel);
  }

  private void setSubmitType(final Project.SubmitType newSubmitType) {
    currentSubmitType = newSubmitType;
    if (submitType != null) {
      for (int i = 0; i < submitType.getItemCount(); i++) {
        if (newSubmitType.name().equals(submitType.getValue(i))) {
          submitType.setSelectedIndex(i);
          return;
        }
      }
      submitType.setSelectedIndex(-1);
    }
  }

  void display(final ProjectDetail result) {
    final Project project = result.project;
    final AccountGroup owner = result.groups.get(project.getOwnerGroupId());
    if (owner != null) {
      ownerTxt.setText(owner.getName());
    } else {
      ownerTxt.setText(Util.M.deletedGroup(project.getOwnerGroupId().get()));
    }

    if (ProjectRight.WILD_PROJECT.equals(project.getId())) {
      ownerPanel.setVisible(false);
      submitTypePanel.setVisible(false);
    } else {
      ownerPanel.setVisible(true);
      submitTypePanel.setVisible(true);
    }

    descTxt.setText(project.getDescription());
    setSubmitType(project.getSubmitType());
  }
}
