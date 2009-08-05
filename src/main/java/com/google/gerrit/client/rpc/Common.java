// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.rpc;

import com.google.gerrit.client.data.AccountCache;
import com.google.gerrit.client.data.GerritConfig;

public class Common {
  private static GerritConfig config;
  private static AccountCache accountCache;

  /** Get the public configuration data used by this Gerrit instance. */
  public static GerritConfig getGerritConfig() {
    return config;
  }

  public static void setGerritConfig(final GerritConfig imp) {
    config = imp;
  }

  /**
   * Get the active AccountCache instance.
   * <p>
   * <b>Note: this is likely only available on the server side.</b>
   */
  public static AccountCache getAccountCache() {
    return accountCache;
  }

  public static void setAccountCache(final AccountCache imp) {
    accountCache = imp;
  }
}
