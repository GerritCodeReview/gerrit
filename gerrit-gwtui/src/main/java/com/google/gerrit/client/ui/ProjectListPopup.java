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
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.HidePopupPanelCommand;
import com.google.gwtexpui.user.client.PluginSafeDialogBox;

/** It creates a popup containing all the projects. */
public class ProjectListPopup {
  private ProjectsTable projectsTab;
  private PluginSafeDialogBox popup;
  private Button close;
  private ScrollPanel sp;
  private PopupPanel.PositionCallback popupPosition;
  private int top;
  private int left;
  private boolean popingUp;
  private boolean firstPopupLoad = true;

  public void initPopup(final String popupText, final String currentPageLink) {
    createWidgets(popupText, currentPageLink);
    final FlowPanel pfp = new FlowPanel();
    sp = new ScrollPanel(projectsTab);
    sp.setSize("100%", "100%");
    pfp.add(sp);
    pfp.add(close);
    popup.setWidget(pfp);
    // For some reason keeping track of preferredWidth keeps the width better,
    // but using 100% for height works better.
    popup.setHeight("100%");
    popupPosition = getPostionCallback();
  }

  protected PopupPanel.PositionCallback getPostionCallback() {
    return new PopupPanel.PositionCallback() {
      @Override
      public void setPosition(int offsetWidth, int offsetHeight) {
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

  protected void feedbackonmovePointerTo(String projectName) {
  }

  protected void feedbackonOpenRow(String projectName) {
  }

  public boolean isPopUp() {
    return popingUp;
  }

  private void createWidgets(final String popupText,
      final String currentPageLink) {
    projectsTab = new ProjectsTable() {
      @Override
      protected void movePointerTo(final int row, final boolean scroll) {
        super.movePointerTo(row, scroll);
        feedbackonmovePointerTo(getRowItem(row).name());
      }

      @Override
      protected void onOpenRow(final int row) {
        super.onOpenRow(row);
        feedbackonOpenRow(getRowItem(row).name());
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

  public void displayPopup() {
    popingUp = true;
    if (firstPopupLoad) { // For sizing/positioning, delay display until loaded
      populateProjects();
    } else {
      popup.setPopupPositionAndShow(popupPosition);
      GlobalKey.dialog(popup);
      try {
        GlobalKey.addApplication(popup, new HidePopupPanelCommand(0,
            KeyCodes.KEY_ESCAPE, popup));
      } catch (Throwable e) {
      }
      projectsTab.setRegisterKeys(true);
      projectsTab.finishDisplay();
      popingUp = false;
    }
  }

  public void closePopup() {
    popup.hide();
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
          displayPopup();
        }
      }
    });
  }
}
