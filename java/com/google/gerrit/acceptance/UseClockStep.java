// Copyright (C) 2019 The Android Open Source Project
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
import java.util.concurrent.TimeUnit;

/**
 * Annotation to use a clock step for the execution of acceptance tests (the test class must inherit
 * from {@link AbstractDaemonTest}).
 *
 * <p>Annotations on method level override annotations on class level.
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface UseClockStep {
  /** Amount to increment clock by on each lookup. */
  long clockStep() default 1L;

  /** Time unit for {@link #clockStep()}. */
  TimeUnit clockStepUnit() default TimeUnit.SECONDS;

  /** Whether the clock should initially be set to {@link java.time.Instant#EPOCH}. */
  boolean startAtEpoch() default false;
}
