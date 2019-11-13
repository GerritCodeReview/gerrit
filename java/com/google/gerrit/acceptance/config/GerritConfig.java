// Copyright (C) 2013 The Android Open Source Project
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
@Repeatable(GerritConfigs.class)
public @interface GerritConfig {
  /**
   * Setting name in the form {@code "section.name"} or {@code "section.subsection.name"} where
   * {@code section}, {@code subsection} and {@code name} correspond to the parameters of the same
   * names in JGit's {@code Config#getString} method.
   *
   * @see org.eclipse.jgit.lib.Config#getString(String, String, String)
   */
  String name();

  /** Single value. Takes precedence over values specified in {@code values}. */
  String value() default "";

  /** Multiple values (list). Ignored if {@code value} is specified. */
  String[] values() default "";
}
