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

package com.google.gerrit.acceptance.config;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({METHOD})
@Retention(RUNTIME)
@Repeatable(GlobalPluginConfigs.class)
public @interface GlobalPluginConfig {
  /** Name of the plugin, corresponding to {@code $site/etc/@pluginName.config}. */
  String pluginName();

  /** @see GerritConfig#name() */
  String name();

  /** @see GerritConfig#value() */
  String value() default "";

  /** @see GerritConfig#values() */
  String[] values() default "";
}
