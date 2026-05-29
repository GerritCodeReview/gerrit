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

package com.google.gerrit.util.cli;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a field/setter annotated with {@literal @}Option as having a dependency on multiple other
 * command line option.
 *
 * <p>If any of the required command line options are not present, the {@literal @}Option will be
 * ignored.
 *
 * <p>For example:
 *
 * <pre>
 *   {@literal @}RequiresOptions({"--help", "--usage"})
 *   {@literal @}Option(name = "--help-as-json",
 *           usage = "display help text in json format")
 *   public boolean displayHelpAsJson;
 * </pre>
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER})
public @interface RequiresOptions {
  String[] value();
}
