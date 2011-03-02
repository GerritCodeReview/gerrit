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
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;

public class ChangeLink extends InlineHyperlink {
  public static String permalink(final Change.Id c) {
    return GWT.getHostPageBaseURL() + c.get();
  }

  protected Change.Id id;
  protected PatchSet.Id ps;
  private ChangeInfo info;

  public ChangeLink(final String text, final Change.Id c) {
    super(text, PageLinks.toChange(c));
    DOM.setElementProperty(getElement(), "href", permalink(c));
    id = c;
    ps = null;
  }

  public ChangeLink(final String text, final PatchSet.Id ps) {
    super(text, PageLinks.toChange(ps));
    id = ps.getParentKey();
    this.ps = ps;
  }

  public ChangeLink(final String text, final ChangeInfo c) {
    this(text, c.getId());
    info = c;
  }

  @Override
  public void go() {
    Gerrit.display(getTargetHistoryToken(), createScreen());
  }

  private Screen createScreen() {
    if (info != null) {
      return new ChangeScreen(info);
    } else if (ps != null) {
      return new ChangeScreen(ps);
    } else {
      return new ChangeScreen(id);
    }
  }
}
