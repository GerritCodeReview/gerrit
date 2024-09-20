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

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

public class GerritOptions {
  private final boolean headless;
  private final boolean replica;
  private final Optional<String> devCdn;

  public static GerritOptions DEFAULT = new GerritOptions(false, false);

  public GerritOptions(boolean headless, boolean replica) {
    this(headless, replica, null);
  }

  public GerritOptions(boolean headless, boolean replica, @Nullable String devCdn) {
    this.headless = headless;
    this.replica = replica;
    this.devCdn = headless ? Optional.empty() : Optional.ofNullable(Strings.emptyToNull(devCdn));
  }

  public boolean headless() {
    return headless;
  }

  public boolean replica() {
    return replica;
  }

  public boolean enableMasterFeatures() {
    return !replica;
  }

  public Optional<String> devCdn() {
    return devCdn;
  }
}
