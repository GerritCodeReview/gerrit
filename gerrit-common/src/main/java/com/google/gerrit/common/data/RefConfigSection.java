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

package com.google.gerrit.common.data;

public abstract class RefConfigSection {
  /** Pattern that matches all references in a project. */
  public static final String ALL = "refs/*";

  /** Pattern that matches all branches in a project. */
  public static final String HEADS = "refs/heads/*";

  /** Prefix that triggers a regular expression pattern. */
  public static final String REGEX_PREFIX = "^";

  /** @return true if the name is likely to be a valid reference section name. */
  public static boolean isValid(String name) {
    return name.startsWith("refs/") || name.startsWith("^refs/");
  }

  protected String name;

  public RefConfigSection() {}

  public RefConfigSection(String name) {
    setName(name);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof RefConfigSection)) {
      return false;
    }
    return name.equals(((RefConfigSection) obj).name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
