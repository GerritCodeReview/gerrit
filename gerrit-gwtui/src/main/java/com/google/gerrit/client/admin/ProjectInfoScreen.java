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

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.download.DownloadPanel;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.common.data.UiCommandDetail;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.InheritedBoolean;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;

import java.util.List;

public class ProjectInfoScreen extends ProjectScreen {
  private String projectName;
  private Project project;

  private LabeledWidgetsGrid grid;

  // Section: Project Options
  private ListBox requireChangeID;
  private ListBox submitType;
  private ListBox state;
  private ListBox contentMerge;

  // Section: Contributor Agreements
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
    grid = new LabeledWidgetsGrid();
    initProjectOptions();
    initAgreements();
    add(grid);
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
    savedPanel = INFO;
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
    grid.addHeader(new SmallHeading(Util.C.headingProjectOptions()));

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
    grid.add(Util.C.headingProjectSubmitType(), submitType);

    state = new ListBox();
    for (final Project.State stateValue : Project.State.values()) {
      state.addItem(Util.toLongString(stateValue), stateValue.name());
    }
    saveEnabler.listenTo(state);
    grid.add(Util.C.headingProjectState(), state);

    contentMerge = newInheritedBooleanBox();
    saveEnabler.listenTo(contentMerge);
    grid.add(Util.C.useContentMerge(), contentMerge);

    requireChangeID = newInheritedBooleanBox();
    saveEnabler.listenTo(requireChangeID);
    grid.addHtml(Util.C.requireChangeID(), requireChangeID);
  }

  private static ListBox newInheritedBooleanBox() {
    ListBox box = new ListBox();
    for (InheritableBoolean b : InheritableBoolean.values()) {
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
      final InheritedBoolean inheritedBoolean = new InheritedBoolean();
      inheritedBoolean.setValue(InheritableBoolean.FALSE);
      setBool(contentMerge, inheritedBoolean);
    } else {
      contentMerge.setEnabled(submitType.isEnabled());
    }
  }

  private void initAgreements() {
    grid.addHeader(new SmallHeading(Util.C.headingAgreements()));

    contributorAgreements = newInheritedBooleanBox();
    if (Gerrit.getConfig().isUseContributorAgreements()) {
      saveEnabler.listenTo(contributorAgreements);
      grid.add(Util.C.useContributorAgreements(), contributorAgreements);
    }

    signedOffBy = newInheritedBooleanBox();
    saveEnabler.listenTo(signedOffBy);
    grid.addHtml(Util.C.useSignedOffBy(), signedOffBy);
  }

  private void initProjectCommands(final List<UiCommandDetail> commands) {
    if (commands == null || commands.isEmpty()) {
      return;
    }
    grid.addHeader(new SmallHeading(Util.C.headingProjectCommands()));
    FlowPanel actionsPanel = new FlowPanel();
    actionsPanel.setStyleName(Gerrit.RESOURCES.css().patchSetActions());
    actionsPanel.setVisible(true);
    populateCommands(commands, actionsPanel);
    grid.add(Util.C.headingCommands(), actionsPanel);
  }

  private void populateCommands(final List<UiCommandDetail> commands,
      final FlowPanel actionsPanel) {
    for (final UiCommandDetail cmd : commands) {
      final Button b = new Button(cmd.label);
      b.setEnabled(cmd.enabled);
      b.setTitle(cmd.title);
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          AsyncCallback<NativeString> cb =
              new AsyncCallback<NativeString>() {
                @Override
                public void onFailure(Throwable caught) {
                  b.setEnabled(true);
                  new ErrorDialog(caught).center();
                }

                @Override
                public void onSuccess(NativeString msg) {
                  b.setEnabled(true);
                  if (!msg.toString().isEmpty()) {
                    Window.alert(msg.asString());
                  }
                }
              };
          RestApi api = ProjectApi.project(project.getNameKey()).view(cmd.id);
          if ("PUT".equalsIgnoreCase(cmd.method)) {
            api.put(JavaScriptObject.createObject(), cb);
          } else if ("DELETE".equalsIgnoreCase(cmd.method)) {
            api.delete(cb);
          } else {
            api.post(JavaScriptObject.createObject(), cb);
          }
        }
      });
      actionsPanel.add(b);
    }
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

  private void setBool(ListBox box, InheritedBoolean inheritedBoolean) {
    int inheritedIndex = -1;
    for (int i = 0; i < box.getItemCount(); i++) {
      if (box.getValue(i).startsWith(InheritableBoolean.INHERIT.name())) {
        inheritedIndex = i;
      }
      if (box.getValue(i).startsWith(inheritedBoolean.value.name())) {
        box.setSelectedIndex(i);
      }
    }
    if (inheritedIndex >= 0) {
      if (project.getParent(Gerrit.getConfig().getWildProject()) == null) {
        if (box.getSelectedIndex() == inheritedIndex) {
          for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getValue(i).equals(InheritableBoolean.FALSE.name())) {
              box.setSelectedIndex(i);
              break;
            }
          }
        }
        box.removeItem(inheritedIndex);
      } else {
        box.setItemText(inheritedIndex, InheritableBoolean.INHERIT.name() + " ("
            + inheritedBoolean.inheritedValue + ")");
      }
    }
  }

  private static InheritableBoolean getBool(ListBox box) {
    int i = box.getSelectedIndex();
    if (i >= 0) {
      final String selectedValue = box.getValue(i);
      if (selectedValue.startsWith(InheritableBoolean.INHERIT.name())) {
        return InheritableBoolean.INHERIT;
      }
      return InheritableBoolean.valueOf(selectedValue);
    }
    return InheritableBoolean.INHERIT;
  }

  void display(final ProjectDetail result) {
    project = result.project;
    descTxt.setText(project.getDescription());
    setBool(contributorAgreements, result.useContributorAgreements);
    setBool(signedOffBy, result.useSignedOffBy);
    setBool(contentMerge, result.useContentMerge);
    setBool(requireChangeID, result.requireChangeID);
    setSubmitType(project.getSubmitType());
    setState(project.getState());

    initProjectCommands(result.getCommands());
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

  private class LabeledWidgetsGrid extends FlexTable {
    private String labelSuffix;

    public LabeledWidgetsGrid() {
      super();
      labelSuffix = ":";
    }

    private void addHeader(Widget widget) {
      int row = getRowCount();
      insertRow(row);
      setWidget(row, 0, widget);
      getCellFormatter().getElement(row, 0).setAttribute("colSpan", "2");
    }

    private void add(String label, boolean labelIsHtml, Widget widget) {
      int row = getRowCount();
      insertRow(row);
      if (label != null) {
        if (labelIsHtml) {
          setHTML(row, 0, label + labelSuffix);
        } else {
          setText(row, 0, label + labelSuffix);
        }
      }
      setWidget(row, 1, widget);
    }

    public void add(String label, Widget widget) {
      add(label, false, widget);
    }

    public void addHtml(String label, Widget widget) {
      add(label, true, widget);
    }

  }
}
