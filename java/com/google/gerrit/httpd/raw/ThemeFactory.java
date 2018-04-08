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

package com.google.gerrit.httpd.raw;

import com.google.gerrit.common.data.HostPageData;
import com.google.gerrit.config.GerritServerConfig;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

class ThemeFactory {
  private final Config cfg;

  @Inject
  ThemeFactory(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
  }

  HostPageData.Theme getSignedOutTheme() {
    return getTheme("signed-out");
  }

  HostPageData.Theme getSignedInTheme() {
    return getTheme("signed-in");
  }

  private HostPageData.Theme getTheme(String name) {
    HostPageData.Theme theme = new HostPageData.Theme();
    theme.backgroundColor = color(name, "backgroundColor", "#FFFFFF");
    theme.textColor = color(name, "textColor", "#353535");
    theme.trimColor = color(name, "trimColor", "#EEEEEE");
    theme.selectionColor = color(name, "selectionColor", "#D8EDF9");
    theme.topMenuColor = color(name, "topMenuColor", "#FFFFFF");
    theme.changeTableOutdatedColor = color(name, "changeTableOutdatedColor", "#F08080");
    theme.tableOddRowColor = color(name, "tableOddRowColor", "transparent");
    theme.tableEvenRowColor = color(name, "tableEvenRowColor", "transparent");
    return theme;
  }

  private String color(String section, String name, String defaultValue) {
    String v = cfg.getString("theme", section, name);
    if (v == null || v.isEmpty()) {
      v = cfg.getString("theme", null, name);
      if (v == null || v.isEmpty()) {
        v = defaultValue;
      }
    }
    if (!v.startsWith("#") && v.matches("^[0-9a-fA-F]{2,6}$")) {
      v = "#" + v;
    }
    return v;
  }
}
