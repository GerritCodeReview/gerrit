// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.util.git.DelegateSystemReader;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Test;

public class GerritInstanceNameProviderTest {

  @Test
  public void instanceNameSet_canonicalWebUrlUnset_useInstanceName() {
    Config cfg = new Config();
    cfg.setString("gerrit", null, "instanceName", "myName");

    GerritInstanceNameProvider provider = new GerritInstanceNameProvider(cfg, null);
    assertThat(provider.get()).isEqualTo("myName");
  }

  @Test
  public void instanceNameSet_canonicalWebUrlSet_useInstanceName() {
    Config cfg = new Config();
    cfg.setString("gerrit", null, "instanceName", "myName");

    GerritInstanceNameProvider provider = new GerritInstanceNameProvider(cfg, "http://myhost");
    assertThat(provider.get()).isEqualTo("myName");
  }

  @Test
  public void instanceNameNotSet_canonicalWebUrlSet_useHostFromCanonicalWebUrl() {
    Config cfg = new Config();
    GerritInstanceNameProvider provider = new GerritInstanceNameProvider(cfg, "http://myhost");
    assertThat(provider.get()).isEqualTo("myhost");
  }

  @Test
  public void instanceNameNotSet_canonicalWebUrlNotSet_useSystemHostName() {
    SystemReader oldSystemReader = SystemReader.getInstance();
    SystemReader.setInstance(
        new DelegateSystemReader(oldSystemReader) {
          @Override
          public String getHostname() {
            return "systemHostName";
          }
        });
    Config cfg = new Config();
    GerritInstanceNameProvider provider = new GerritInstanceNameProvider(cfg, null);
    assertThat(provider.get()).isEqualTo("systemHostName");
  }
}
