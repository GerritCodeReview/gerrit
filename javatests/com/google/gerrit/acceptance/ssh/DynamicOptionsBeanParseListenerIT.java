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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.sshd.commands.ListProjectsCommand;
import com.google.inject.AbstractModule;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

@NoHttpd
@UseSsh
public class DynamicOptionsBeanParseListenerIT extends AbstractDaemonTest {

  @Test
  public void testBeanParseListener() throws Exception {
    createProjectOverAPI("project1", project, true, null);
    createProjectOverAPI("project2", project, true, null);
    try (AutoCloseable ignored = installPlugin("my-plugin", PluginModule.class)) {
      String output = adminSshSession.exec("gerrit ls-projects");
      adminSshSession.assertSuccess();
      assertThat(getProjects(output)).hasSize(1);
    }
  }

  protected List<String> getProjects(String sshOutput) {
    return Arrays.asList(sshOutput.split("\n"));
  }

  protected static class ListProjectsCommandBeanListener
      implements DynamicOptions.BeanParseListener {
    @Override
    public void onBeanParseStart(String plugin, Object bean) {
      ListProjectsCommand command = (ListProjectsCommand) bean;
      command.impl.setLimit(1);
    }

    @Override
    public void onBeanParseEnd(String plugin, Object bean) {}
  }

  protected static class PluginModule extends AbstractModule {
    @Override
    public void configure() {
      bind(DynamicOptions.DynamicBean.class)
          .annotatedWith(Exports.named(ListProjectsCommand.class))
          .to(ListProjectsCommandBeanListener.class);
    }
  }
}
