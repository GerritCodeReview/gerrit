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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.ModuleImpl;
import com.google.gerrit.server.audit.AuditModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.junit.Test;

public class DaemonOverridesTestLibModulesIT extends AbstractDaemonTest {
  private static final String TEST_MODULE = "test-module";

  @Inject
  @Named(value = TEST_MODULE)
  private String testModuleClassName;

  public abstract static class TestModule extends AuditModule {
    @Override
    protected void configure() {
      super.configure();
      bind(String.class).annotatedWith(Names.named(TEST_MODULE)).toInstance(getClass().getName());
    }
  }

  @ModuleImpl(name = TEST_MODULE)
  public static class DefaultModule extends TestModule {}

  @ModuleImpl(name = TEST_MODULE)
  public static class OverriddenModule extends TestModule {}
//
//  @Override
//  public Module createAuditModule() {
//    return new DefaultModule();
//  }
//
//  @Override
//  public Module createModule() {
//    return new OverriddenModule();
//  }

  @Test
  public void testSysModuleShouldOverrideTheDefaultOneWithSameModuleAnnotation() {
    assertThat(testModuleClassName).isEqualTo(OverriddenModule.class.getName());
  }
}
