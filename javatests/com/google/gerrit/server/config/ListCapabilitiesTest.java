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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.restapi.config.ListCapabilities;
import com.google.gerrit.server.restapi.config.ListCapabilities.CapabilityInfo;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class ListCapabilitiesTest {
  private Injector injector;

  @Before
  public void setUp() throws Exception {
    AbstractModule mod =
        new AbstractModule() {
          @Override
          protected void configure() {
            DynamicMap.mapOf(binder(), CapabilityDefinition.class);
            bind(CapabilityDefinition.class)
                .annotatedWith(Exports.named("printHello"))
                .toInstance(
                    new CapabilityDefinition() {
                      @Override
                      public String getDescription() {
                        return "Print Hello";
                      }
                    });
            bind(PermissionBackend.class).to(FakePermissionBackend.class);
          }
        };
    injector = Guice.createInjector(mod);
  }

  @Test
  public void list() throws Exception {
    Map<String, CapabilityInfo> m =
        injector.getInstance(ListCapabilities.class).apply(new ConfigResource());
    for (String id : GlobalCapability.getAllNames()) {
      assertThat(m).containsKey(id);
      assertThat(m.get(id).id).isEqualTo(id);
      assertThat(m.get(id).name).isNotNull();
    }

    String pluginCapability = "gerrit-printHello";
    assertThat(m).containsKey(pluginCapability);
    assertThat(m.get(pluginCapability).id).isEqualTo(pluginCapability);
    assertThat(m.get(pluginCapability).name).isEqualTo("Print Hello");
  }

  @Singleton
  private static class FakePermissionBackend extends PermissionBackend {
    @Override
    public WithUser currentUser() {
      throw new UnsupportedOperationException();
    }

    @Override
    public WithUser user(CurrentUser user) {
      throw new UnsupportedOperationException();
    }

    @Override
    public WithUser absentUser(Id user) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean usesDefaultCapabilities() {
      return true;
    }
  }
}
