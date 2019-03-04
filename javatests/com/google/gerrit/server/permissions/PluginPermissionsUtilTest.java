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
import static com.google.gerrit.server.permissions.PluginPermissionsUtil.isValidPluginPermission;

import org.junit.Test;

/** Small tests for {@link PluginPermissionsUtil}. */
public class PluginPermissionsUtilTest {

  @Test
  public void pluginPermissionNameInConfigPattern() {
    assertThat(isValidPluginPermission("create")).isFalse();
    assertThat(isValidPluginPermission("label-Code-Review")).isFalse();
    assertThat(isValidPluginPermission("plugin-foo")).isFalse();
    assertThat(isValidPluginPermission("plugin-foo")).isFalse();

    assertThat(isValidPluginPermission("plugin-foo-a")).isTrue();
    // "-" is allowed for a plugin name. Here "foo-a" should be the name of the plugin.
    assertThat(isValidPluginPermission("plugin-foo-a-b")).isTrue();

    assertThat(isValidPluginPermission("plugin-foo-a-")).isFalse();
    assertThat(isValidPluginPermission("plugin-foo-a1")).isFalse();
  }
}
