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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.Util;
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.HidePopupPanelCommand;
import com.google.gwtexpui.globalkey.client.NpTextBox;

/** A popup containing all projects. */
public class ProjectListPopup {
  private HighlightingProjectsTable projectsTab;
  private DialogBox popup;
  private NpTextBox filterTxt;
  private HorizontalPanel filterPanel;
  private String match;
  private Query query;
  private Button closeTop;
  private Button closeBottom;
  private ScrollPanel sp;
  private PopupPanel.PositionCallback popupPosition;
  private int preferredTop;
  private int preferredLeft;
  private boolean poppingUp;
  private boolean firstPopupLoad = true;

  public void initPopup(final String popupText, final String currentPageLink) {
    createWidgets(popupText, currentPageLink);
    final FlowPanel pfp = new FlowPanel();
    pfp.add(filterPanel);
    pfp.add(closeTop);
    sp = new ScrollPanel(projectsTab);
    sp.setSize("100%", "100%");
    pfp.add(sp);
    pfp.add(closeBottom);
    popup.setWidget(pfp);
    popup.setHeight("100%");
    popupPosition = getPositionCallback();
  }

  protected PopupPanel.PositionCallback getPositionCallback() {
    return new PopupPanel.PositionCallback() {
      @Override
      public void setPosition(int offsetWidth, int offsetHeight) {
        if (preferredTop + offsetHeight > Window.getClientWidth()) {
          preferredTop = Window.getClientWidth() - offsetHeight;
        }
        if (preferredLeft + offsetWidth > Window.getClientWidth()) {
          preferredLeft = Window.getClientWidth() - offsetWidth;
        }

        if (preferredTop < 0) {
          sp.setHeight((sp.getOffsetHeight() + preferredTop) + "px");
          preferredTop = 0;
        }
        if (preferredLeft < 0) {
          sp.setWidth((sp.getOffsetWidth() + preferredLeft) + "px");
          preferredLeft = 0;
        }

        popup.setPopupPosition(preferredLeft, preferredTop);
      }
    };
  }

  /**
   * Invoked after moving pointer to a project.
   *
   * @param projectName project name.
   */
  protected void onMovePointerTo(String projectName) {}

  /**
   * Invoked after opening a project row.
   *
   * @param projectName project name.
   */
  protected void openRow(String projectName) {}

  public boolean isPoppingUp() {
    return poppingUp;
  }

  private void createWidgets(final String popupText, final String currentPageLink) {
    filterPanel = new HorizontalPanel();
    filterPanel.setStyleName(Gerrit.RESOURCES.css().projectFilterPanel());
    final Label filterLabel = new Label(com.google.gerrit.client.admin.Util.C.projectFilter());
    filterLabel.setStyleName(Gerrit.RESOURCES.css().projectFilterLabel());
    filterPanel.add(filterLabel);
    filterTxt = new NpTextBox();
    filterTxt.addKeyUpHandler(
        new KeyUpHandler() {
          @Override
          public void onKeyUp(KeyUpEvent event) {
            Query q = new Query(filterTxt.getValue());
            if (!match.equals(q.qMatch)) {
              if (query == null) {
                q.run();
              }
              query = q;
            }
          }
        });
    filterPanel.add(filterTxt);

    projectsTab =
        new HighlightingProjectsTable() {
          @Override
          protected void movePointerTo(final int row, final boolean scroll) {
            super.movePointerTo(row, scroll);
            onMovePointerTo(getRowItem(row).name());
          }

          @Override
          protected void onOpenRow(final int row) {
            super.onOpenRow(row);
            openRow(getRowItem(row).name());
          }
        };
    projectsTab.setSavePointerId(currentPageLink);

    closeTop = createCloseButton();
    closeBottom = createCloseButton();

    popup = new DialogBox();
    popup.setModal(false);
    popup.setText(popupText);
  }

  private Button createCloseButton() {
    Button close = new Button(Util.C.projectsClose());
    close.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            closePopup();
          }
        });
    return close;
  }

  public void displayPopup() {
    poppingUp = true;
    if (firstPopupLoad) { // For sizing/positioning, delay display until loaded
      match = "";
      query = new Query(match).run();
    } else {
      popup.setPopupPositionAndShow(popupPosition);
      GlobalKey.dialog(popup);
      GlobalKey.addApplication(popup, new HidePopupPanelCommand(0, KeyCodes.KEY_ESCAPE, popup));
      projectsTab.setRegisterKeys(true);
      projectsTab.finishDisplay();
      filterTxt.setFocus(true);
      poppingUp = false;
    }
  }

  public void closePopup() {
    popup.hide();
  }

  public void setPreferredCoordinates(final int top, final int left) {
    this.preferredTop = top;
    this.preferredLeft = left;
  }

  private class Query {
    private final String qMatch;

    Query(String match) {
      this.qMatch = match;
    }

    Query run() {
      ProjectMap.match(
          qMatch,
          new GerritCallback<ProjectMap>() {
            @Override
            public void onSuccess(ProjectMap result) {
              if (!firstPopupLoad && !popup.isShowing()) {
                query = null;
              } else if (query == Query.this) {
                query = null;
                showMap(result);
              } else {
                query.run();
              }
            }
          });
      return this;
    }

    private void showMap(ProjectMap result) {
      ProjectListPopup.this.match = qMatch;
      projectsTab.display(result, qMatch);

      if (firstPopupLoad) {
        // Display was delayed until table was loaded
        firstPopupLoad = false;
        displayPopup();
      }
    }
  }
}
