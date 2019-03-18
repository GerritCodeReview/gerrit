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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.server.DynamicOptions.BeanProvider;
import com.google.gerrit.server.query.change.ChangeData;

/**
 * Interface for plugins to provide additional fields in {@link
 * com.google.gerrit.extensions.common.ChangeInfo ChangeInfo}.
 *
 * <p>Register a {@code ChangeAttributeFactory} in a plugin {@code Module} like this:
 *
 * <pre>
 * DynamicSet.bind(binder(), ChangeAttributeFactory.class).to(YourClass.class);
 * </pre>
 *
 * <p>See the <a
 * href="https://gerrit-review.googlesource.com/Documentation/dev-plugins.html#query_attributes">plugin
 * developer documentation for more details and examples.
 */
public interface ChangeAttributeFactory {

  /**
   * Create a plugin-provided info field.
   *
   * <p>Typically, implementations will subclass {@code PluginDefinedInfo} to add additional fields.
   *
   * @param cd change.
   * @param beanProvider provider of {@code DynamicBean}s, which may be used for reading options.
   * @param plugin plugin name.
   * @return the plugin's special change info.
   */
  PluginDefinedInfo create(ChangeData cd, BeanProvider beanProvider, String plugin);
}
