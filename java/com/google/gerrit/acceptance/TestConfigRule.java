// Copyright (C) 2024 The Android Open Source Project
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

import com.google.gerrit.acceptance.config.ConfigAnnotationParser;
import com.google.gerrit.acceptance.config.GerritSystemProperty;
import com.google.gerrit.server.util.git.DelegateSystemReader;
import java.io.File;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class TestConfigRule implements TestRule {
  private final TemporaryFolder temporaryFolder;
  private final AbstractDaemonTest test;
  private Description description;
  private GerritServer.Description methodDescription;
  GerritServer.Description classDescription;
  private boolean testRequiresSsh;
  private SystemReader oldSystemReader;

  public TestConfigRule(TemporaryFolder temporaryFolder, AbstractDaemonTest test) {
    this.temporaryFolder = temporaryFolder;
    this.test = test;
  }

  @Override
  public Statement apply(Statement statement, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        setTestConfigFromDescription(description);
        try {
          statement.evaluate();
        } finally {
          clear();
        }
      }
    };
  }

  private void setTestConfigFromDescription(Description description) {
    oldSystemReader = setFakeSystemReader(temporaryFolder.getRoot());

    this.description = description;
    classDescription = GerritServer.Description.forTestClass(description, test.configName);
    methodDescription = GerritServer.Description.forTestMethod(description, test.configName);

    if (methodDescription.systemProperties() != null) {
      ConfigAnnotationParser.parse(methodDescription.systemProperties());
    }

    if (methodDescription.systemProperty() != null) {
      ConfigAnnotationParser.parse(methodDescription.systemProperty());
    }

    test.baseConfig.unset("gerrit", null, "canonicalWebUrl");
    test.baseConfig.unset("httpd", null, "listenUrl");

    test.baseConfig.setInt("index", null, "batchThreads", -1);

    testRequiresSsh = classDescription.useSshAnnotation() || methodDescription.useSshAnnotation();
    if (!testRequiresSsh) {
      test.baseConfig.setString("sshd", null, "listenAddress", "off");
    }
  }

  private static SystemReader setFakeSystemReader(File tempDir) {
    SystemReader oldSystemReader = SystemReader.getInstance();
    SystemReader.setInstance(
        new DelegateSystemReader(oldSystemReader) {
          @Override
          public FileBasedConfig openJGitConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, new File(tempDir, "jgit.config"), FS.detect());
          }

          @Override
          public FileBasedConfig openUserConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, new File(tempDir, "user.config"), FS.detect());
          }

          @Override
          public FileBasedConfig openSystemConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, new File(tempDir, "system.config"), FS.detect());
          }
        });
    return oldSystemReader;
  }

  private void clear() {
    if (methodDescription.systemProperties() != null) {
      for (GerritSystemProperty sysProp : methodDescription.systemProperties().value()) {
        System.clearProperty(sysProp.name());
      }
    }

    if (methodDescription.systemProperty() != null) {
      System.clearProperty(methodDescription.systemProperty().name());
    }
    description = null;
    methodDescription = null;
    classDescription = null;
    testRequiresSsh = false;

    SystemReader.setInstance(oldSystemReader);
    oldSystemReader = null;
  }

  public Description description() {
    return description;
  }

  public GerritServer.Description methodDescription() {
    return methodDescription;
  }

  public GerritServer.Description classDescription() {
    return classDescription;
  }

  public boolean testRequiresSsh() {
    return testRequiresSsh;
  }

  public Config baseConfig() {
    return test.baseConfig;
  }
}
