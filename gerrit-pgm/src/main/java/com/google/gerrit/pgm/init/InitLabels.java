// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.Arrays;

@Singleton
public class InitLabels implements InitStep {
  private static final String KEY_LABEL = "label";
  private static final String KEY_FUNCTION = "function";
  private static final String KEY_VALUE = "value";
  private static final String LABEL_VERIFIED = "Verified";

  private final ConsoleUI ui;
  private final AllProjectsConfig allProjectsConfig;

  @Inject
  InitLabels(ConsoleUI ui, AllProjectsConfig allProjectsConfig) {
    this.ui = ui;
    this.allProjectsConfig = allProjectsConfig;
  }

  @Override
  public void run() throws Exception {
    ui.header("Review Labels");
    boolean enabled = ui.yesno(false, "Install Verified label");
    Config cfg = allProjectsConfig.load();
    if (enabled) {
      cfg.setString(KEY_LABEL, LABEL_VERIFIED, KEY_FUNCTION, "MaxWithBlock");
      cfg.setStringList(KEY_LABEL, LABEL_VERIFIED, KEY_VALUE,
          Arrays.asList(new String[] {"-1 Fails", " 0 No score", "+1 Verified"}));
      allProjectsConfig.save("Review Label Initialization");
    }
  }
}
