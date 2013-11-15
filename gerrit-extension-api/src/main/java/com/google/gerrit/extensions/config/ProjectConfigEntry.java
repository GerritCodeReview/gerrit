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

package com.google.gerrit.extensions.config;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

import java.util.ArrayList;
import java.util.List;

@ExtensionPoint
public class ProjectConfigEntry {
  private final String displayName;
  private final boolean inheritable;
  private final String defaultValue;
  private final Type type;
  private final List<String> supportedValues;

  public ProjectConfigEntry(String displayName, boolean inheritable,
      String defaultValue) {
    this.displayName = displayName;
    this.inheritable = inheritable;
    this.defaultValue = defaultValue;
    this.type = Type.STRING;
    this.supportedValues = null;
  }

  public ProjectConfigEntry(String displayName, boolean inheritable,
      int defaultValue) {
    this.displayName = displayName;
    this.inheritable = inheritable;
    this.defaultValue = Integer.toString(defaultValue);
    this.type = Type.INT;
    this.supportedValues = null;
  }

  public ProjectConfigEntry(String displayName, boolean inheritable,
      long defaultValue) {
    this.displayName = displayName;
    this.inheritable = inheritable;
    this.defaultValue = Long.toString(defaultValue);
    this.type = Type.LONG;
    this.supportedValues = null;
  }

  public ProjectConfigEntry(String displayName, boolean defaultValue) {
    this.displayName = displayName;
    this.inheritable = false;
    this.defaultValue = Boolean.toString(defaultValue);
    this.type = Type.BOOLEAN;
    this.supportedValues = null;
  }

  public ProjectConfigEntry(String displayName, boolean inheritable,
      String defaultValue, List<String> supportedValues) {
    this.displayName = displayName;
    this.inheritable = inheritable;
    this.defaultValue = defaultValue;
    this.type = Type.LIST;
    this.supportedValues = supportedValues;
  }

  public <T extends Enum<?>> ProjectConfigEntry(String displayName,
      boolean inheritable, T defaultValue, Class<T> supportedValues) {
    this.displayName = displayName;
    this.inheritable = inheritable;
    this.defaultValue = defaultValue.name();
    this.type = Type.LIST;
    this.supportedValues = new ArrayList<String>();
    for (Enum<?> e : supportedValues.getEnumConstants()) {
      this.supportedValues.add(e.name());
    }
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isInheritable() {
    return inheritable;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public Type getType() {
    return type;
  }

  public List<String> getSupportedValues() {
    return supportedValues;
  }

  public enum Type {
    STRING, INT, LONG, BOOLEAN, LIST
  }
}
