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

package com.google.gerrit.common;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A marker to say a method/type/field/constructor is added or is increased to public solely because
 * it is called from inside a project or an organisation using Gerrit.
 */
@Target({METHOD, TYPE, FIELD, CONSTRUCTOR})
@Retention(RUNTIME)
@Repeatable(UsedAt.Uses.class)
public @interface UsedAt {
  /** Enumeration of projects that call a method/type/field. */
  enum Project {
    COLLABNET,
    GOOGLE,
    PLUGINS_ALL, // Use this project if a method/type is generally made available to all plugins.
    PLUGIN_CHECKS,
    PLUGIN_CODE_OWNERS,
    PLUGIN_DELETE_PROJECT,
    PLUGIN_HIGH_AVAILABILITY,
    PLUGIN_MULTI_SITE,
    PLUGIN_SERVICEUSER,
    PLUGIN_PULL_REPLICATION,
    PLUGIN_WEBSESSION_FLATFILE,
    MODULE_GIT_REFS_FILTER
  }

  /** Reference to the project that uses the method annotated with this annotation. */
  Project value();

  /** Allows to mark method/type/field with multiple UsedAt annotations. */
  @Retention(RUNTIME)
  @Target(ElementType.TYPE)
  @interface Uses {
    UsedAt[] value();
  }
}
