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
import com.google.gerrit.client.download.DownloadPanel;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritedBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

public class ProjectInfoScreen extends ProjectScreen {
  private String projectName;
  private Project project;

  private Panel projectOptionsPanel;
  private ListBox requireChangeID;
  private ListBox submitType;
  private ListBox state;
  private ListBox contentMerge;

  private Panel agreementsPanel;
  private ListBox contributorAgreements;
  private ListBox signedOffBy;

  private NpTextArea descTxt;
  private Button saveProject;

  private OnEditEnabler saveEnabler;

  public ProjectInfoScreen(final Project.NameKey toShow) {
    super(toShow);
    projectName = toShow.get();
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

    add(new ProjectDownloadPanel(projectName, true));

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
      final boolean canModifyDescription, final boolean canModifyMergeType,
      final boolean canModifyState) {
    submitType.setEnabled(canModifyMergeType);
    state.setEnabled(canModifyState);
    contentMerge.setEnabled(canModifyMergeType);
    descTxt.setEnabled(canModifyDescription);
    contributorAgreements.setEnabled(canModifyAgreements);
    signedOffBy.setEnabled(canModifyAgreements);
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

    contentMerge = newInheritedBooleanBox();
    FlowPanel fp = new FlowPanel();
    fp.add(contentMerge);
    fp.add(new InlineLabel(Util.C.useContentMerge()));
    saveEnabler.listenTo(contentMerge);
    projectOptionsPanel.add(fp);

    requireChangeID = newInheritedBooleanBox();
    fp = new FlowPanel();
    fp.add(requireChangeID);
    fp.add(new InlineHTML(Util.C.requireChangeID()));
    saveEnabler.listenTo(requireChangeID);
    projectOptionsPanel.add(fp);

    add(projectOptionsPanel);
  }

  private static ListBox newInheritedBooleanBox() {
    ListBox box = new ListBox();
    for (InheritedBoolean b : InheritedBoolean.values()) {
      box.addItem(b.name(), b.name());
    }
    return box;
  }

  /**
   * Enables the {@link #contentMerge} checkbox if the selected submit type
   * allows the usage of content merge.
   * If the submit type (currently only 'Fast Forward Only') does not allow
   * content merge the useContentMerge checkbox gets disabled.
   */
  private void setEnabledForUseContentMerge() {
    if (SubmitType.FAST_FORWARD_ONLY.equals(Project.SubmitType
        .valueOf(submitType.getValue(submitType.getSelectedIndex())))) {
      contentMerge.setEnabled(false);
      setBool(contentMerge, InheritedBoolean.FALSE);
    } else {
      contentMerge.setEnabled(submitType.isEnabled());
    }
  }

  private void initAgreements() {
    agreementsPanel = new VerticalPanel();
    agreementsPanel.add(new SmallHeading(Util.C.headingAgreements()));

    contributorAgreements = newInheritedBooleanBox();
    if (Gerrit.getConfig().isUseContributorAgreements()) {
      FlowPanel fp = new FlowPanel();
      fp.add(contributorAgreements);
      fp.add(new InlineLabel(Util.C.useContributorAgreements()));
      saveEnabler.listenTo(contributorAgreements);
      agreementsPanel.add(fp);
    }

    signedOffBy = newInheritedBooleanBox();
    FlowPanel fp = new FlowPanel();
    fp.add(signedOffBy);
    fp.add(new InlineHTML(Util.C.useSignedOffBy()));
    saveEnabler.listenTo(signedOffBy);
    agreementsPanel.add(fp);

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
    if (state != null) {
      for (int i = 0; i < state.getItemCount(); i++) {
        if (newState.name().equals(state.getValue(i))) {
          state.setSelectedIndex(i);
          break;
        }
      }
    }
  }

  private static void setBool(ListBox box, InheritedBoolean val) {
    for (int i = 0; i < box.getItemCount(); i++) {
      if (val.name().equals(box.getValue(i))) {
        box.setSelectedIndex(i);
        break;
      }
    }
  }

  private static InheritedBoolean getBool(ListBox box) {
    int i = box.getSelectedIndex();
    if (i >= 0) {
      return InheritedBoolean.valueOf(box.getValue(i));
    }
    return InheritedBoolean.INHERIT;
  }

  void display(final ProjectDetail result) {
    project = result.project;

    descTxt.setText(project.getDescription());
    setBool(contributorAgreements, project.getUseContributorAgreements());
    setBool(signedOffBy, project.getUseSignedOffBy());
    setBool(contentMerge, project.getUseContentMerge());
    setBool(requireChangeID, project.getRequireChangeID());
    setSubmitType(project.getSubmitType());
    setState(project.getState());

    saveProject.setEnabled(false);
  }

  private void doSave() {
    project.setDescription(descTxt.getText().trim());
    project.setUseContributorAgreements(getBool(contributorAgreements));
    project.setUseSignedOffBy(getBool(signedOffBy));
    project.setUseContentMerge(getBool(contentMerge));
    project.setRequireChangeID(getBool(requireChangeID));
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

  public class ProjectDownloadPanel extends DownloadPanel {
    public ProjectDownloadPanel(String project, boolean isAllowsAnonymous) {
      super(project, null, isAllowsAnonymous);
    }

    @Override
    public void populateDownloadCommandLinks() {
      if (!urls.isEmpty()) {
        if (allowedCommands.contains(DownloadCommand.CHECKOUT)
            || allowedCommands.contains(DownloadCommand.DEFAULT_DOWNLOADS)) {
          commands.add(cmdLinkfactory.new CloneCommandLink());
        }
      }
    }
  }
}
