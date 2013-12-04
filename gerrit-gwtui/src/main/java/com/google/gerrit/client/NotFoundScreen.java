// Copyright (C) 2008 The Android Open Source Project
// Copyright (C) 2014 Digia Plc and/or its subsidiary(-ies).
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

package com.google.gerrit.client;

import com.google.gerrit.client.ui.Screen;
import com.google.gwt.user.client.ui.Label;

/** Displays an error message letting the user know the page doesn't exist. */
public class NotFoundScreen extends Screen {
  String text;

  public NotFoundScreen() {
  }

  public NotFoundScreen(final String text) {
    this.text = text;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Gerrit.C.notFoundTitle());
    add(new Label(Gerrit.C.notFoundBody()));
    if (text != null) {
      add(new Label(text));
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    display();
  }
}
