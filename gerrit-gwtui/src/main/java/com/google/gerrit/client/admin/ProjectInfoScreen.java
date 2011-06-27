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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

public class ProjectInfoScreen extends ProjectScreen {
  private Project project;

  private Panel projectOptionsPanel;
  private CheckBox requireChangeID;
  private CheckBox useContentMerge;

  private Panel agreementsPanel;
  private CheckBox useContributorAgreements;
  private CheckBox useSignedOffBy;

  private NpTextArea descTxt;
  private Button saveProject;

  private OnEditEnabler saveEnabler;

  public ProjectInfoScreen(final Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    saveProject = new Button(Util.C.buttonSaveChanges());
    saveProject.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doSave();
      }
    });

    initDescription();
    initProjectOptions();
    initAgreements();
    add(saveProject);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.projectDetail(getProjectKey(),
        new ScreenLoadCallback<ProjectDetail>(this) {
          public void preDisplay(final ProjectDetail result) {
            enableForm(result.canModifyAgreements,
                result.canModifyDescription, result.canModifyMergeType);
            saveProject.setVisible(
                result.canModifyAgreements ||
                result.canModifyDescription ||
                result.canModifyMergeType);
            display(result);
          }
        });
  }

  private void enableForm(final boolean canModifyAgreements,
      final boolean canModifyDescription, final boolean canModifyMergeType) {
    useContentMerge.setEnabled(canModifyMergeType);
    descTxt.setEnabled(canModifyDescription);
    useContributorAgreements.setEnabled(canModifyAgreements);
    useSignedOffBy.setEnabled(canModifyAgreements);
    requireChangeID.setEnabled(canModifyMergeType);
  }

  private void initDescription() {
    final VerticalPanel vp = new VerticalPanel();
    vp.add(new SmallHeading(Util.C.headingDescription()));

    descTxt = new NpTextArea();
    descTxt.setVisibleLines(6);
    descTxt.setCharacterWidth(60);
    vp.add(descTxt);

    add(vp);
    saveEnabler = new OnEditEnabler(saveProject);
    saveEnabler.listenTo(descTxt);
  }

  private void initProjectOptions() {
    projectOptionsPanel = new VerticalPanel();
    projectOptionsPanel.add(new SmallHeading(Util.C.headingProjectOptions()));

    useContentMerge = new CheckBox(Util.C.useContentMerge(), true);
    saveEnabler.listenTo(useContentMerge);
    projectOptionsPanel.add(useContentMerge);

    requireChangeID = new CheckBox(Util.C.requireChangeID(), true);
    saveEnabler.listenTo(requireChangeID);
    projectOptionsPanel.add(requireChangeID);

    add(projectOptionsPanel);
  }

  private void initAgreements() {
    agreementsPanel = new VerticalPanel();
    agreementsPanel.add(new SmallHeading(Util.C.headingAgreements()));

    useContributorAgreements = new CheckBox(Util.C.useContributorAgreements());
    saveEnabler.listenTo(useContributorAgreements);
    agreementsPanel.add(useContributorAgreements);

    useSignedOffBy = new CheckBox(Util.C.useSignedOffBy(), true);
    saveEnabler.listenTo(useSignedOffBy);
    agreementsPanel.add(useSignedOffBy);

    add(agreementsPanel);
  }

  void display(final ProjectDetail result) {
    project = result.project;

    final boolean isall =
        Gerrit.getConfig().getWildProject().equals(project.getNameKey());
    projectOptionsPanel.setVisible(!isall);
    agreementsPanel.setVisible(!isall);
    useContributorAgreements.setVisible(Gerrit.getConfig()
        .isUseContributorAgreements());

    descTxt.setText(project.getDescription());
    useContributorAgreements.setValue(project.isUseContributorAgreements());
    useSignedOffBy.setValue(project.isUseSignedOffBy());
    useContentMerge.setValue(project.isUseContentMerge());
    requireChangeID.setValue(project.isRequireChangeID());

    saveProject.setEnabled(false);
  }

  private void doSave() {
    project.setDescription(descTxt.getText().trim());
    project.setUseContributorAgreements(useContributorAgreements.getValue());
    project.setUseSignedOffBy(useSignedOffBy.getValue());
    project.setUseContentMerge(useContentMerge.getValue());
    project.setRequireChangeID(requireChangeID.getValue());

    enableForm(false, false, false);

    Util.PROJECT_SVC.changeProjectSettings(project,
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            enableForm(result.canModifyAgreements,
                result.canModifyDescription, result.canModifyMergeType);
            display(result);
          }
        });
  }
}
