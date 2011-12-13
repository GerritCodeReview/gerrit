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

import com.google.gerrit.client.ui.ParentProjectNameSuggestOracle;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class ParentProjectBox extends Composite {

  private final NpTextBox textBox;
  private final SuggestBox suggestBox;
  private final ParentProjectNameSuggestOracle suggestOracle;

  public ParentProjectBox() {
    textBox = new NpTextBox();
    suggestOracle = new ParentProjectNameSuggestOracle();
    suggestBox = new SuggestBox(suggestOracle, textBox);
    initWidget(suggestBox);
  }

  public void setVisibleLength(int len) {
    textBox.setVisibleLength(len);
  }

  public void setProjectName(final Project.NameKey projectName) {
    suggestOracle.setProject(projectName);
  }

  public void setParentProjectName(final Project.NameKey projectName) {
    suggestBox.setText(projectName != null ? projectName.get() : "");
  }

  public Project.NameKey getParentProjectName() {
    final String projectName = suggestBox.getText().trim();
    if (projectName.isEmpty()) {
      return null;
    }
    return new Project.NameKey(projectName);
  }
}
