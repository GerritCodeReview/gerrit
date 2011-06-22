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

  protected Change.Id cid;
  protected PatchSet.Id psid;

  public ChangeLink(final String text, final Change.Id c) {
    super(text, PageLinks.toChange(c));
    DOM.setElementProperty(getElement(), "href", permalink(c));
    cid = c;
    psid = null;
  }

  public ChangeLink(final String text, final PatchSet.Id ps) {
    super(text, PageLinks.toChange(ps));
    cid = ps.getParentKey();
    psid = ps;
  }

  public ChangeLink(final String text, final ChangeInfo info) {
    super(text, getTarget(info));
    cid = info.getId();
    psid = info.getPatchSetId();
  }

  public static String getTarget(final ChangeInfo info) {
    PatchSet.Id ps = info.getPatchSetId();
    return (ps == null) ? PageLinks.toChange(info) : PageLinks.toChange(ps);
  }

  @Override
  public void go() {
    Gerrit.display(getTargetHistoryToken(), createScreen());
  }

  private Screen createScreen() {
    if (psid != null) {
      return new ChangeScreen(psid);
    } else {
      return new ChangeScreen(cid);
    }
  }
}
