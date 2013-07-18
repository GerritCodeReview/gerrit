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

package com.google.gerrit.pgm;

import static com.google.common.base.Objects.firstNonNull;

import com.google.common.collect.Lists;
import com.google.gerrit.pgm.init.InstallPlugins;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePath;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;

import java.io.File;
import java.util.List;

public class SiteModule extends AbstractModule {
  private final ConsoleUI ui;
  private final File sitePath;
  private final List<String> installPlugins;

  public SiteModule(ConsoleUI ui, File sitePath, List<String> installPlugins) {
    this.ui = ui;
    this.sitePath = sitePath;
    this.installPlugins = installPlugins;
  }

  @Override
  protected void configure() {
    bind(ConsoleUI.class).toInstance(ui);
    bind(File.class).annotatedWith(SitePath.class).toInstance(sitePath);
    List<String> plugins =
        firstNonNull(installPlugins, Lists.<String> newArrayList());
    bind(new TypeLiteral<List<String>>() {})
        .annotatedWith(InstallPlugins.class).toInstance(plugins);
  }
}
