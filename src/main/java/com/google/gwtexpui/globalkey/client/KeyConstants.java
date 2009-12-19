// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gwtexpui.globalkey.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.Constants;

public interface KeyConstants extends Constants {
  public static final KeyConstants I = GWT.create(KeyConstants.class);

  String applicationSection();
  String showHelp();
  String closeCurrentDialog();

  String keyboardShortcuts();
  String closeButton();
  String orOtherKey();
  String thenOtherKey();

  String keyCtrl();
  String keyAlt();
  String keyMeta();
  String keyEnter();
  String keyEsc();
}
