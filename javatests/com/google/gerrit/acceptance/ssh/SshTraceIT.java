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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@UseSsh
public class SshTraceIT extends AbstractDaemonTest {
  @Inject private DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners;
  @Inject private DynamicSet<PerformanceLogger> performanceLoggers;

  private TraceValidatingProjectCreationValidationListener projectCreationListener;
  private RegistrationHandle projectCreationListenerRegistrationHandle;
  private TestPerformanceLogger testPerformanceLogger;
  private RegistrationHandle performanceLoggerRegistrationHandle;

  @Before
  public void setup() {
    projectCreationListener = new TraceValidatingProjectCreationValidationListener();
    projectCreationListenerRegistrationHandle =
        projectCreationValidationListeners.add("gerrit", projectCreationListener);
    testPerformanceLogger = new TestPerformanceLogger();
    performanceLoggerRegistrationHandle = performanceLoggers.add("gerrit", testPerformanceLogger);
  }

  @After
  public void cleanup() {
    projectCreationListenerRegistrationHandle.remove();
    performanceLoggerRegistrationHandle.remove();
  }

  @Test
  public void sshCallWithoutTrace() throws Exception {
    adminSshSession.exec("gerrit create-project new1");
    adminSshSession.assertSuccess();
    assertThat(projectCreationListener.traceId).isNull();
    assertThat(projectCreationListener.foundTraceId).isFalse();
    assertThat(projectCreationListener.isLoggingForced).isFalse();
  }

  @Test
  public void sshCallWithTrace() throws Exception {
    adminSshSession.exec("gerrit create-project --trace new2");

    // The trace ID is written to stderr.
    adminSshSession.assertFailure(RequestId.Type.TRACE_ID.name());

    assertThat(projectCreationListener.traceId).isNotNull();
    assertThat(projectCreationListener.foundTraceId).isTrue();
    assertThat(projectCreationListener.isLoggingForced).isTrue();
  }

  @Test
  public void sshCallWithTraceAndProvidedTraceId() throws Exception {
    adminSshSession.exec("gerrit create-project --trace --trace-id issue/123 new3");

    // The trace ID is written to stderr.
    adminSshSession.assertFailure(RequestId.Type.TRACE_ID.name());

    assertThat(projectCreationListener.traceId).isEqualTo("issue/123");
    assertThat(projectCreationListener.foundTraceId).isTrue();
    assertThat(projectCreationListener.isLoggingForced).isTrue();
  }

  @Test
  public void sshCallWithTraceIdAndWithoutTraceFails() throws Exception {
    adminSshSession.exec("gerrit create-project --trace-id issue/123 new4");
    adminSshSession.assertFailure("A trace ID can only be set if --trace was specified.");
  }

  @Test
  public void performanceLoggingForSshCall() throws Exception {
    adminSshSession.exec("gerrit create-project new5");
    adminSshSession.assertSuccess();
    assertThat(testPerformanceLogger.logEntries()).isNotEmpty();
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
    private List<PerformanceLogEntry> logEntries = new ArrayList<>();

    @Override
    public void log(String operation, long durationMs, Map<String, Optional<Object>> metaData) {
      logEntries.add(PerformanceLogEntry.create(operation, metaData));
    }

    ImmutableList<PerformanceLogEntry> logEntries() {
      return ImmutableList.copyOf(logEntries);
    }
  }

  @AutoValue
  abstract static class PerformanceLogEntry {
    static PerformanceLogEntry create(String operation, Map<String, Optional<Object>> metaData) {
      return new AutoValue_SshTraceIT_PerformanceLogEntry(operation, ImmutableMap.copyOf(metaData));
    }

    abstract String operation();

    abstract ImmutableMap<String, Object> metaData();
  }
}
