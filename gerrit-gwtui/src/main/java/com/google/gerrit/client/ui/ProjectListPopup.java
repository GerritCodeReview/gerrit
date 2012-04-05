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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.account.Util;
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.HidePopupPanelCommand;
import com.google.gwtexpui.user.client.PluginSafeDialogBox;

import javax.annotation.Nullable;

/** It creates a popup containing all the projects. */
public class ProjectListPopup {
  final private HandlerManager handlerManager = new HandlerManager(this);

  private ProjectsTable projectsTab;
  private PluginSafeDialogBox popup;
  private Button close;
  private ScrollPanel sp;
  private PopupPanel.PositionCallback popupPosition;

  private int preferredPopupWidth = -1;
  private int top;
  private int left;

  private boolean firstPopupLoad = true;

  private SuggestBox nameBox;
  private ResizeHandler parentScreen;
  private HandlerRegistration regWindowResize;

  public ProjectListPopup(final String popupText, final String currentPageLink,
      final SuggestBox inputText, @Nullable final ResizeHandler parent) {
    nameBox = inputText;
    parentScreen = parent;
    createWidgets(popupText, currentPageLink);
    initPopup();

    addOpenRowHandler(new ProjectListPopupOnOpenRowHandler() {

      @Override
      public void onOpenProjectRow(
          ProjectListPopupOnOpenRowEvent projectListPopupEvent) {
        if (nameBox != null) {
          nameBox.setText(projectListPopupEvent.getProjectName());
        }
      }
    });
  }

  protected void initPopup() {
    final FlowPanel pfp = new FlowPanel();
    sp = new ScrollPanel(projectsTab);
    pfp.add(sp);
    pfp.add(close);
    popup.setWidget(pfp);

    popupPosition = new PopupPanel.PositionCallback() {
      @Override
      public void setPosition(int offsetWidth, int offsetHeight) {
        if (preferredPopupWidth == -1) {
          preferredPopupWidth = offsetWidth;
        }

        if (top + offsetHeight > Window.getClientWidth()) {
          top = Window.getClientWidth() - offsetHeight;
        }
        if (left + offsetWidth > Window.getClientWidth()) {
          left = Window.getClientWidth() - offsetWidth;
        }

        if (top < 0) {
          sp.setHeight((sp.getOffsetHeight() + top) + "px");
          top = 0;
        }
        if (left < 0) {
          sp.setWidth((sp.getOffsetWidth() + left) + "px");
          left = 0;
        }

        popup.setPopupPosition(left, top);
      }
    };
  }

  protected void createWidgets(final String popupText,
      final String currentPageLink) {
    projectsTab = new ProjectsTable() {

      @Override
      protected void movePointerTo(final int row, final boolean scroll) {
        super.movePointerTo(row, scroll);
        if (nameBox != null) {
          nameBox.setText(getRowItem(row).name());
        }
      }

      @Override
      protected void onOpenRow(final int row) {
        super.onOpenRow(row);
        handlerManager.fireEvent(new ProjectListPopupOnOpenRowEvent(getRowItem(
            row).name()));
      }
    };
    projectsTab.setSavePointerId(currentPageLink);

    close = new Button(Util.C.projectsClose());
    close.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        closePopup();
      }
    });

    popup = new PluginSafeDialogBox();
    popup.setModal(false);
    popup.setText(popupText);
  }

  public void display() {
    if (firstPopupLoad) { // For sizing/positioning, delay display until loaded
      populateProjects();
    } else {
      popup.setPopupPositionAndShow(popupPosition);

      GlobalKey.dialog(popup);
      GlobalKey.addApplication(popup, new HidePopupPanelCommand(0,
          KeyCodes.KEY_ESCAPE, popup));

      projectsTab.setRegisterKeys(true);
      projectsTab.finishDisplay();

    }
    if (regWindowResize == null && parentScreen != null) {
      regWindowResize = Window.addResizeHandler(parentScreen);

    }
  }

  public void closePopup() {
    popup.hide();
    if (regWindowResize != null) {
      regWindowResize.removeHandler();
      regWindowResize = null;
    }
  }

  public void resize() {
    sp.setSize("100%", "100%");

    // For some reason keeping track of preferredWidth keeps the width better,
    // but using 100% for height works better.
    popup.setHeight("100%");
    popupPosition.setPosition(preferredPopupWidth, popup.getOffsetHeight());
  }

  public void setCoordinates(final int top, final int left) {
    this.top = top;
    this.left = left;
  }

  protected void populateProjects() {
    ProjectMap.all(new GerritCallback<ProjectMap>() {
      @Override
      public void onSuccess(final ProjectMap result) {
        projectsTab.display(result);
        if (firstPopupLoad) { // Display was delayed until table was loaded
          firstPopupLoad = false;
          display();
        }
      }
    });
  }

  public interface ProjectListPopupOnOpenRowHandler extends EventHandler {
    public void onOpenProjectRow(
        ProjectListPopupOnOpenRowEvent projectListPopupEvent);
  }

  public static class ProjectListPopupOnOpenRowEvent extends
      GwtEvent<ProjectListPopupOnOpenRowHandler> {

    private static final Type<ProjectListPopupOnOpenRowHandler> TYPE =
        new Type<ProjectListPopupOnOpenRowHandler>();

    private String projectName;

    public ProjectListPopupOnOpenRowEvent() {
    }

    public ProjectListPopupOnOpenRowEvent(final String projectName) {
      this.projectName = projectName;
    }

    public static Type<ProjectListPopupOnOpenRowHandler> getType() {
      return TYPE;
    }

    public String getProjectName() {
      return projectName;
    }

    @Override
    protected void dispatch(ProjectListPopupOnOpenRowHandler handler) {
      handler.onOpenProjectRow(this);
    }

    @Override
    public Type<ProjectListPopupOnOpenRowHandler> getAssociatedType() {
      return TYPE;
    }
  }

  public void addOpenRowHandler(ProjectListPopupOnOpenRowHandler handler) {
    handlerManager
        .addHandler(ProjectListPopupOnOpenRowEvent.getType(), handler);
  }
}
