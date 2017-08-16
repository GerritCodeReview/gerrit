// Copyright (C) 2012 The Android Open Source Project
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
import java.lang.annotation.Retention;

/**
 * Local path where a plugin can store its own private data.
 *
 * <p>A plugin or extension may receive this string by Guice injection to discover a directory where
 * it can store configuration or other data that is private:
 *
 * <p>This binding is on both {@link java.io.File} and {@link java.nio.file.Path}, pointing to the
 * same location. The {@code File} version should be considered deprecated and may be removed in a
 * future version.
 *
 * <pre>
 * {@literal @Inject}
 * MyType(@PluginData java.nio.file.Path myDir) {
 *   this.in = Files.newInputStream(myDir.resolve(&quot;my.config&quot;));
 * }
 * </pre>
 */
@Retention(RUNTIME)
@BindingAnnotation
public @interface PluginData {}
