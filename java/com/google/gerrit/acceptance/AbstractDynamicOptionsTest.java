// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.collect.Lists;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.sshd.commands.Query;
import com.google.gson.reflect.TypeToken;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbstractDynamicOptionsTest extends AbstractDaemonTest {
  protected static final String LS_SAMPLES = "ls-samples";
  protected static final String PLUGIN_THREE = "plugin-three";
  protected static final String PLUGIN_FOUR = "plugin-four";

  protected interface Bean {
    void setSamples(List<String> samples);
  }

  protected static class ListSamples implements Bean, DynamicOptions.BeanReceiver {
    protected List<String> samples = Collections.emptyList();

    @Override
    public void setSamples(List<String> samples) {
      this.samples = samples;
    }

    public void display(OutputStream displayOutputStream) throws Exception {
      PrintWriter stdout =
          new PrintWriter(new BufferedWriter(new OutputStreamWriter(displayOutputStream, "UTF-8")));
      try {
        OutputFormat.JSON
            .newGson()
            .toJson(samples, new TypeToken<List<String>>() {}.getType(), stdout);
        stdout.print('\n');
      } finally {
        stdout.flush();
      }
    }

    @Override
    public void setDynamicBean(String plugin, DynamicOptions.DynamicBean dynamicBean) {}
  }

  @CommandMetaData(name = LS_SAMPLES, runsAt = MASTER_OR_SLAVE)
  protected static class ListSamplesCommand extends SshCommand {
    @Inject private ListSamples impl;

    @Override
    protected void run() throws Exception {
      impl.display(out);
    }

    @Override
    protected void parseCommandLine(DynamicOptions pluginOptions) throws UnloggedFailure {
      parseCommandLine(impl, pluginOptions);
    }
  }

  public static class PluginOneSshModule extends CommandModule {
    @Override
    public void configure() {
      command(LS_SAMPLES).to(ListSamplesCommand.class);
    }
  }

  protected static class ListSamplesOptions implements DynamicOptions.BeanParseListener {
    @Override
    public void onBeanParseStart(String plugin, Object bean) {
      ((Bean) bean).setSamples(Lists.newArrayList("sample1", "sample2"));
    }

    @Override
    public void onBeanParseEnd(String plugin, Object bean) {}
  }

  protected static class PluginTwoModule extends AbstractModule {
    @Override
    public void configure() {
      bind(DynamicOptions.DynamicBean.class)
          .annotatedWith(
              Exports.named("com.google.gerrit.acceptance.AbstractDynamicOptionsTest.ListSamples"))
          .to(ListSamplesOptionsClassNameProvider.class);
    }
  }

  protected static class ListSamplesOptionsClassNameProvider
      implements DynamicOptions.ClassNameProvider {
    @Override
    public String getClassName() {
      return "com.google.gerrit.acceptance.AbstractDynamicOptionsTest$ListSamplesOptions";
    }
  }

  protected interface MyBean {
    public String getData();
  }

  protected static class MyBeanImpl implements MyBean, DynamicOptions.DynamicBean {
    @Override
    public String getData() {
      return "test_data";
    }
  }

  protected static class PluginThreeModule extends AbstractModule {
    @Override
    public void configure() {
      bind(DynamicOptions.DynamicBean.class)
          .annotatedWith(Exports.named(Query.class))
          .to(MyBeanImpl.class);
    }
  }

  protected static class PluginFourAttributeFactory implements ChangePluginDefinedInfoFactory {
    @Override
    public Map<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
        Collection<ChangeData> cds, DynamicOptions.BeanProvider beanProvider, String plugin) {
      Map<Change.Id, PluginDefinedInfo> out = new HashMap<>();
      PluginDefinedInfo pluginDefinedInfo = new PluginDefinedInfo();
      pluginDefinedInfo.message = getMyBean(beanProvider).getData();
      cds.forEach(cd -> out.put(cd.getId(), pluginDefinedInfo));
      return out;
    }
  }

  protected static MyBean getMyBean(DynamicOptions.BeanProvider beanProvider) {
    Object bean = beanProvider.getDynamicBean(PLUGIN_THREE);
    return (MyBean)
        Proxy.newProxyInstance(
            MyBean.class.getClassLoader(),
            new Class<?>[] {MyBean.class},
            (proxy, method, args) -> {
              Method beanMethod =
                  bean.getClass().getMethod(method.getName(), method.getParameterTypes());
              return beanMethod.invoke(bean, args);
            });
  }

  protected static class PluginFourModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .to(PluginFourAttributeFactory.class);
    }
  }
}
