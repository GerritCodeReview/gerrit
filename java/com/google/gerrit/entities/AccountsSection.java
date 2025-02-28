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

package com.google.gerrit.entities;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.InlineMe;
import java.util.List;

public record AccountsSection(ImmutableList<PermissionRule> sameGroupVisibility) {
  public AccountsSection {
    requireNonNull(sameGroupVisibility, "sameGroupVisibility");
  }

  @InlineMe(replacement = "this.sameGroupVisibility()")
  public ImmutableList<PermissionRule> getSameGroupVisibility() {
    return sameGroupVisibility();
  }

  public static AccountsSection create(List<PermissionRule> sameGroupVisibility) {
    return new AccountsSection(ImmutableList.copyOf(sameGroupVisibility));
  }
}
