// Copyright (C) 2018 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import org.junit.Test;

@UseSsh
public class SshTraceIT extends AbstractDaemonTest {
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void sshCallWithoutTrace() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      adminSshSession.exec("gerrit create-project new1");
      adminSshSession.assertSuccess();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.foundTraceId).isFalse();
      assertThat(projectCreationListener.isLoggingForced).isFalse();
    }
  }

  @Test
  public void sshCallWithTrace() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      adminSshSession.exec("gerrit create-project --trace new2");

      // The trace ID is written to stderr.
      adminSshSession.assertFailure(RequestId.Type.TRACE_ID.name());

      assertThat(projectCreationListener.traceId).isNotNull();
      assertThat(projectCreationListener.foundTraceId).isTrue();
      assertThat(projectCreationListener.isLoggingForced).isTrue();
    }
  }

  @Test
  public void sshCallWithTraceAndProvidedTraceId() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      adminSshSession.exec("gerrit create-project --trace --trace-id issue/123 new3");

      // The trace ID is written to stderr.
      adminSshSession.assertFailure(RequestId.Type.TRACE_ID.name());

      assertThat(projectCreationListener.traceId).isEqualTo("issue/123");
      assertThat(projectCreationListener.foundTraceId).isTrue();
      assertThat(projectCreationListener.isLoggingForced).isTrue();
    }
  }

  @Test
  public void sshCallWithTraceIdAndWithoutTraceFails() throws Exception {
    adminSshSession.exec("gerrit create-project --trace-id issue/123 new4");
    adminSshSession.assertFailure("A trace ID can only be set if --trace was specified.");
  }

  @Test
  @GerritConfig(name = "tracing.performanceLogging", value = "true")
  public void performanceLoggingForSshCall() throws Exception {
    TestPerformanceLogger testPerformanceLogger = new TestPerformanceLogger();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testPerformanceLogger)) {
      adminSshSession.exec("gerrit create-project new5");
      adminSshSession.assertSuccess();
      assertThat(testPerformanceLogger.logEntries()).isNotEmpty();
    }
  }

  private static class TraceValidatingProjectCreationValidationListener
      implements ProjectCreationValidationListener {
    String traceId;
    Boolean foundTraceId;
    Boolean isLoggingForced;

    @Override
    public void validateNewProject(CreateProjectArgs args) throws ValidationException {
      this.traceId =
          Iterables.getFirst(LoggingContext.getInstance().getTagsAsMap().get("TRACE_ID"), null);
      this.foundTraceId = traceId != null;
      this.isLoggingForced = LoggingContext.getInstance().shouldForceLogging(null, null, false);
    }
  }

  private static class TestPerformanceLogger implements PerformanceLogger {
    private ImmutableList.Builder<PerformanceLogEntry> logEntries = ImmutableList.builder();

    @Override
    public void log(String operation, long durationMs, Metadata metadata) {
      logEntries.add(PerformanceLogEntry.create(operation, metadata));
    }

    ImmutableList<PerformanceLogEntry> logEntries() {
      return logEntries.build();
    }
  }

  @AutoValue
  abstract static class PerformanceLogEntry {
    static PerformanceLogEntry create(String operation, Metadata metadata) {
      return new AutoValue_SshTraceIT_PerformanceLogEntry(operation, metadata);
    }

    abstract String operation();

    abstract Metadata metadata();
  }
}
