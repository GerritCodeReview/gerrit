// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.client.VoidResult;

public class ProjectAdminScreen extends AccountScreen {
  private Project.Id projectId;

  private TextBox ownerTxtBox;
  private SuggestBox ownerTxt;
  private Button saveOwner;

  private TextArea descTxt;
  private Button saveDesc;

  public ProjectAdminScreen(final Project.Id toShow) {
    projectId = toShow;
  }

  @Override
  public void onLoad() {
    if (descTxt == null) {
      initUI();
    }

    enableForm(false);
    saveOwner.setEnabled(false);
    saveDesc.setEnabled(false);
    super.onLoad();

    Util.PROJECT_SVC.projectDetail(projectId,
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            enableForm(true);
            saveOwner.setEnabled(false);
            saveDesc.setEnabled(false);
            display(result);
          }
        });
  }

  private void enableForm(final boolean on) {
    ownerTxtBox.setEnabled(on);
    descTxt.setEnabled(on);
  }

  private void initUI() {
    initOwner();
    initDescription();
  }


  private void initOwner() {
    final VerticalPanel ownerPanel = new VerticalPanel();
    final Label ownerHdr = new Label(Util.C.headingOwner());
    ownerHdr.setStyleName("gerrit-SmallHeading");
    ownerPanel.add(ownerHdr);

    ownerTxtBox = new TextBox();
    ownerTxtBox.setVisibleLength(60);
    ownerTxt = new SuggestBox(new AccountGroupSuggestOracle(), ownerTxtBox);
    ownerPanel.add(ownerTxt);

    saveOwner = new Button(Util.C.buttonChangeGroupOwner());
    saveOwner.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
        final String newOwner = ownerTxt.getText().trim();
        if (newOwner.length() > 0) {
          Util.PROJECT_SVC.changeProjectOwner(projectId, newOwner,
              new GerritCallback<VoidResult>() {
                public void onSuccess(final VoidResult result) {
                  saveOwner.setEnabled(false);
                }
              });
        }
      }
    });
    ownerPanel.add(saveOwner);
    add(ownerPanel);

    new TextSaveButtonListener(ownerTxtBox, saveOwner);
  }

  private void initDescription() {
    final VerticalPanel vp = new VerticalPanel();
    final Label descHdr = new Label(Util.C.headingDescription());
    descHdr.setStyleName("gerrit-SmallHeading");
    vp.add(descHdr);

    descTxt = new TextArea();
    descTxt.setVisibleLines(6);
    descTxt.setCharacterWidth(60);
    vp.add(descTxt);

    saveDesc = new Button(Util.C.buttonSaveDescription());
    saveDesc.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
        final String txt = descTxt.getText().trim();
        Util.PROJECT_SVC.changeProjectDescription(projectId, txt,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                saveDesc.setEnabled(false);
              }
            });
      }
    });
    vp.add(saveDesc);
    add(vp);

    new TextSaveButtonListener(descTxt, saveDesc);
  }

  private void display(final ProjectDetail result) {
    setTitleText(Util.M.project(result.project.getName()));
    ownerTxt.setText(result.ownerGroup.getName());
    descTxt.setText(result.project.getDescription());
  }
}
