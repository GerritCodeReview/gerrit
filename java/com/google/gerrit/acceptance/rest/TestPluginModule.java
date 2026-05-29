// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest;

import static com.google.gerrit.acceptance.rest.PluginResource.PLUGIN_KIND;
import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.inject.AbstractModule;

public class TestPluginModule extends AbstractModule {

  public static final String PLUGIN_CAPABILITY = "printHello";
  public static final String PLUGIN_COLLECTION = "foo";

  @Override
  protected void configure() {
    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(PLUGIN_CAPABILITY))
        .toInstance(
            new CapabilityDefinition() {
              @Override
              public String getDescription() {
                return "Print Hello";
              }
            });

    install(
        new RestApiModule() {
          @Override
          protected void configure() {
            DynamicMap.mapOf(binder(), PLUGIN_KIND);
            child(CONFIG_KIND, PLUGIN_COLLECTION).to(PluginCollection.class);
            get(PLUGIN_KIND).to(GetTestPlugin.class);
            create(PLUGIN_KIND).to(CreateTestPlugin.class);
          }
        });
  }
}
