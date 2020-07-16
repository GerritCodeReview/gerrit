// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.PermissionRule;
import java.util.List;

@AutoValue
public abstract class AccountsSection {
  public abstract ImmutableList<PermissionRule> getSameGroupVisibility();

  public static AccountsSection create(List<PermissionRule> sameGroupVisibility) {
    return new AutoValue_AccountsSection(ImmutableList.copyOf(sameGroupVisibility));
  }
}
