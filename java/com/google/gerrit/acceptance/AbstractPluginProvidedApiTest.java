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
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PluginProvidedApi;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AbstractPluginProvidedApiTest extends AbstractDaemonTest {
  protected static final String PLUGIN_ONE = "plugin-one";
  protected static final String PLUGIN_TWO = "plugin-two";

  protected interface MyApi extends PluginProvidedApi {
    public String getData();
  }

  protected static class MyApiImpl implements MyApi {
    @Override
    public String getData() {
      return "test_data";
    }
  }

  protected static class PluginOneModule extends AbstractModule {
    @Override
    public void configure() {
      bind(PluginProvidedApi.class).annotatedWith(Exports.named("MyApi")).to(MyApiImpl.class);
    }
  }

  protected static class PluginTwoAttributeFactory implements ChangePluginDefinedInfoFactory {
    protected DynamicMap<PluginProvidedApi> pluginProvidedApi;

    @Inject
    public PluginTwoAttributeFactory(DynamicMap<PluginProvidedApi> pluginProvidedApi) {
      this.pluginProvidedApi = pluginProvidedApi;
    }

    @Override
    public Map<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
        Collection<ChangeData> cds, DynamicOptions.BeanProvider beanProvider, String plugin) {
      Map<Change.Id, PluginDefinedInfo> out = new HashMap<>();
      PluginDefinedInfo pluginDefinedInfo = new PluginDefinedInfo();
      pluginDefinedInfo.message = getMyApi(pluginProvidedApi.get(PLUGIN_ONE, "MyApi")).getData();
      cds.forEach(cd -> out.put(cd.getId(), pluginDefinedInfo));
      return out;
    }

    protected static MyApi getMyApi(PluginProvidedApi pluginProvidedApi) {
      return (MyApi)
          Proxy.newProxyInstance(
              MyApi.class.getClassLoader(),
              new Class<?>[] {MyApi.class},
              (proxy, method, args) -> {
                Method apiMethod =
                    pluginProvidedApi
                        .getClass()
                        .getMethod(method.getName(), method.getParameterTypes());
                return apiMethod.invoke(pluginProvidedApi, args);
              });
    }
  }

  protected static class PluginTwoModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .to(PluginTwoAttributeFactory.class);
    }
  }
}
