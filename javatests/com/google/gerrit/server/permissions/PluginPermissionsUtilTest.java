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

package com.google.gerrit.server.permissions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.permissions.PluginPermissionsUtil.isPluginPermission;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

/** Small tests for {@link PluginPermissionsUtil}. */
public final class PluginPermissionsUtilTest {

  @Test
  public void isPluginPermission_validName_returnTrue() {
    // "-" is allowed for a plugin name. Here "foo-a" should be the name of the plugin.
    ImmutableList<String> validPluginPermissions =
        ImmutableList.of("plugin-foo-a", "plugin-foo-a-b");

    for (String permission : validPluginPermissions) {
      boolean result = isPluginPermission(permission);

      assertThat(result).named("valid plugin permission: %s", permission).isTrue();
      ;
    }
  }

  @Test
  public void isPluginPermission_invalidName_returnFalse() {
    ImmutableList<String> validPluginPermissions =
        ImmutableList.of(
            "create",
            "label-Code-Review",
            "plugin-foo",
            "plugin-foo",
            "plugin-foo-a-",
            "plugin-foo-a1");

    for (String permission : validPluginPermissions) {
      boolean result = isPluginPermission(permission);

      assertThat(result).named("invalid plugin permission: %s", permission).isFalse();
    }
  }
}
