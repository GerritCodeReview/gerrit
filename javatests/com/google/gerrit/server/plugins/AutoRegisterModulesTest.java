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

package com.google.gerrit.server.plugins;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.sshd.SshCommand;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Manifest;
import org.easymock.EasyMockSupport;
import org.junit.Test;

public class AutoRegisterModulesTest {

  @Test
  public void shouldRegisterSshCommand() throws InvalidPluginException {
    EasyMockSupport ems = new EasyMockSupport();
    ModuleGenerator sshModule = ems.createNiceMock(ModuleGenerator.class);

    PluginGuiceEnvironment env = ems.createNiceMock(PluginGuiceEnvironment.class);
    expect(env.hasSshModule()).andReturn(true);
    expect(env.newSshModuleGenerator()).andReturn(sshModule);

    sshModule.setPluginName("test_plugin_name");
    expectLastCall();
    sshModule.export(anyObject(Export.class), eq(TestSshCommand.class));
    expectLastCall();

    ems.replayAll();

    PluginContentScanner scanner = new TestPluginContextScanner();
    ClassLoader classLoader = this.getClass().getClassLoader();

    AutoRegisterModules objectUnderTest =
        new AutoRegisterModules("test_plugin_name", env, scanner, classLoader);
    objectUnderTest.discover();

    ems.verifyAll();
  }

  @Export(value = "test")
  public class TestSshCommand extends SshCommand {
    @Override
    protected void run() throws UnloggedFailure, Failure, Exception {}
  }

  private class TestPluginContextScanner implements PluginContentScanner {

    @Override
    public Manifest getManifest() throws IOException {
      return null;
    }

    @Override
    public Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> scan(
        String pluginName, Iterable<Class<? extends Annotation>> annotations)
        throws InvalidPluginException {
      Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> extensions = new HashMap<>();
      extensions.put(
          Export.class,
          Lists.newArrayList(
              new ExtensionMetaData(
                  "com.google.gerrit.server.plugins.AutoRegisterModulesTest$TestSshCommand",
                  "com.google.gerrit.extensions.annotations.Export")));
      extensions.put(Listen.class, Lists.newArrayList());
      return extensions;
    }

    @Override
    public Optional<PluginEntry> getEntry(String resourcePath) throws IOException {
      return null;
    }

    @Override
    public InputStream getInputStream(PluginEntry entry) throws IOException {
      return null;
    }

    @Override
    public Enumeration<PluginEntry> entries() {
      return null;
    }
  }
}
