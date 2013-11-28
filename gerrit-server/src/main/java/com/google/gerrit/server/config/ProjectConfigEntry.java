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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.annotations.ExtensionPoint;

import java.util.List;

@ExtensionPoint
public class ProjectConfigEntry {
  private final String displayName;
  private final String defaultValue;
  private final Type type;
  private final List<String> permittedValues;

  public ProjectConfigEntry(String displayName, String defaultValue) {
    this.displayName = displayName;
    this.defaultValue = defaultValue;
    this.type = Type.STRING;
    this.permittedValues = null;
  }

  public ProjectConfigEntry(String displayName, int defaultValue) {
    this.displayName = displayName;
    this.defaultValue = Integer.toString(defaultValue);
    this.type = Type.INT;
    this.permittedValues = null;
  }

  public ProjectConfigEntry(String displayName, long defaultValue) {
    this.displayName = displayName;
    this.defaultValue = Long.toString(defaultValue);
    this.type = Type.LONG;
    this.permittedValues = null;
  }

  public ProjectConfigEntry(String displayName, boolean defaultValue) {
    this.displayName = displayName;
    this.defaultValue = Boolean.toString(defaultValue);
    this.type = Type.BOOLEAN;
    this.permittedValues = null;
  }

  public ProjectConfigEntry(String displayName, String defaultValue,
      List<String> supportedValues) {
    this.displayName = displayName;
    this.defaultValue = defaultValue;
    this.type = Type.LIST;
    this.permittedValues = supportedValues;
  }

  public <T extends Enum<?>> ProjectConfigEntry(String displayName,
      T defaultValue, Class<T> supportedValues) {
    this.displayName = displayName;
    this.defaultValue = defaultValue.name();
    this.type = Type.LIST;
    this.permittedValues =
        Lists.newArrayListWithCapacity(supportedValues.getEnumConstants().length);
    for (Enum<?> e : supportedValues.getEnumConstants()) {
      this.permittedValues.add(e.name());
    }
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

  public List<String> getPermittedValues() {
    return permittedValues;
  }

  public enum Type {
    STRING, INT, LONG, BOOLEAN, LIST
  }
}
