// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.realm.http;

import javax.inject.Inject;

import com.google.gerrit.pgm.init.Section;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.realm.RealmConfigurationInitializer;

public class HttpRealmConfigurationInitializer implements
    RealmConfigurationInitializer {

  private final ConsoleUI ui;
  private final Section auth;

  @Inject
  HttpRealmConfigurationInitializer(ConsoleUI ui, Section.Factory sectionFactory) {
    this.ui = ui;
    auth = sectionFactory.get("auth", null);
  }

  @Override
  public void init() {
    String hdr = auth.get("httpHeader");
    if (ui.yesno(hdr != null, "Get username from custom HTTP header")) {
      auth.string("Username HTTP header", "httpHeader", "SM_USER");
    } else if (hdr != null) {
      auth.unset("httpHeader");
    }
    auth.string("SSO logout URL", "logoutUrl", null);
  }

}
