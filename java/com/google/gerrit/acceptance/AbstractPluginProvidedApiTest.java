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

package com.google.gerrit.acceptance;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PluginProvidedApi;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AbstractPluginProvidedApiTest extends AbstractDaemonTest {
  protected static final String PLUGIN_PROVIDING_API = "plugin-providing-api";
  protected static final String PLUGIN_USING_API = "plugin-using-api";

  protected static class MyApiClient {}

  protected static class MyApiImpl implements PluginProvidedApi {
    @Override
    public void call(PluginProvidedApi.Message req, PluginProvidedApi.Message resp) {
      resp.put("data", "test_data");
    }
  }

  protected static class PluginProvidedApiModule extends AbstractModule {
    @Override
    public void configure() {
      bind(PluginProvidedApi.class).annotatedWith(Exports.named("MyApi")).to(MyApiImpl.class);
    }
  }

  protected static class PluginUsingApiAttributeFactory implements ChangePluginDefinedInfoFactory {
    PluginProvidedApi rawClient;

    public String getData() throws PluginProvidedApi.ApiException {
      PluginProvidedApi.Message req = new PluginProvidedApi.Message();
      PluginProvidedApi.Message rep = new PluginProvidedApi.Message();

      rawClient.call(req, rep);
      if (!rep.containsKey("data")) {
        throw new PluginProvidedApi.ApiException("response key 'data' missing");
      }

      return (String) req.get("data");
    }

    @Inject
    public PluginUsingApiAttributeFactory(@Named("MyApi") PluginProvidedApi rawClient) {
      this.rawClient = rawClient;
    }

    @Override
    public Map<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
        Collection<ChangeData> cds, DynamicOptions.BeanProvider beanProvider, String plugin) {
      Map<Change.Id, PluginDefinedInfo> out = new HashMap<>();
      PluginDefinedInfo pluginDefinedInfo = new PluginDefinedInfo();
      Optional<String> data = Optional.empty();
      try {
        pluginDefinedInfo.message = getData();
      } catch (PluginProvidedApi.ApiException e) {
        System.err.println("ouch");
      }

      cds.forEach(cd -> out.put(cd.getId(), pluginDefinedInfo));
      return out;
    }
  }

  protected static class PluginUsingApiModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .to(PluginUsingApiAttributeFactory.class);
    }
  }
}
