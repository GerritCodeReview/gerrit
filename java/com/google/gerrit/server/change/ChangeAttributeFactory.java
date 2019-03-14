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
import com.google.gerrit.server.query.change.ChangeQueryProcessor;

/**
 * Register a ChangeAttributeFactory in a config Module like this:
 *
 * <p>bind(ChangeAttributeFactory.class) .annotatedWith(Exports.named("export-name"))
 * .to(YourClass.class);
 */
public interface ChangeAttributeFactory {
  PluginDefinedInfo create(ChangeData cd, BeanProvider beanProvider, String plugin);
}
