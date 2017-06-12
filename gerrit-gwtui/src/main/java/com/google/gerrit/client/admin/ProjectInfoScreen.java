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
import com.google.gerrit.client.GerritUiExtensionPoint;
import com.google.gerrit.client.StringListPanel;
import com.google.gerrit.client.access.AccessMap;
import com.google.gerrit.client.access.ProjectAccessInfo;
import com.google.gerrit.client.actions.ActionButton;
import com.google.gerrit.client.api.ExtensionPanel;
import com.google.gerrit.client.change.Resources;
import com.google.gerrit.client.download.DownloadPanel;
import com.google.gerrit.client.info.ActionInfo;
import com.google.gerrit.client.info.DownloadInfo.DownloadCommandInfo;
import com.google.gerrit.client.info.DownloadInfo.DownloadSchemeInfo;
import com.google.gerrit.client.projects.ConfigInfo;
import com.google.gerrit.client.projects.ConfigInfo.ConfigParameterInfo;
import com.google.gerrit.client.projects.ConfigInfo.ConfigParameterValue;
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
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasEnabled;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
  private ListBox newChangeForAllNotInTarget;
  private ListBox enableSignedPush;
  private ListBox requireSignedPush;
  private ListBox rejectImplicitMerges;
  private NpTextBox maxObjectSizeLimit;
  private Label effectiveMaxObjectSizeLimit;
  private Map<String, Map<String, HasEnabled>> pluginConfigWidgets;

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
    saveProject.setStyleName("");
    saveProject.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            doSave();
          }
        });

    ExtensionPanel extensionPanelTop =
        new ExtensionPanel(GerritUiExtensionPoint.PROJECT_INFO_SCREEN_TOP);
    extensionPanelTop.put(GerritUiExtensionPoint.Key.PROJECT_NAME, getProjectKey().get());
    add(extensionPanelTop);

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

    ExtensionPanel extensionPanelBottom =
        new ExtensionPanel(GerritUiExtensionPoint.PROJECT_INFO_SCREEN_BOTTOM);
    extensionPanelBottom.put(GerritUiExtensionPoint.Key.PROJECT_NAME, getProjectKey().get());
    add(extensionPanelBottom);
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    Project.NameKey project = getProjectKey();
    CallbackGroup cbg = new CallbackGroup();
    AccessMap.get(
        project,
        cbg.add(
            new GerritCallback<ProjectAccessInfo>() {
              @Override
              public void onSuccess(ProjectAccessInfo result) {
                isOwner = result.isOwner();
                enableForm();
                saveProject.setVisible(isOwner);
              }
            }));
    ProjectApi.getConfig(
        project,
        cbg.addFinal(
            new ScreenLoadCallback<ConfigInfo>(this) {
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
    state.setEnabled(isOwner);
    submitType.setEnabled(isOwner);
    setEnabledForUseContentMerge();
    newChangeForAllNotInTarget.setEnabled(isOwner);
    if (enableSignedPush != null) {
      enableSignedPush.setEnabled(isOwner);
    }
    descTxt.setEnabled(isOwner);
    contributorAgreements.setEnabled(isOwner);
    signedOffBy.setEnabled(isOwner);
    requireChangeID.setEnabled(isOwner);
    rejectImplicitMerges.setEnabled(isOwner);
    maxObjectSizeLimit.setEnabled(isOwner);

    if (pluginConfigWidgets != null) {
      for (Map<String, HasEnabled> widgetMap : pluginConfigWidgets.values()) {
        for (HasEnabled widget : widgetMap.values()) {
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

    state = new ListBox();
    for (ProjectState stateValue : ProjectState.values()) {
      state.addItem(Util.toLongString(stateValue), stateValue.name());
    }
    saveEnabler.listenTo(state);
    grid.add(Util.C.headingProjectState(), state);

    submitType = new ListBox();
    for (final SubmitType type : SubmitType.values()) {
      submitType.addItem(Util.toLongString(type), type.name());
    }
    submitType.addChangeHandler(
        new ChangeHandler() {
          @Override
          public void onChange(ChangeEvent event) {
            setEnabledForUseContentMerge();
          }
        });
    saveEnabler.listenTo(submitType);
    grid.add(Util.C.headingProjectSubmitType(), submitType);

    contentMerge = newInheritedBooleanBox();
    saveEnabler.listenTo(contentMerge);
    grid.add(Util.C.useContentMerge(), contentMerge);

    newChangeForAllNotInTarget = newInheritedBooleanBox();
    saveEnabler.listenTo(newChangeForAllNotInTarget);
    grid.add(Util.C.createNewChangeForAllNotInTarget(), newChangeForAllNotInTarget);

    requireChangeID = newInheritedBooleanBox();
    saveEnabler.listenTo(requireChangeID);
    grid.addHtml(Util.C.requireChangeID(), requireChangeID);

    if (Gerrit.info().receive().enableSignedPush()) {
      enableSignedPush = newInheritedBooleanBox();
      saveEnabler.listenTo(enableSignedPush);
      grid.add(Util.C.enableSignedPush(), enableSignedPush);
      requireSignedPush = newInheritedBooleanBox();
      saveEnabler.listenTo(requireSignedPush);
      grid.add(Util.C.requireSignedPush(), requireSignedPush);
    }

    rejectImplicitMerges = newInheritedBooleanBox();
    saveEnabler.listenTo(rejectImplicitMerges);
    grid.addHtml(Util.C.rejectImplicitMerges(), rejectImplicitMerges);

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
   * Enables the {@link #contentMerge} checkbox if the selected submit type allows the usage of
   * content merge. If the submit type (currently only 'Fast Forward Only') does not allow content
   * merge the useContentMerge checkbox gets disabled.
   */
  private void setEnabledForUseContentMerge() {
    if (SubmitType.FAST_FORWARD_ONLY.equals(
        SubmitType.valueOf(submitType.getValue(submitType.getSelectedIndex())))) {
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
    if (Gerrit.info().auth().useContributorAgreements()) {
      saveEnabler.listenTo(contributorAgreements);
      grid.add(Util.C.useContributorAgreements(), contributorAgreements);
    }

    signedOffBy = newInheritedBooleanBox();
    saveEnabler.listenTo(signedOffBy);
    grid.addHtml(Util.C.useSignedOffBy(), signedOffBy);
  }

  private void setSubmitType(final SubmitType newSubmitType) {
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

  private void setState(final ProjectState newState) {
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
    if (box == null) {
      return;
    }
    int inheritedIndex = -1;
    for (int i = 0; i < box.getItemCount(); i++) {
      if (box.getValue(i).startsWith(InheritableBoolean.INHERIT.name())) {
        inheritedIndex = i;
      }
      if (box.getValue(i).startsWith(inheritedBoolean.configuredValue().name())) {
        box.setSelectedIndex(i);
      }
    }
    if (inheritedIndex >= 0) {
      if (Gerrit.info().gerrit().isAllProjects(getProjectKey())) {
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
        box.setItemText(
            inheritedIndex,
            InheritableBoolean.INHERIT.name() + " (" + inheritedBoolean.inheritedValue() + ")");
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
    setBool(contributorAgreements, result.useContributorAgreements());
    setBool(signedOffBy, result.useSignedOffBy());
    setBool(contentMerge, result.useContentMerge());
    setBool(newChangeForAllNotInTarget, result.createNewChangeForAllNotInTarget());
    setBool(requireChangeID, result.requireChangeId());
    if (Gerrit.info().receive().enableSignedPush()) {
      setBool(enableSignedPush, result.enableSignedPush());
      setBool(requireSignedPush, result.requireSignedPush());
    }
    setBool(rejectImplicitMerges, result.rejectImplicitMerges());
    setSubmitType(result.submitType());
    setState(result.state());
    maxObjectSizeLimit.setText(result.maxObjectSizeLimit().configuredValue());
    if (result.maxObjectSizeLimit().inheritedValue() != null) {
      effectiveMaxObjectSizeLimit.setVisible(true);
      effectiveMaxObjectSizeLimit.setText(
          Util.M.effectiveMaxObjectSizeLimit(result.maxObjectSizeLimit().value()));
      effectiveMaxObjectSizeLimit.setTitle(
          Util.M.globalMaxObjectSizeLimit(result.maxObjectSizeLimit().inheritedValue()));
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
      Map<String, HasEnabled> widgetMap = new HashMap<>();
      pluginConfigWidgets.put(pluginName, widgetMap);
      LabeledWidgetsGrid g = new LabeledWidgetsGrid();
      g.addHeader(new SmallHeading(Util.M.pluginProjectOptionsTitle(pluginName)));
      pluginOptionsPanel.add(g);
      NativeMap<ConfigParameterInfo> pluginConfig = info.pluginConfig(pluginName);
      pluginConfig.copyKeysIntoChildren("name");
      for (ConfigParameterInfo param : Natives.asList(pluginConfig.values())) {
        HasEnabled w;
        switch (param.type()) {
          case "STRING":
          case "INT":
          case "LONG":
            w = renderTextBox(g, param);
            break;
          case "BOOLEAN":
            w = renderCheckBox(g, param);
            break;
          case "LIST":
            w = renderListBox(g, param);
            break;
          case "ARRAY":
            w = renderStringListPanel(g, param);
            break;
          default:
            throw new UnsupportedOperationException("unsupported widget type");
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

  private TextBox renderTextBox(LabeledWidgetsGrid g, ConfigParameterInfo param) {
    NpTextBox textBox = param.type().equals("STRING") ? new NpTextBox() : new NpIntTextBox();
    if (param.inheritable()) {
      textBox.setValue(param.configuredValue());
      Label inheritedLabel = new Label(Util.M.pluginProjectInheritedValue(param.inheritedValue()));
      inheritedLabel.setStyleName(Gerrit.RESOURCES.css().pluginProjectConfigInheritedValue());
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

  private CheckBox renderCheckBox(LabeledWidgetsGrid g, ConfigParameterInfo param) {
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
    g.add((String) null, p);
    saveEnabler.listenTo(checkBox);
    return checkBox;
  }

  private ListBox renderListBox(LabeledWidgetsGrid g, ConfigParameterInfo param) {
    if (param.permittedValues() == null) {
      return null;
    }
    ListBox listBox = new ListBox();
    if (param.inheritable()) {
      listBox.addItem(Util.M.pluginProjectInheritedListValue(param.inheritedValue()));
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
            new Label(Util.M.pluginProjectInheritedValue(param.inheritedValue()));
        inheritedLabel.setStyleName(Gerrit.RESOURCES.css().pluginProjectConfigInheritedValue());
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

  private StringListPanel renderStringListPanel(LabeledWidgetsGrid g, ConfigParameterInfo param) {
    StringListPanel p =
        new StringListPanel(null, Arrays.asList(getDisplayName(param)), saveProject, false);
    List<List<String>> values = new ArrayList<>();
    for (String v : Natives.asList(param.values())) {
      values.add(Arrays.asList(v));
    }
    p.display(values);
    if (!param.editable()) {
      p.setEnabled(false);
    }
    addWidget(g, p, param);
    return p;
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
    boolean showCreateChange = Gerrit.isSignedIn();

    NativeMap<ActionInfo> actions = info.actions();
    if (actions == null) {
      actions = NativeMap.create().cast();
    }
    if (actions.isEmpty() && !showCreateChange) {
      return;
    }
    actions.copyKeysIntoChildren("id");
    actionsGrid.addHeader(new SmallHeading(Util.C.headingProjectCommands()));
    FlowPanel actionsPanel = new FlowPanel();
    actionsPanel.setStyleName(Gerrit.RESOURCES.css().projectActions());
    actionsPanel.setVisible(true);
    actionsGrid.add(Util.C.headingCommands(), actionsPanel);

    for (String id : actions.keySet()) {
      actionsPanel.add(new ActionButton(getProjectKey(), actions.get(id)));
    }

    // TODO: The user should have create permission on the branch referred to by
    // HEAD. This would have to happen on the server side.
    if (showCreateChange) {
      actionsPanel.add(createChangeAction());
    }

    if (isOwner) {
      actionsPanel.add(createEditConfigAction());
    }
  }

  private Button createChangeAction() {
    final Button createChange = new Button(Util.C.buttonCreateChange());
    createChange.setStyleName("");
    createChange.setTitle(Util.C.buttonCreateChangeDescription());
    createChange.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            CreateChangeAction.call(createChange, getProjectKey().get());
          }
        });
    return createChange;
  }

  private Button createEditConfigAction() {
    final Button editConfig = new Button(Util.C.buttonEditConfig());
    editConfig.setStyleName("");
    editConfig.setTitle(Util.C.buttonEditConfigDescription());
    editConfig.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            EditConfigAction.call(editConfig, getProjectKey().get());
          }
        });
    return editConfig;
  }

  private void doSave() {
    enableForm(false);
    saveProject.setEnabled(false);
    InheritableBoolean esp = enableSignedPush != null ? getBool(enableSignedPush) : null;
    InheritableBoolean rsp = requireSignedPush != null ? getBool(requireSignedPush) : null;
    ProjectApi.setConfig(
        getProjectKey(),
        descTxt.getText().trim(),
        getBool(contributorAgreements),
        getBool(contentMerge),
        getBool(signedOffBy),
        getBool(newChangeForAllNotInTarget),
        getBool(requireChangeID),
        esp,
        rsp,
        getBool(rejectImplicitMerges),
        maxObjectSizeLimit.getText().trim(),
        SubmitType.valueOf(submitType.getValue(submitType.getSelectedIndex())),
        ProjectState.valueOf(state.getValue(state.getSelectedIndex())),
        getPluginConfigValues(),
        new GerritCallback<ConfigInfo>() {
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

  private Map<String, Map<String, ConfigParameterValue>> getPluginConfigValues() {
    Map<String, Map<String, ConfigParameterValue>> pluginConfigValues =
        new HashMap<>(pluginConfigWidgets.size());
    for (Entry<String, Map<String, HasEnabled>> e : pluginConfigWidgets.entrySet()) {
      Map<String, ConfigParameterValue> values = new HashMap<>(e.getValue().size());
      pluginConfigValues.put(e.getKey(), values);
      for (Entry<String, HasEnabled> e2 : e.getValue().entrySet()) {
        HasEnabled widget = e2.getValue();
        if (widget instanceof TextBox) {
          values.put(
              e2.getKey(),
              ConfigParameterValue.create().value(((TextBox) widget).getValue().trim()));
        } else if (widget instanceof CheckBox) {
          values.put(
              e2.getKey(),
              ConfigParameterValue.create()
                  .value(Boolean.toString(((CheckBox) widget).getValue())));
        } else if (widget instanceof ListBox) {
          ListBox listBox = (ListBox) widget;
          // the inherited value is at index 0,
          // if it is selected no value should be set on this project
          String value =
              listBox.getSelectedIndex() > 0 ? listBox.getValue(listBox.getSelectedIndex()) : null;
          values.put(e2.getKey(), ConfigParameterValue.create().value(value));
        } else if (widget instanceof StringListPanel) {
          values.put(
              e2.getKey(),
              ConfigParameterValue.create()
                  .values(((StringListPanel) widget).getValues(0).toArray(new String[] {})));
        } else {
          throw new UnsupportedOperationException("unsupported widget type");
        }
      }
    }
    return pluginConfigValues;
  }

  public static class ProjectDownloadPanel extends DownloadPanel {
    public ProjectDownloadPanel(String project, boolean isAllowsAnonymous) {
      super(project, isAllowsAnonymous);
    }

    @Override
    protected List<DownloadCommandInfo> getCommands(DownloadSchemeInfo schemeInfo) {
      return schemeInfo.cloneCommands(project);
    }
  }

  private static class LabeledWidgetsGrid extends FlexTable {
    private String labelSuffix;

    LabeledWidgetsGrid() {
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
