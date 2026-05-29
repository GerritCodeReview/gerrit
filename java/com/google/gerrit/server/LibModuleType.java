// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server;

/** Loadable module type for the different Gerrit injectors. */
public enum LibModuleType {

  /** Module for the sysInjector. */
  SYS_MODULE_TYPE("Module"),

  /** BatchModule for the sysInjector */
  SYS_BATCH_MODULE_TYPE("BatchModule"),

  /** Module for the dbInjector. */
  DB_MODULE_TYPE("DbModule"),

  /** Module for the implementation of the indexing backend. */
  INDEX_MODULE_TYPE("IndexModule");

  private final String configKey;

  LibModuleType(String configKey) {
    this.configKey = configKey;
  }

  /**
   * Returns the module type for libModule loaded from <gerrit_site/lib> directory.
   *
   * @return module type string
   */
  public String getConfigKey() {
    return configKey;
  }
}
