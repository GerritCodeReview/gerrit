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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.DynamicOptions.BeanProvider;
import com.google.gerrit.server.query.change.ChangeData;
import java.util.Objects;
import java.util.stream.Stream;

/** Static helpers for use by {@link PluginDefinedAttributesFactory} implementations. */
public class PluginDefinedAttributesFactories {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Nullable
  public static ImmutableList<PluginDefinedInfo> createAll(
      ChangeData cd,
      BeanProvider beanProvider,
      Stream<Extension<ChangeAttributeFactory>> attrFactories) {
    ImmutableList<PluginDefinedInfo> result =
        attrFactories
            .map(e -> tryCreate(cd, beanProvider, e.getPluginName(), e.get()))
            .filter(Objects::nonNull)
            .collect(toImmutableList());
    return !result.isEmpty() ? result : null;
  }

  @Nullable
  private static PluginDefinedInfo tryCreate(
      ChangeData cd, BeanProvider beanProvider, String plugin, ChangeAttributeFactory attrFactory) {
    PluginDefinedInfo pdi = null;
    try {
      pdi = attrFactory.create(cd, beanProvider, plugin);
    } catch (RuntimeException ex) {
      logger.atWarning().atMostEvery(1, MINUTES).withCause(ex).log(
          "error populating attribute on change %s from plugin %s", cd.getId(), plugin);
    }
    if (pdi != null) {
      pdi.name = plugin;
    }
    return pdi;
  }

  private PluginDefinedAttributesFactories() {}
}
