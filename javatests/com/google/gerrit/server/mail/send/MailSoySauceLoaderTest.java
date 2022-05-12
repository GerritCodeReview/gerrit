// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.template.soy.shared.SoyAstCache;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

public class MailSoySauceLoaderTest {

  private SitePaths sitePaths;
  private DynamicSet<MailSoyTemplateProvider> set;

  @Before
  public void setUp() throws Exception {
    sitePaths = new SitePaths(Paths.get("."));
    set = new DynamicSet<>();
  }

  @Test
  public void soyCompilation() {
    MailSoySauceLoader loader =
        new MailSoySauceLoader(
            sitePaths,
            new SoyAstCache(),
            new PluginSetContext<>(set, PluginMetrics.DISABLED_INSTANCE));
    assertThat(loader.load(this.getClass().getClassLoader())).isNotNull(); // should not throw
  }
}
