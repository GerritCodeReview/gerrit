// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.testing;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Exclude the method or class from the check of the Git Repository object counting and leak check
 * upon testing.
 *
 * <p>This option should be used only when a test or suite is well-known to generate Git Repository
 * object leaks and that is currently either acceptable or under review for later fix.
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface NoGitRepositoryCheckIfClosed {}
