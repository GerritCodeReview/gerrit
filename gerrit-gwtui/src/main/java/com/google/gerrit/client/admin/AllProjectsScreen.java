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
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class AllProjectsScreen extends ProjectListScreen {
  private NpTextBox filterTxt;
  private String subname;

  @Override
  protected void onLoad() {
    super.onLoad();
    if (subname == null || "".equals(subname)) {
      ProjectMap.all(new ScreenLoadCallback<ProjectMap>(this) {
        @Override
        protected void preDisplay(final ProjectMap result) {
          display(result);
        }
      });
    } else {
      ProjectMap.match(subname, new ScreenLoadCallback<ProjectMap>(this) {
        @Override
        protected void preDisplay(final ProjectMap result) {
          display(result);
        }
      });
    }
  }

  @Override
  protected void initPageHeader() {
    final HorizontalPanel hp = new HorizontalPanel();
    hp.setStyleName(Gerrit.RESOURCES.css().filterPanel());
    final Label filterLabel = new Label(Util.C.filter());
    filterLabel.setStyleName(Gerrit.RESOURCES.css().filterLabel());
    hp.add(filterLabel);
    filterTxt = new NpTextBox();
    filterTxt.setWidth("200px");
    filterTxt.setValue(subname);
    filterTxt.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        subname = filterTxt.getValue();
        onLoad();
      }
    });
    hp.add(filterTxt);
    add(hp);
  }

  @Override
  public void onShowView() {
    super.onShowView();
    filterTxt.setFocus(true);
  }
}
