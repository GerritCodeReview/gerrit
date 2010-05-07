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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

public class ProjectInfoPanel extends Composite {
  private Project.NameKey projectName;
  private Project project;

  private Panel submitTypePanel;
  private ListBox submitType;

  private Panel agreementsPanel;
  private CheckBox useContributorAgreements;
  private CheckBox useSignedOffBy;

  private NpTextArea descTxt;
  private Button saveProject;

  public ProjectInfoPanel(final Project.NameKey toShow) {
    saveProject = new Button(Util.C.buttonSaveChanges());
    saveProject.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doSave();
      }
    });

    final FlowPanel body = new FlowPanel();
    initDescription(body);
    initSubmitType(body);
    initAgreements(body);
    body.add(saveProject);

    initWidget(body);
    projectName = toShow;
  }

  @Override
  protected void onLoad() {
    enableForm(false);
    saveProject.setEnabled(false);
    super.onLoad();
    refresh();
  }

  private void refresh() {
    Util.PROJECT_SVC.projectDetail(projectName,
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            enableForm(result.canModifyData);
            saveProject.setVisible(result.canModifyData);
            saveProject.setEnabled(false);
            display(result);
          }
        });
  }

  private void enableForm(final boolean on) {
    submitType.setEnabled(on);
    descTxt.setEnabled(on);
    useContributorAgreements.setEnabled(on);
    useSignedOffBy.setEnabled(on);
    saveProject.setEnabled(on);
  }

  private void initDescription(final Panel body) {
    final VerticalPanel vp = new VerticalPanel();
    vp.add(new SmallHeading(Util.C.headingDescription()));

    descTxt = new NpTextArea();
    descTxt.setVisibleLines(6);
    descTxt.setCharacterWidth(60);
    vp.add(descTxt);

    body.add(vp);
    new TextSaveButtonListener(descTxt, saveProject);
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
        saveProject.setEnabled(true);
      }
    });
    submitTypePanel.add(submitType);
    body.add(submitTypePanel);
  }

  private void initAgreements(final Panel body) {
    final ValueChangeHandler<Boolean> onChangeSave =
        new ValueChangeHandler<Boolean>() {
          @Override
          public void onValueChange(ValueChangeEvent<Boolean> event) {
            saveProject.setEnabled(true);
          }
        };

    agreementsPanel = new VerticalPanel();
    agreementsPanel.add(new SmallHeading(Util.C.headingAgreements()));

    useContributorAgreements = new CheckBox(Util.C.useContributorAgreements());
    useContributorAgreements.addValueChangeHandler(onChangeSave);
    agreementsPanel.add(useContributorAgreements);

    useSignedOffBy = new CheckBox(Util.C.useSignedOffBy(), true);
    useSignedOffBy.addValueChangeHandler(onChangeSave);
    agreementsPanel.add(useSignedOffBy);

    body.add(agreementsPanel);
  }

  private void setSubmitType(final Project.SubmitType newSubmitType) {
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
    project = result.project;

    final boolean isall =
        Gerrit.getConfig().getWildProject().equals(project.getNameKey());
    submitTypePanel.setVisible(!isall);
    agreementsPanel.setVisible(!isall);
    useContributorAgreements.setVisible(Gerrit.getConfig()
        .isUseContributorAgreements());

    descTxt.setText(project.getDescription());
    useContributorAgreements.setValue(project.isUseContributorAgreements());
    useSignedOffBy.setValue(project.isUseSignedOffBy());
    setSubmitType(project.getSubmitType());
  }

  private void doSave() {
    project.setDescription(descTxt.getText().trim());
    project.setUseContributorAgreements(useContributorAgreements.getValue());
    project.setUseSignedOffBy(useSignedOffBy.getValue());
    if (submitType.getSelectedIndex() >= 0) {
      project.setSubmitType(Project.SubmitType.valueOf(submitType
          .getValue(submitType.getSelectedIndex())));
    }

    enableForm(false);
    saveProject.setEnabled(false);

    Util.PROJECT_SVC.changeProjectSettings(project,
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            enableForm(true);
            display(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            refresh();
            super.onFailure(caught);
          }
        });
  }
}
