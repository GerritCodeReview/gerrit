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

package com.google.gerrit.acceptance.rest;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.restapi.project.ListProjects;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.AbstractModule;
import java.util.List;
import org.junit.Test;

public class DynamicOptionsBeanParseListenerIT extends AbstractDaemonTest {
  private static final Gson GSON = OutputFormat.JSON.newGson();

  @Test
  public void testBeanParseListener() throws Exception {
    createProjectOverAPI("project1", project, true, null);
    createProjectOverAPI("project2", project, true, null);
    try (AutoCloseable ignored = installPlugin("my-plugin", PluginModule.class)) {
      assertThat(getProjects(adminRestSession.get("/projects/"))).hasSize(1);
    }
  }

  protected List<Object> getProjects(RestResponse res) throws Exception {
    res.assertOK();
    return GSON.fromJson(res.getReader(), new TypeToken<List<Object>>() {}.getType());
  }

  protected static class ListProjectsBeanListener implements DynamicOptions.BeanParseListener {
    @Override
    public void onBeanParseStart(String plugin, Object bean) {
      ListProjects listProjects = (ListProjects) bean;
      listProjects.setLimit(1);
    }

    @Override
    public void onBeanParseEnd(String plugin, Object bean) {}
  }

  protected static class PluginModule extends AbstractModule {
    @Override
    public void configure() {
      bind(DynamicOptions.DynamicBean.class)
          .annotatedWith(Exports.named(ListProjects.class))
          .to(ListProjectsBeanListener.class);
    }
  }
}
