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

import com.google.gerrit.client.Link;
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;

public class ChangeLink extends DirectScreenLink {
  public static String permalink(final Change.Id c) {
    return GWT.getModuleBaseURL() + c.get();
  }

  protected Change.Id id;
  private ChangeInfo info;

  public ChangeLink(final String text, final Change.Id c) {
    super(text, Link.toChange(c));
    final String href = permalink(c);
    DOM.setElementProperty(DOM.getFirstChild(getElement()), "href", href);
    id = c;
  }

  public ChangeLink(final String text, final ChangeInfo c) {
    this(text, c.getId());
    info = c;
  }

  @Override
  protected Screen createScreen() {
    return info != null ? new ChangeScreen(info) : new ChangeScreen(id);
  }
}
