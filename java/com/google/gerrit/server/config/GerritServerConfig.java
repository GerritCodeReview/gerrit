// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;

/**
 * Marker on {@link org.eclipse.jgit.lib.Config} holding {@code gerrit.config} .
 *
 * <p>The {@code gerrit.config} file contains almost all site-wide configuration settings for the
 * Gerrit Code Review server.
 */
@Retention(RUNTIME)
@BindingAnnotation
public @interface GerritServerConfig {}
