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

package com.google.gerrit.acceptance;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to mark tests that require a local disk for the execution.
 *
 * <p>Tests that do not have this annotation are executed in memory.
 *
 * <p>Using this annotation makes the execution of the test more expensive/slower. This is why it
 * should only be used if the test requires a local disk (e.g. if the test triggers the Git garbage
 * collection functionality which only works with a local disk).
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface UseLocalDisk {}
