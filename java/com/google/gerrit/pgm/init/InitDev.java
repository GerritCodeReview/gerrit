// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class InitDev implements InitStep {
  private final InitFlags flags;
  private final Section plugins;

  @Inject
  InitDev(InitFlags flags, Section.Factory sections) {
    this.flags = flags;
    this.plugins = sections.get("plugins", null);
  }

  @Override
  public void run() throws Exception {
    if (!flags.dev) {
      return;
    }
    plugins.set("allowRemoteAdmin", "true");
  }
}
