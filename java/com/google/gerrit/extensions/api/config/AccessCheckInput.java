// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.api.config;

import com.google.gerrit.common.Nullable;

public class AccessCheckInput {
  public String account;
  public String project;

  @Nullable public String ref;

  public AccessCheckInput(String account, String project, @Nullable String ref) {
    this.account = account;
    this.project = project;
    this.ref = ref;
  }

  public AccessCheckInput() {}
}
