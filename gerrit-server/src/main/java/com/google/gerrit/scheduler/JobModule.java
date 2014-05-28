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

package com.google.gerrit.scheduler;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;

import org.quartz.Job;

/**
 * Support binding classes implementing the {@link Job} interface in Guice
 */
public abstract class JobModule extends AbstractModule {

  public static final TypeLiteral<Class<? extends Job>> ANY_JOB =
      new TypeLiteral<Class<? extends Job>>() {};

  protected void job(String name, Class<? extends Job> type) {
    bind(ANY_JOB).annotatedWith(Exports.named(name)).toInstance(type);
  }
}
