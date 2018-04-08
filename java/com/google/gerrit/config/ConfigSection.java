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

package com.google.gerrit.config;

import org.eclipse.jgit.lib.Config;

/** Provides access to one section from {@link Config} */
public class ConfigSection {

  private final Config cfg;
  private final String section;

  public ConfigSection(Config cfg, String section) {
    this.cfg = cfg;
    this.section = section;
  }

  public String optional(String name) {
    return cfg.getString(section, null, name);
  }

  public String required(String name) {
    return ConfigUtil.getRequired(cfg, section, name);
  }
}
