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
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.gerrit.sshd.commands.Query;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.internal.UniqueAnnotations;
import java.util.Collections;
import org.kohsuke.args4j.Option;

public class AbstractLifecycleListenersTest extends AbstractDaemonTest {
  protected static class SimpleModule extends AbstractModule {
    @Override
    public void configure() {
      bind(com.google.gerrit.server.DynamicOptions.DynamicBean.class)
          .annotatedWith(Exports.named(Query.class))
          .to(MyClassNameProvider.class);
      bind(DynamicOptions.DynamicBean.class)
          .annotatedWith(Exports.named(QueryChanges.class))
          .to(MyClassNameProvider.class);
    }
  }

  protected static class MyClassNameProvider implements DynamicOptions.ModulesClassNamesProvider {
    @Override
    public String getClassName() {
      return "com.google.gerrit.acceptance.AbstractLifecycleListenersTest$MyOptions";
    }

    @Override
    public Iterable<String> getModulesClassNames() {
      return Collections.singleton(
          "com.google.gerrit.acceptance.AbstractLifecycleListenersTest$MyOptions$MyOptionsModule");
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
            .to(MyLifecycleListener.class);
      }
    }
  }

  protected static class MyLifecycleListener implements LifecycleListener {
    protected final InvocationCheck invocationCheck;

    @Inject
    public MyLifecycleListener(InvocationCheck invocationCheck) {
      this.invocationCheck = invocationCheck;
    }

    @Override
    public void start() {
      invocationCheck.setStartInvoked(true);
    }

    @Override
    public void stop() {
      invocationCheck.setStopInvoked(true);
    }
  }

  @Singleton
  public static class InvocationCheck {
    private boolean isStartInvoked = false;
    private boolean isStopInvoked = false;

    public boolean isStartInvoked() {
      return isStartInvoked;
    }

    public void setStartInvoked(boolean startInvoked) {
      isStartInvoked = startInvoked;
    }

    public boolean isStopInvoked() {
      return isStopInvoked;
    }

    public void setStopInvoked(boolean stopInvoked) {
      isStopInvoked = stopInvoked;
    }
  }
}
