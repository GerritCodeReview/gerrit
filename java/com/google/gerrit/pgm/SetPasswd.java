// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.pgm;

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.pgm.init.api.Section.Factory;
import com.google.inject.Inject;

public class SetPasswd {

  private ConsoleUI ui;
  private Factory sections;

  @Inject
  public SetPasswd(ConsoleUI ui, Section.Factory sections) {
    this.ui = ui;
    this.sections = sections;
  }

  public void run(String section, String key, String password) throws Exception {
    Section passwordSection = sections.get(section, null);

    if (ui.isBatch()) {
      passwordSection.setSecure(key, password);
    } else {
      ui.header("Set password for [%s]", section);
      passwordSection.passwordForKey("Enter password", key);
    }
  }
}
