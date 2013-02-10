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

package com.google.gerrit.server.project.renaming;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.FactoryModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

public class TaskModule extends FactoryModule {
  /**
   * Register a factory for a project renaming task.
   *
   * @param factory The factory to register. {@code factory} has to be declared
   *    within the class that it generates instances for. The factory's
   *    declaring class has to be an instance of {@code Task}.
   */
  @SuppressWarnings("unchecked")
  protected final void taskFactory(Class<? extends Task.Factory> factory) {
    FactoryModuleBuilder builder = new FactoryModuleBuilder();
    Class<?> declaringClass = factory.getDeclaringClass();
    if (Task.class.isAssignableFrom(declaringClass)) {
      builder.implement(Task.class, (Class<? extends Task>) declaringClass);
      install(builder.build(factory));
      DynamicSet.bind(binder(), Task.Factory.class).to(factory);
    } else {
      this.addError("Task.Factory implementation at "
          + factory.getCanonicalName() + " is not declared in a descendant "
          + "of Task");
    }
  }

  @Override
  protected void configure() {
    DynamicSet.setOf(binder(), Task.Factory.class);
    taskFactory(RenameParentsTask.Factory.class);
    taskFactory(RenameChangesTask.Factory.class);
    taskFactory(RenameWatchesTask.Factory.class);
    taskFactory(RenameRepositoryTask.Factory.class);
  }
}
