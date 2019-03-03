package com.google.gerrit.server.permissions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.permissions.PluginPermissionsUtil.isPluginPermission;

import org.junit.Test;

/** Small tests for {@link PluginPermissionsUtil}. */
public class PluginPermissionsUtilTest {

  @Test
  public void pluginPermissionNameInConfigPattern() {
    assertThat(isPluginPermission("create")).isFalse();
    assertThat(isPluginPermission("label-Code-Review")).isFalse();
    assertThat(isPluginPermission("plugin-foo")).isFalse();
    assertThat(isPluginPermission("plugin-foo")).isFalse();

    assertThat(isPluginPermission("plugin-foo-a")).isTrue();
    // "-" is allowed for a plugin name. Here "foo-a" should be the name of hte plugin.
    assertThat(isPluginPermission("plugin-foo-a-b")).isTrue();

    assertThat(isPluginPermission("plugin-foo-a-")).isFalse();
    assertThat(isPluginPermission("plugin-foo-a1")).isFalse();
  }
}
