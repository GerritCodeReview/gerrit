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

package com.google.gerrit.pgm.init.api;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.config.AllProjectsNameProvider;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class AllProjectsNameOnInitProvider implements Provider<String> {
  private final String name;

  @Inject
  AllProjectsNameOnInitProvider(Section.Factory sections) {
    String n = sections.get("gerrit", null).get("allProjects");
    name = MoreObjects.firstNonNull(Strings.emptyToNull(n), AllProjectsNameProvider.DEFAULT);
  }

  @Override
  public String get() {
    return name;
  }
}
