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

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.PluginLogFile;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.sshd.commands.Query;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.internal.UniqueAnnotations;
import java.util.Collections;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

public class AbstractPluginLogFileTest extends AbstractDaemonTest {
  protected static class TestModule extends AbstractModule {
    @Override
    public void configure() {
      bind(com.google.gerrit.server.DynamicOptions.DynamicBean.class)
          .annotatedWith(Exports.named(Query.class))
          .to(MyClassNameProvider.class);
    }
  }

  protected static class MyClassNameProvider implements DynamicOptions.ModulesClassNamesProvider {
    @Override
    public String getClassName() {
      return "com.google.gerrit.acceptance.AbstractPluginLogFileTest$MyOptions";
    }

    @Override
    public Iterable<String> getModulesClassNames() {
      return Collections.singleton(
          "com.google.gerrit.acceptance.AbstractPluginLogFileTest$MyOptions$MyOptionsModule");
    }
  }

  public static class MyOptions implements DynamicOptions.DynamicBean {
    @Option(name = "--opt")
    public boolean opt;

    public static class MyOptionsModule extends AbstractModule {
      @Override
      protected void configure() {
        bind(LifecycleListener.class)
            .annotatedWith(UniqueAnnotations.create())
            .to(MyPluginLogFile.class);
      }
    }
  }

  protected static class MyPluginLogFile extends PluginLogFile {
    protected static final String logName = "test_log";

    @Inject
    public MyPluginLogFile(MySystemLog mySystemLog, ServerInformation serverInfo) {
      super(mySystemLog, serverInfo, logName, new PatternLayout("[%d] [%t] %m%n"));
    }
  }

  @Singleton
  protected static class MySystemLog extends SystemLog {
    protected InvocationCounter invocationCounter;

    @Inject
    public MySystemLog(SitePaths site, Config config, InvocationCounter invocationCounter) {
      super(site, config);
      this.invocationCounter = invocationCounter;
    }

    @Override
    public AsyncAppender createAsyncAppender(
        String name, Layout layout, boolean rotate, boolean forPlugin) {
      invocationCounter.increment();
      return super.createAsyncAppender(name, layout, rotate, forPlugin);
    }
  }

  @Singleton
  public static class InvocationCounter {
    private int counter = 0;

    public int getCounter() {
      return counter;
    }

    public synchronized void increment() {
      counter++;
    }
  }
}
