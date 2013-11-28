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

package com.google.gerrit.server.config;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

@ExtensionPoint
public class ProjectConfigEntry {
  public enum Type {
    STRING, INT, LONG
  }

  private final String displayName;
  private final String defaultValue;
  private final Type type;

  public ProjectConfigEntry(String displayName, String defaultValue) {
    this(displayName, defaultValue, Type.STRING);
  }

  public ProjectConfigEntry(String displayName, int defaultValue) {
    this(displayName, Integer.toString(defaultValue), Type.INT);
  }

  public ProjectConfigEntry(String displayName, long defaultValue) {
    this(displayName, Long.toString(defaultValue), Type.LONG);
  }

  private ProjectConfigEntry(String displayName, String defaultValue, Type type) {
    this.displayName = displayName;
    this.defaultValue = defaultValue;
    this.type = type;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public Type getType() {
    return type;
  }
}
