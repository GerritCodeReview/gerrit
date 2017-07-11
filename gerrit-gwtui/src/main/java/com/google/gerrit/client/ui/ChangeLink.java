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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;

public class ChangeLink extends InlineHyperlink {
  public static String permalink(Change.Id c) {
    return GWT.getHostPageBaseURL() + c.get();
  }

  protected Change.Id cid;

  public ChangeLink(Project.NameKey project, Change.Id c, String text) {
    super(text, PageLinks.toChange(project, c));
    getElement().setPropertyString("href", permalink(c));
    cid = c;
  }

  @Override
  public void go() {
    Gerrit.display(getTargetHistoryToken());
  }
}
