package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@UseSsh
public class SshTraceIT extends AbstractDaemonTest {
  @Inject private DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners;

  private TraceValidatingProjectCreationValidationListener projectCreationListener;
  private RegistrationHandle projectCreationListenerRegistrationHandle;

  @Before
  public void setup() {
    projectCreationListener = new TraceValidatingProjectCreationValidationListener();
    projectCreationListenerRegistrationHandle =
        projectCreationValidationListeners.add(projectCreationListener);
  }

  @After
  public void cleanup() {
    projectCreationListenerRegistrationHandle.remove();
  }

  @Test
  public void sshCallWithoutTrace() throws Exception {
    adminSshSession.exec("gerrit create-project new1");
    adminSshSession.assertSuccess();
    assertThat(projectCreationListener.foundTraceId).isFalse();
    assertThat(projectCreationListener.isLoggingForced).isFalse();
  }

  @Test
  public void sshCallWithTrace() throws Exception {
    adminSshSession.exec("gerrit create-project --trace new2");

    // The trace ID is written to stderr.
    adminSshSession.assertFailure(RequestId.Type.TRACE_ID.name());

    assertThat(projectCreationListener.foundTraceId).isTrue();
    assertThat(projectCreationListener.isLoggingForced).isTrue();
  }

  private static class TraceValidatingProjectCreationValidationListener
      implements ProjectCreationValidationListener {
    Boolean foundTraceId;
    Boolean isLoggingForced;

    @Override
    public void validateNewProject(CreateProjectArgs args) throws ValidationException {
      this.foundTraceId = LoggingContext.getInstance().getTagsAsMap().containsKey("TRACE_ID");
      this.isLoggingForced = LoggingContext.getInstance().shouldForceLogging(null, null, false);
    }
  }
}
