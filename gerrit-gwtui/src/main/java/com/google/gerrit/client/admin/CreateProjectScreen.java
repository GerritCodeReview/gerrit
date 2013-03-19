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

package com.google.gerrit.client.admin;

import static com.google.gerrit.common.data.GlobalCapability.CREATE_PROJECT;
import static com.google.gerrit.common.data.GlobalCapability
    .INSTANTIATE_SOME_TEMPLATE;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.NotFoundScreen;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.account.AccountCapabilities;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.projects.ProjectInfo;
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.ProjectListPopup;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.ProjectsTable;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.ProjectUtil;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.List;

public class CreateProjectScreen extends Screen {
  private Grid grid;
  private ListBox template;
  private NpTextBox project;
  private NpTextBox projectNamePrefix;
  private Button create;
  private Button browse;
  private HintTextBox parent;
  private SuggestBox suggestParent;
  private CheckBox emptyCommit;
  private CheckBox permissionsOnly;
  private ProjectsTable suggestedParentsTab;
  private ProjectListPopup projectsPopup;
  private boolean useTemplate;
  private boolean canCreateProject;

  public CreateProjectScreen() {
    super();
    setRequiresSignIn(true);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    AccountCapabilities.all(new GerritCallback<AccountCapabilities>() {
      @Override
      public void onSuccess(AccountCapabilities ac) {
        if (ac.canPerform(CREATE_PROJECT)
            || ac.canPerform(INSTANTIATE_SOME_TEMPLATE)) {
          canCreateProject = ac.canPerform(CREATE_PROJECT);
          display();
        } else {
          Gerrit.display(PageLinks.ADMIN_CREATE_PROJECT, new NotFoundScreen());
        }
      }
    }, INSTANTIATE_SOME_TEMPLATE, CREATE_PROJECT);
  }

  @Override
  public void onShowView() {
    super.onShowView();
    populateTemplateListBox();
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    projectsPopup.closePopup();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.createProjectTitle());
    addCreateProjectPanel();

    /* popup */
    projectsPopup = new ProjectListPopup() {
      @Override
      protected void onMovePointerTo(String projectName) {
        // prevent user input from being overwritten by simply poping up
        if (!projectsPopup.isPoppingUp() || "".equals(suggestParent.getText())) {
          suggestParent.setText(projectName);
        }
      }
    };
    projectsPopup.initPopup(Util.C.projects(), PageLinks.ADMIN_PROJECTS);
  }

  private void addCreateProjectPanel() {
    final VerticalPanel fp = new VerticalPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().createProjectPanel());

    initTemplateListBox();
    initProjectNamePrefix();
    initCreateTxt();
    initCreateButton();
    initParentBox();

    addGrid(fp);

    emptyCommit = new CheckBox(Util.C.checkBoxEmptyCommit());
    permissionsOnly = new CheckBox(Util.C.checkBoxPermissionsOnly());
    fp.add(emptyCommit);
    fp.add(permissionsOnly);
    fp.add(create);
    VerticalPanel vp = new VerticalPanel();
    vp.add(fp);
    initSuggestedParents();
    vp.add(suggestedParentsTab);
    add(vp);
  }

  private void initTemplateListBox() {
    useTemplate = false;
    template = new ListBox();
    template.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        if (event.getSource() == template) {
          setTemplate(template.getValue(template.getSelectedIndex()));
        }
      }
    });
  }

  private void initProjectNamePrefix() {
    projectNamePrefix = new NpTextBox();
    projectNamePrefix.setEnabled(false);
  }

  private void updateProjectNamePrefix(String prefix) {
    final Widget gridWidget;
    if (prefix == null || prefix.isEmpty()) {
      project.setVisibleLength(50);
      projectNamePrefix.setText("");
      gridWidget = project;
    } else {
      Panel projectPanel = new HorizontalPanel();

      projectNamePrefix.setText(prefix);
      projectNamePrefix.setVisibleLength(prefix.length());
      projectPanel.add(projectNamePrefix);

      // The 3 in the following computation is a rough estimate of borders,
      // margins, paddings etc.
      project.setVisibleLength(50 - prefix.length() - 3);
      projectPanel.add(project);

      gridWidget = projectPanel;
    }
    grid.setWidget(1, 1, gridWidget);
  }

  private void populateTemplateListBox() {
    template.clear();

    if (canCreateProject) {
      template.addItem(Util.C.templateNone(), (String) null);
    }

    ProjectMap.instantiableTemplates(new ScreenLoadCallback<ProjectMap>(this) {
      @Override
      protected void preDisplay(final ProjectMap result) {
        List<ProjectInfo> projectInfos = Natives.asList(result.values());
        for (ProjectInfo projectInfo: projectInfos) {
          template.addItem(projectInfo.name(), projectInfo.name_key().get());
        }
        if (!projectInfos.isEmpty()) {
          setTemplate(template.getValue(0));
        }
      }
    });

    if (template.getItemCount() == 0) {
      this.enableForm(false);
    } else {
      setTemplate(template.getValue(0));
    }
  }

  private void initCreateTxt() {
    project = new NpTextBox();
    project.setVisibleLength(50);
    project.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          doCreateProject();
        }
      }
    });
  }

  private void initCreateButton() {
    create = new Button(Util.C.buttonCreateProject());
    create.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doCreateProject();
      }
    });

    browse = new Button(Util.C.buttonBrowseProjects());
    browse.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        int top = grid.getAbsoluteTop() - 50; // under page header
        // Try to place it to the right of everything else, but not
        // right justified
        int left =
            5 + Math.max(
                grid.getAbsoluteLeft() + grid.getOffsetWidth(),
                suggestedParentsTab.getAbsoluteLeft()
                    + suggestedParentsTab.getOffsetWidth());
        projectsPopup.setPreferredCoordinates(top, left);
        projectsPopup.displayPopup();
      }
    });
  }

  private void initParentBox() {
    parent = new HintTextBox();
    suggestParent =
        new SuggestBox(new ProjectNameSuggestOracle(), parent);
    parent.setVisibleLength(50);
  }

  private void initSuggestedParents() {
    suggestedParentsTab = new ProjectsTable() {
      {
        table.setText(0, 1, Util.C.parentSuggestions());
      }

      @Override
      protected void populate(final int row, final ProjectInfo k) {
        final Anchor projectLink = new Anchor(k.name());
        projectLink.addClickHandler(new ClickHandler() {

          @Override
          public void onClick(ClickEvent event) {
            suggestParent.setText(getRowItem(row).name());
          }
        });

        table.setWidget(row, 1, projectLink);
        table.setText(row, 2, k.description());

        setRowItem(row, k);
      }
    };
    suggestedParentsTab.setVisible(false);

    ProjectMap.parentCandidates(new GerritCallback<ProjectMap>() {
      @Override
      public void onSuccess(ProjectMap list) {
        if (!list.isEmpty()) {
          suggestedParentsTab.setVisible(true);
          suggestedParentsTab.display(list);
          suggestedParentsTab.finishDisplay();
        }
      }
    });
  }

  private void addGrid(final VerticalPanel fp) {
    grid = new Grid(3, 3);
    grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    grid.setText(0, 0, Util.C.headingTemplate() + ":");
    grid.setWidget(0, 1, template);
    grid.setText(1, 0, Util.C.columnProjectName() + ":");
    updateProjectNamePrefix(null);
    grid.setText(2, 0, Util.C.headingParentProjectName() + ":");
    grid.setWidget(2, 1, suggestParent);
    grid.setWidget(2, 2, browse);
    fp.add(grid);
  }

  private void setTemplate(String templateName) {
    useTemplate = templateName != null && !templateName.isEmpty();
    updateProjectNamePrefix(null);
    if (useTemplate) {
      enableForm(false);
      Util.PROJECT_SVC.projectDetail(new Project.NameKey(templateName),
          new ScreenLoadCallback<ProjectDetail>(this) {
            public void preDisplay(final ProjectDetail result) {
              final String parentName;
              if (result.parent == null) {
                parentName = "";
              } else {
                parentName = result.parent.get();
              }
              parent.setText(parentName);
              updateProjectNamePrefix(result.templateProjectNamePrefix);
              enableForm(true);
            }
          });

    } else {
      enableForm(canCreateProject);
    }
  }

  private void doCreateProject() {
    String projectNameBase = project.getText().trim();
    final String projectName = projectNamePrefix.getText() + projectNameBase;
    final String parentName = suggestParent.getText().trim();

    if ("".equals(projectNameBase)) {
      project.setFocus(true);
      return;
    }

    enableForm(false);
    ProjectApi.createProject(template.getValue(template.getSelectedIndex()),
        projectName, parentName, emptyCommit.getValue(),
        permissionsOnly.getValue(), new AsyncCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            String nameWithoutSuffix = ProjectUtil.stripGitSuffix(projectName);
            History.newItem(Dispatcher.toProjectAdmin(new Project.NameKey(
                nameWithoutSuffix), ProjectScreen.INFO));
          }

          @Override
          public void onFailure(Throwable caught) {
            new ErrorDialog(caught.getMessage()).center();
            enableForm(true);
          }
        });
  }

  private void enableForm(final boolean enabled) {
    template.setEnabled(enabled);
    project.setEnabled(enabled);
    create.setEnabled(enabled);
    parent.setEnabled(enabled && !useTemplate);
    browse.setEnabled(enabled && !useTemplate);
    emptyCommit.setEnabled(enabled);
    permissionsOnly.setEnabled(enabled);
    suggestedParentsTab.setVisible(enabled && !useTemplate);
  }
}
