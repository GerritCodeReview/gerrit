// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.vhost;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;

/**
 * Marker on {@code org.eclipse.jgit.lib.Config} holding the JVM wide config.
 * <p>
 * In virtual hosted implementation of Gerrit Code Review, the global config is
 * distinguished from the server-specific configurations:
 * <ul>
 * <li>{@code @GerritGlobalConfig Config}: single JVM wide configuration shared
 * by all virtual hosted sites. The JVM owner (or virtual host administrator)
 * can use this to set default policy.
 * <li>{@code @GerritServerConfig Config}: per-server configuration. Multiple
 * configurations are active at once within the same JVM.
 * </ul>
 */
@Retention(RUNTIME)
@BindingAnnotation
public @interface GerritGlobalConfig {
}
