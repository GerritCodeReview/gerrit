// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.server.permissions.DefaultPermissionMappings.refPermission;

import com.google.gerrit.entities.Permission;
import org.junit.Test;

public class DefaultPermissionsMappingTest {
  @Test
  public void stringToRefPermission() {
    assertThat(refPermission("doesnotexist")).isEmpty();
    assertThat(refPermission("")).isEmpty();
    assertThat(refPermission(Permission.VIEW_PRIVATE_CHANGES))
        .hasValue(RefPermission.READ_PRIVATE_CHANGES);
  }
}
