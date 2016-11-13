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

package com.google.gerrit.acceptance;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;

class ConfigAnnotationParser {
  private static Splitter splitter = Splitter.on(".").trimResults();

  static Config parse(Config base, GerritConfigs annotation) {
    if (annotation == null) {
      return null;
    }

    Config cfg = new Config(base);
    for (GerritConfig c : annotation.value()) {
      parseAnnotation(cfg, c);
    }
    return cfg;
  }

  static Config parse(Config base, GerritConfig annotation) {
    Config cfg = new Config(base);
    parseAnnotation(cfg, annotation);
    return cfg;
  }

  private static void parseAnnotation(Config cfg, GerritConfig c) {
    ArrayList<String> l = Lists.newArrayList(splitter.split(c.name()));
    if (l.size() == 2) {
      if (!Strings.isNullOrEmpty(c.value())) {
        cfg.setString(l.get(0), null, l.get(1), c.value());
      } else {
        String[] values = c.values();
        cfg.setStringList(l.get(0), null, l.get(1), Arrays.asList(values));
      }
    } else if (l.size() == 3) {
      if (!Strings.isNullOrEmpty(c.value())) {
        cfg.setString(l.get(0), l.get(1), l.get(2), c.value());
      } else {
        cfg.setStringList(l.get(0), l.get(1), l.get(2), Arrays.asList(c.value()));
      }
    } else {
      throw new IllegalArgumentException(
          "GerritConfig.name must be of the format" + " section.subsection.name or section.name");
    }
  }
}
