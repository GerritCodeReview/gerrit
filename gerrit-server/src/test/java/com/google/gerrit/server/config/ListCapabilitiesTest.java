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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.config.ListCapabilities.CapabilityInfo;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
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
          }
        };
    injector = Guice.createInjector(mod);
  }

  @Test
  public void testList() throws Exception {
    Map<String, CapabilityInfo> m =
        injector.getInstance(ListCapabilities.class).apply(new ConfigResource());
    for (String id : GlobalCapability.getAllNames()) {
      assertTrue("contains " + id, m.containsKey(id));
      assertEquals(id, m.get(id).id);
      assertNotNull(id + " has name", m.get(id).name);
    }

    String pluginCapability = "gerrit-printHello";
    assertTrue("contains " + pluginCapability, m.containsKey(pluginCapability));
    assertEquals(pluginCapability, m.get(pluginCapability).id);
    assertEquals("Print Hello", m.get(pluginCapability).name);
  }
}
