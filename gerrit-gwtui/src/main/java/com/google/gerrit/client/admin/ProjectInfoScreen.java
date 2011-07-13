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
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

public class ProjectInfoScreen extends ProjectScreen {
  private Project project;

  private Panel projectOptionsPanel;
  private CheckBox requireChangeID;
  private ListBox submitType;
  private ListBox state;
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
                result.canModifyDescription, result.canModifyMergeType, result.canModifyState);
            saveProject.setVisible(
                result.canModifyAgreements ||
                result.canModifyDescription ||
                result.canModifyMergeType ||
                result.canModifyState);
            display(result);
          }
        });
  }

  private void enableForm(final boolean canModifyAgreements,
      final boolean canModifyDescription, final boolean canModifyMergeType, final boolean canModifyState) {
    submitType.setEnabled(canModifyMergeType);
    state.setEnabled(canModifyState);
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

    submitType = new ListBox();
    for (final Project.SubmitType type : Project.SubmitType.values()) {
      submitType.addItem(Util.toLongString(type), type.name());
    }
    submitType.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        setEnabledForUseContentMerge();
      }
    });
    saveEnabler.listenTo(submitType);
    projectOptionsPanel.add(submitType);

    state = new ListBox();
    for (final Project.State stateValue : Project.State.values()) {
      state.addItem(Util.toLongString(stateValue), stateValue.name());
    }

    saveEnabler.listenTo(state);
    projectOptionsPanel.add(state);

    useContentMerge = new CheckBox(Util.C.useContentMerge(), true);
    saveEnabler.listenTo(useContentMerge);
    projectOptionsPanel.add(useContentMerge);

    requireChangeID = new CheckBox(Util.C.requireChangeID(), true);
    saveEnabler.listenTo(requireChangeID);
    projectOptionsPanel.add(requireChangeID);

    add(projectOptionsPanel);
  }

  /**
   * Enables the {@link #useContentMerge} checkbox if the selected submit type
   * allows the usage of content merge.
   * If the submit type (currently only 'Fast Forward Only') does not allow
   * content merge the useContentMerge checkbox gets disabled.
   */
  private void setEnabledForUseContentMerge() {
    if (SubmitType.FAST_FORWARD_ONLY.equals(Project.SubmitType
        .valueOf(submitType.getValue(submitType.getSelectedIndex())))) {
      useContentMerge.setEnabled(false);
      useContentMerge.setValue(false);
    } else {
      useContentMerge.setEnabled(submitType.isEnabled());
    }
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

  private void setSubmitType(final Project.SubmitType newSubmitType) {
    int index = -1;
    if (submitType != null) {
      for (int i = 0; i < submitType.getItemCount(); i++) {
        if (newSubmitType.name().equals(submitType.getValue(i))) {
          index = i;
          break;
        }
      }
      submitType.setSelectedIndex(index);
      setEnabledForUseContentMerge();
    }
  }

  private void setState(final Project.State newState) {
    int index = -1;
    if (state != null) {
      for (int i = 0; i < state.getItemCount(); i++) {
        if (newState.name().equals(state.getValue(i))) {
          index = i;
          break;
        }
      }
      state.setSelectedIndex(index);
    }
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
    setSubmitType(project.getSubmitType());
    setState(project.getState());

    saveProject.setEnabled(false);
  }

  private void doSave() {
    project.setDescription(descTxt.getText().trim());
    project.setUseContributorAgreements(useContributorAgreements.getValue());
    project.setUseSignedOffBy(useSignedOffBy.getValue());
    project.setUseContentMerge(useContentMerge.getValue());
    project.setRequireChangeID(requireChangeID.getValue());
    if (submitType.getSelectedIndex() >= 0) {
      project.setSubmitType(Project.SubmitType.valueOf(submitType
          .getValue(submitType.getSelectedIndex())));
    }
    if (state.getSelectedIndex() >= 0) {
      project.setState(Project.State.valueOf(state
          .getValue(state.getSelectedIndex())));
    }

    enableForm(false, false, false, false);

    Util.PROJECT_SVC.changeProjectSettings(project,
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            enableForm(result.canModifyAgreements,
                result.canModifyDescription, result.canModifyMergeType, result.canModifyState);
            display(result);
          }
        });
  }
}
