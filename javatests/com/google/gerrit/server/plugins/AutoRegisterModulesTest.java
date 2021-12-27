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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.sshd.SshCommand;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.Test;

public class AutoRegisterModulesTest {

  @Test
  public void shouldRegisterSshCommand() throws InvalidPluginException {
    ModuleGenerator sshModule = mock(ModuleGenerator.class);
    PluginGuiceEnvironment env = mock(PluginGuiceEnvironment.class);

    when(env.hasSshModule()).thenReturn(true);
    when(env.newSshModuleGenerator()).thenReturn(sshModule);

    PluginContentScanner scanner = new TestPluginContextScanner();
    ClassLoader classLoader = this.getClass().getClassLoader();

    AutoRegisterModules objectUnderTest =
        new AutoRegisterModules("test_plugin_name", env, scanner, classLoader);
    objectUnderTest.discover();

    verify(sshModule).setPluginName("test_plugin_name");
    verify(sshModule).export(any(Export.class), eq(TestSshCommand.class));
  }

  @Export(value = "test")
  public static class TestSshCommand extends SshCommand {
    @Override
    protected void run() throws UnloggedFailure, Failure, Exception {}
  }

  private static class TestPluginContextScanner implements PluginContentScanner {

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
    public Stream<PluginEntry> entries() {
      return null;
    }
  }
}
