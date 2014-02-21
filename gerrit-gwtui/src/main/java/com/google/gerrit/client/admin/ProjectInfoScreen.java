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
import com.google.gerrit.client.access.AccessMap;
import com.google.gerrit.client.access.ProjectAccessInfo;
import com.google.gerrit.client.actions.ActionButton;
import com.google.gerrit.client.actions.ActionInfo;
import com.google.gerrit.client.change.Resources;
import com.google.gerrit.client.download.DownloadPanel;
import com.google.gerrit.client.projects.ConfigInfo;
import com.google.gerrit.client.projects.ConfigInfo.ConfigParameterInfo;
import com.google.gerrit.client.projects.ConfigInfo.InheritedBooleanInfo;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.NpIntTextBox;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ProjectInfoScreen extends ProjectScreen {
  private boolean isOwner;

  private LabeledWidgetsGrid grid;
  private Panel pluginOptionsPanel;
  private LabeledWidgetsGrid actionsGrid;

  // Section: Project Options
  private ListBox requireChangeID;
  private ListBox submitType;
  private ListBox state;
  private ListBox contentMerge;
  private NpTextBox maxObjectSizeLimit;
  private Label effectiveMaxObjectSizeLimit;
  private Map<String, Map<String, FocusWidget>> pluginConfigWidgets;

  // Section: Contributor Agreements
  private ListBox contributorAgreements;
  private ListBox signedOffBy;

  private NpTextArea descTxt;
  private Button saveProject;

  private OnEditEnabler saveEnabler;

  public ProjectInfoScreen(final Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    Resources.I.style().ensureInjected();
    saveProject = new Button(Util.C.buttonSaveChanges());
    saveProject.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doSave();
      }
    });

    add(new ProjectDownloadPanel(getProjectKey().get(), true));

    initDescription();
    grid = new LabeledWidgetsGrid();
    pluginOptionsPanel = new FlowPanel();
    actionsGrid = new LabeledWidgetsGrid();
    initProjectOptions();
    initAgreements();
    add(grid);
    add(pluginOptionsPanel);
    add(saveProject);
    add(actionsGrid);
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    Project.NameKey project = getProjectKey();
    CallbackGroup cbg = new CallbackGroup();
    AccessMap.get(project,
        cbg.add(new GerritCallback<ProjectAccessInfo>() {
          @Override
          public void onSuccess(ProjectAccessInfo result) {
            isOwner = result.isOwner();
            enableForm();
            saveProject.setVisible(isOwner);
          }
        }));
    ProjectApi.getConfig(project,
        cbg.addFinal(new ScreenLoadCallback<ConfigInfo>(this) {
          @Override
          public void preDisplay(ConfigInfo result) {
            display(result);
          }
        }));

    savedPanel = INFO;
  }

  private void enableForm() {
    enableForm(isOwner);
  }

  private void enableForm(boolean isOwner) {
    submitType.setEnabled(isOwner);
    state.setEnabled(isOwner);
    contentMerge.setEnabled(isOwner);
    descTxt.setEnabled(isOwner);
    contributorAgreements.setEnabled(isOwner);
    signedOffBy.setEnabled(isOwner);
    requireChangeID.setEnabled(isOwner);
    maxObjectSizeLimit.setEnabled(isOwner);

    if (pluginConfigWidgets != null) {
      for (Map<String, FocusWidget> widgetMap : pluginConfigWidgets.values()) {
        for (FocusWidget widget : widgetMap.values()) {
          widget.setEnabled(isOwner);
        }
      }
    }
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

    maxObjectSizeLimit = new NpTextBox();
    saveEnabler.listenTo(maxObjectSizeLimit);
    effectiveMaxObjectSizeLimit = new Label();
    effectiveMaxObjectSizeLimit.setStyleName(
        Gerrit.RESOURCES.css().maxObjectSizeLimitEffectiveLabel());
    HorizontalPanel p = new HorizontalPanel();
    p.add(maxObjectSizeLimit);
    p.add(effectiveMaxObjectSizeLimit);
    grid.addHtml(Util.C.headingMaxObjectSizeLimit(), p);
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
      InheritedBooleanInfo b = InheritedBooleanInfo.create();
      b.setConfiguredValue(InheritableBoolean.FALSE);
      setBool(contentMerge, b);
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

  private void setBool(ListBox box, InheritedBooleanInfo inheritedBoolean) {
    int inheritedIndex = -1;
    for (int i = 0; i < box.getItemCount(); i++) {
      if (box.getValue(i).startsWith(InheritableBoolean.INHERIT.name())) {
        inheritedIndex = i;
      }
      if (box.getValue(i).startsWith(inheritedBoolean.configured_value().name())) {
        box.setSelectedIndex(i);
      }
    }
    if (inheritedIndex >= 0) {
      if (getProjectKey().equals(Gerrit.getConfig().getWildProject())) {
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
            + inheritedBoolean.inherited_value() + ")");
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

  void display(ConfigInfo result) {
    descTxt.setText(result.description());
    setBool(contributorAgreements, result.use_contributor_agreements());
    setBool(signedOffBy, result.use_signed_off_by());
    setBool(contentMerge, result.use_content_merge());
    setBool(requireChangeID, result.require_change_id());
    setSubmitType(result.submit_type());
    setState(result.state());
    maxObjectSizeLimit.setText(result.max_object_size_limit().configured_value());
    if (result.max_object_size_limit().inherited_value() != null) {
      effectiveMaxObjectSizeLimit.setVisible(true);
      effectiveMaxObjectSizeLimit.setText(
          Util.M.effectiveMaxObjectSizeLimit(result.max_object_size_limit().value()));
      effectiveMaxObjectSizeLimit.setTitle(
          Util.M.globalMaxObjectSizeLimit(result.max_object_size_limit().inherited_value()));
    } else {
      effectiveMaxObjectSizeLimit.setVisible(false);
    }

    saveProject.setEnabled(false);
    initPluginOptions(result);
    initProjectActions(result);
  }

  private void initPluginOptions(ConfigInfo info) {
    pluginOptionsPanel.clear();
    pluginConfigWidgets = new HashMap<>();

    for (String pluginName : info.pluginConfig().keySet()) {
      Map<String, FocusWidget> widgetMap = new HashMap<>();
      pluginConfigWidgets.put(pluginName, widgetMap);
      LabeledWidgetsGrid g = new LabeledWidgetsGrid();
      g.addHeader(new SmallHeading(Util.M.pluginProjectOptionsTitle(pluginName)));
      pluginOptionsPanel.add(g);
      NativeMap<ConfigParameterInfo> pluginConfig =
          info.pluginConfig(pluginName);
      pluginConfig.copyKeysIntoChildren("name");
      for (ConfigParameterInfo param : Natives.asList(pluginConfig.values())) {
        FocusWidget w;
        if ("STRING".equals(param.type())) {
          w = renderTextBox(g, param, false);
        } else if ("INT".equals(param.type()) || "LONG".equals(param.type())) {
          w = renderTextBox(g, param, true);
        } else if ("BOOLEAN".equals(param.type())) {
          w = renderCheckBox(g, param);
        } else if ("LIST".equals(param.type())
            && param.permittedValues() != null) {
          w = renderListBox(g, param);
        } else {
          continue;
        }
        if (param.editable()) {
          widgetMap.put(param.name(), w);
        } else {
          w.setEnabled(false);
        }
      }
    }

    enableForm();
  }

  private TextBox renderTextBox(LabeledWidgetsGrid g,
      ConfigParameterInfo param, boolean numbersOnly) {
    NpTextBox textBox = numbersOnly ? new NpIntTextBox() : new NpTextBox();
    if (param.inheritable()) {
      textBox.setValue(param.configuredValue());
      Label inheritedLabel =
          new Label(Util.M.pluginProjectInheritedValue(param
              .inheritedValue()));
      inheritedLabel.setStyleName(Gerrit.RESOURCES.css()
          .pluginProjectConfigInheritedValue());
      HorizontalPanel p = new HorizontalPanel();
      p.add(textBox);
      p.add(inheritedLabel);
      addWidget(g, p, param);
    } else {
      textBox.setValue(param.value());
      addWidget(g, textBox, param);
    }
    saveEnabler.listenTo(textBox);
    return textBox;
  }

  private CheckBox renderCheckBox(LabeledWidgetsGrid g,
      ConfigParameterInfo param) {
    CheckBox checkBox = new CheckBox(getDisplayName(param));
    checkBox.setValue(Boolean.parseBoolean(param.value()));
    HorizontalPanel p = new HorizontalPanel();
    p.add(checkBox);
    if (param.description() != null) {
      Image infoImg = new Image(Gerrit.RESOURCES.info());
      infoImg.setTitle(param.description());
      p.add(infoImg);
    }
    if (param.warning() != null) {
      Image warningImg = new Image(Gerrit.RESOURCES.warning());
      warningImg.setTitle(param.warning());
      p.add(warningImg);
    }
    g.add((String)null, p);
    saveEnabler.listenTo(checkBox);
    return checkBox;
  }

  private ListBox renderListBox(LabeledWidgetsGrid g,
      ConfigParameterInfo param) {
    ListBox listBox = new ListBox();
    if (param.inheritable()) {
      listBox.addItem(
          Util.M.pluginProjectInheritedListValue(param.inheritedValue()));
      if (param.configuredValue() == null) {
        listBox.setSelectedIndex(0);
      }
      for (int i = 0; i < param.permittedValues().length(); i++) {
        String pv = param.permittedValues().get(i);
        listBox.addItem(pv);
        if (pv.equals(param.configuredValue())) {
          listBox.setSelectedIndex(i + 1);
        }
      }
    } else {
      for (int i = 0; i < param.permittedValues().length(); i++) {
        String pv = param.permittedValues().get(i);
        listBox.addItem(pv);
        if (pv.equals(param.value())) {
          listBox.setSelectedIndex(i);
        }
      }
    }

    if (param.editable()) {
      saveEnabler.listenTo(listBox);
      addWidget(g, listBox, param);
    } else {
      listBox.setEnabled(false);

      if (param.inheritable() && listBox.getSelectedIndex() != 0) {
        // the inherited value is not selected,
        // since the listBox is disabled the inherited value cannot be
        // seen and we have to display it explicitly
        Label inheritedLabel =
            new Label(Util.M.pluginProjectInheritedValue(param
                .inheritedValue()));
        inheritedLabel.setStyleName(Gerrit.RESOURCES.css()
            .pluginProjectConfigInheritedValue());
        HorizontalPanel p = new HorizontalPanel();
        p.add(listBox);
        p.add(inheritedLabel);
        addWidget(g, p, param);
      } else {
        addWidget(g, listBox, param);
      }
    }

    return listBox;
  }

  private void addWidget(LabeledWidgetsGrid g, Widget w, ConfigParameterInfo param) {
    if (param.description() != null || param.warning() != null) {
      HorizontalPanel p = new HorizontalPanel();
      p.add(new Label(getDisplayName(param)));
      if (param.description() != null) {
        Image infoImg = new Image(Gerrit.RESOURCES.info());
        infoImg.setTitle(param.description());
        p.add(infoImg);
      }
      if (param.warning() != null) {
        Image warningImg = new Image(Gerrit.RESOURCES.warning());
        warningImg.setTitle(param.warning());
        p.add(warningImg);
      }
      p.add(new Label(":"));
      g.add(p, w);
    } else {
      g.add(getDisplayName(param), w);
    }
  }

  private String getDisplayName(ConfigParameterInfo param) {
    return param.displayName() != null ? param.displayName() : param.name();
  }

  private void initProjectActions(ConfigInfo info) {
    actionsGrid.clear(true);
    actionsGrid.removeAllRows();

    NativeMap<ActionInfo> actions = info.actions();
    if (actions == null || actions.isEmpty()) {
      return;
    }
    actions.copyKeysIntoChildren("id");
    actionsGrid.addHeader(new SmallHeading(Util.C.headingProjectCommands()));
    FlowPanel actionsPanel = new FlowPanel();
    actionsPanel.setStyleName(Gerrit.RESOURCES.css().projectActions());
    actionsPanel.setVisible(true);
    actionsGrid.add(Util.C.headingCommands(), actionsPanel);
    for (String id : actions.keySet()) {
      actionsPanel.add(new ActionButton(getProjectKey(),
          actions.get(id)));
    }
  }

  private void doSave() {
    enableForm(false);
    saveProject.setEnabled(false);
    ProjectApi.setConfig(getProjectKey(), descTxt.getText().trim(),
        getBool(contributorAgreements), getBool(contentMerge),
        getBool(signedOffBy), getBool(requireChangeID),
        maxObjectSizeLimit.getText().trim(),
        Project.SubmitType.valueOf(submitType.getValue(submitType.getSelectedIndex())),
        Project.State.valueOf(state.getValue(state.getSelectedIndex())),
        getPluginConfigValues(), new GerritCallback<ConfigInfo>() {
          @Override
          public void onSuccess(ConfigInfo result) {
            enableForm();
            display(result);
          }

          @Override
          public void onFailure(Throwable caught) {
            enableForm();
            super.onFailure(caught);
          }
        });
  }

  private Map<String, Map<String, String>> getPluginConfigValues() {
    Map<String, Map<String, String>> pluginConfigValues =
        new HashMap<>(pluginConfigWidgets.size());
    for (Entry<String, Map<String, FocusWidget>> e : pluginConfigWidgets.entrySet()) {
      Map<String, String> values =
          new HashMap<String, String>(e.getValue().size());
      pluginConfigValues.put(e.getKey(), values);
      for (Entry<String, FocusWidget> e2 : e.getValue().entrySet()) {
        FocusWidget widget = e2.getValue();
        if (widget instanceof TextBox) {
          values.put(e2.getKey(), ((TextBox) widget).getValue().trim());
        } else if (widget instanceof CheckBox) {
          values.put(e2.getKey(), Boolean.toString(((CheckBox) widget).getValue()));
        } else if (widget instanceof ListBox) {
          ListBox listBox = (ListBox) widget;
          // the inherited value is at index 0,
          // if it is selected no value should be set on this project
          String value = listBox.getSelectedIndex() > 0
              ? listBox.getValue(listBox.getSelectedIndex()) : null;
          values.put(e2.getKey(), value);
        }
      }
    }
    return pluginConfigValues;
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
          if (Gerrit.getConfig().getSshdAddress() != null && hasUserName()) {
            commands.add(
                cmdLinkfactory.new CloneWithCommitMsgHookCommandLink(getProjectKey()));
          }
        }
      }
    }
  }

  private static boolean hasUserName() {
    return Gerrit.isSignedIn()
        && Gerrit.getUserAccount().getUserName() != null
        && Gerrit.getUserAccount().getUserName().length() > 0;
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

    public void add(Widget label, Widget widget) {
      int row = getRowCount();
      insertRow(row);
      setWidget(row, 0, label);
      setWidget(row, 1, widget);
    }
  }
}
