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

package com.google.gerrit.server.api.projects;

import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.config.FactoryModule;

public class Module extends FactoryModule {
  @Override
  protected void configure() {
    bind(Projects.class).to(ProjectsImpl.class);

    factory(BranchApiImpl.Factory.class);
    factory(TagApiImpl.Factory.class);
    factory(ProjectApiImpl.Factory.class);
    factory(ChildProjectApiImpl.Factory.class);
  }
}
