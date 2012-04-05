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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.data.ProjectList;
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
  private boolean popingUp;

  private static SuggestBox nameBox;
  private ResizeHandler parentScreen;
  private HandlerRegistration regWindowResize;

  public ProjectListPopup(final String popupText, final String currentPageLink,
      final SuggestBox inputText, final ResizeHandler parent) {
    nameBox = inputText;
    parentScreen = parent;
    createWidgets(popupText, currentPageLink);
    initPopup();

    addProjectListPopupOnCloseHandler(new ProjectListPopupOnCloseHandler() {
      @Override
      public void onClose(ProjectListPopupOnCloseEvent projectListPopupEvent) {
        resetHandlerRegistration();
      }
    });

    addProjectListPopupOnOpenRowHandler(new ProjectListPopupOnOpenRowHandler() {

      @Override
      public void onOpenProjectRow(
          ProjectListPopupOnOpenRowEvent projectListPopupEvent) {
        if (nameBox != null) {
          nameBox.setText(projectListPopupEvent.getProjectName());
        }
      }
    });

    addProjectListPopupOnMovePointerHandler(new ProjectListPopupOnMovePointerHandler() {
      @Override
      public void onMovePointer(
          ProjectListPopupOnMovePointerEvent projectListPopupEvent) {
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
        handlerManager.fireEvent(new ProjectListPopupOnMovePointerEvent(
            popingUp, getRowItem(row).getName()));
      }

      @Override
      protected void onOpenRow(final int row) {
        super.onOpenRow(row);
        handlerManager.fireEvent(new ProjectListPopupOnOpenRowEvent(getRowItem(
            row).getName()));
      }
    };
    projectsTab.setSavePointerId(currentPageLink);

    close = new Button(Util.C.projectsClose());
    close.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        closePopup();
        handlerManager.fireEvent(new ProjectListPopupOnCloseEvent());
      }
    });

    popup = new PluginSafeDialogBox();
    popup.setModal(false);
    popup.setText(popupText);
  }

  public void display() {
    popingUp = true;
    if (firstPopupLoad) { // For sizing/positioning, delay display until loaded
      populateProjects();
    } else {
      popup.setPopupPositionAndShow(popupPosition);

      GlobalKey.dialog(popup);
      GlobalKey.addApplication(popup, new HidePopupPanelCommand(0,
          KeyCodes.KEY_ESCAPE, popup));

      projectsTab.setRegisterKeys(true);
      projectsTab.finishDisplay();

      popingUp = false;
    }
    if (regWindowResize == null) {
      regWindowResize = Window.addResizeHandler(parentScreen);

    }
  }

  public void closePopup() {
    popup.hide();
    resetHandlerRegistration();
  }

  protected void resetHandlerRegistration() {
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
    Util.PROJECT_SVC.visibleProjects(new GerritCallback<ProjectList>() {
      @Override
      public void onSuccess(final ProjectList result) {
        projectsTab.display(result.getProjects());
        if (firstPopupLoad) { // Display was delayed until table was loaded
          firstPopupLoad = false;
          display();
        }
      }
    });
  }

  public interface ProjectListPopupOnCloseHandler extends EventHandler {
    public void onClose(ProjectListPopupOnCloseEvent projectListPopupEvent);
  }

  public interface ProjectListPopupOnOpenRowHandler extends EventHandler {
    public void onOpenProjectRow(
        ProjectListPopupOnOpenRowEvent projectListPopupEvent);
  }

  public interface ProjectListPopupOnMovePointerHandler extends EventHandler {
    public void onMovePointer(
        ProjectListPopupOnMovePointerEvent projectListPopupEvent);
  }

  public static class ProjectListPopupOnCloseEvent extends
      GwtEvent<ProjectListPopupOnCloseHandler> {
    private static final Type<ProjectListPopupOnCloseHandler> TYPE =
        new Type<ProjectListPopupOnCloseHandler>();

    public ProjectListPopupOnCloseEvent() {
    }

    public static Type<ProjectListPopupOnCloseHandler> getType() {
      return TYPE;
    }

    @Override
    protected void dispatch(ProjectListPopupOnCloseHandler handler) {
      handler.onClose(this);
    }

    @Override
    public GwtEvent.Type<ProjectListPopupOnCloseHandler> getAssociatedType() {
      // TODO Auto-generated method stub
      return TYPE;
    }
  }

  public static class ProjectListPopupOnMovePointerEvent extends
      GwtEvent<ProjectListPopupOnMovePointerHandler> {
    private static final Type<ProjectListPopupOnMovePointerHandler> TYPE =
        new Type<ProjectListPopupOnMovePointerHandler>();
    private boolean popingUp;
    private String projectName;

    public ProjectListPopupOnMovePointerEvent() {
    }

    public static Type<ProjectListPopupOnMovePointerHandler> getType() {
      return TYPE;
    }

    public ProjectListPopupOnMovePointerEvent(final boolean popingUp,
        String projectName) {
      this.popingUp = popingUp;
      this.projectName = projectName;
    }

    public boolean isPopingUp() {
      return popingUp;
    }

    public String getProjectName() {
      return projectName;
    }

    @Override
    protected void dispatch(ProjectListPopupOnMovePointerHandler handler) {
      handler.onMovePointer(this);
    }

    @Override
    public GwtEvent.Type<ProjectListPopupOnMovePointerHandler> getAssociatedType() {
      // TODO Auto-generated method stub
      return TYPE;
    }
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

  public void addProjectListPopupOnOpenRowHandler(
      ProjectListPopupOnOpenRowHandler handler) {
    handlerManager
        .addHandler(ProjectListPopupOnOpenRowEvent.getType(), handler);
  }

  public void addProjectListPopupOnMovePointerHandler(
      ProjectListPopupOnMovePointerHandler handler) {
    handlerManager.addHandler(ProjectListPopupOnMovePointerEvent.getType(),
        handler);
  }

  public void addProjectListPopupOnCloseHandler(
      ProjectListPopupOnCloseHandler handler) {
    handlerManager.addHandler(ProjectListPopupOnCloseEvent.getType(), handler);
  }
}
