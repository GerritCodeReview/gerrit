// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;
import org.eclipse.jgit.lib.Repository;

/**
 * A reference to the shared All-Users {@link Repository}.
 *
 * <p>Inject a {@code Provider<Repository>} and use the repository obtained from {@code get()}
 * without closing it. The provider opens the underlying repository on first use and closes it at
 * shutdown.
 */
@Retention(RUNTIME)
@BindingAnnotation
public @interface AllUsersRepository {}
