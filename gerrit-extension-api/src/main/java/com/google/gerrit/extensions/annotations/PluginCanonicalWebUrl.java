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

package com.google.gerrit.extensions.annotations;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation applied to a String containing the plugin canonical web URL.
 *
 * <p>A plugin or extension may receive this string by Guice injection to discover the canonical web
 * URL under which the plugin is available:
 *
 * <pre>
 *  {@literal @Inject}
 *  MyType(@PluginCanonicalWebUrl String myUrl) {
 *  ...
 *  }
 * </pre>
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RUNTIME)
@BindingAnnotation
public @interface PluginCanonicalWebUrl {}
