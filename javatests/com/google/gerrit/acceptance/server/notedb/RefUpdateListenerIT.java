package com.google.gerrit.acceptance.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Test;

@Sandboxed
@UseLocalDisk
@NoHttpd
public class RefUpdateListenerIT extends AbstractDaemonTest {

  private TestRefOperationValidationListener testRefOperationListener;

  public class TestRefOperationValidationListener implements RefOperationValidationListener {

    private int count = 0;

    @Override
    public List<ValidationMessage> onRefOperation(RefReceivedEvent refEvent)
        throws ValidationException {
      count++;
      return Collections.emptyList();
    }

    public int getCount() {
      return count;
    }

    public void reset() {
      count = 0;
    }
  }

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        testRefOperationListener = new TestRefOperationValidationListener();
        DynamicSet.bind(binder(), RefOperationValidationListener.class)
            .toInstance(testRefOperationListener);
      }
    };
  }

  static {
    System.setProperty("gerrit.notedb", "ON");
  }

  @After
  public void cleanup() {
    testRefOperationListener.reset();
  }

  @Test
  public void aNormalPushShouldTriggerARefOperationValidation() throws Exception {
    PushOneCommit.Result r =
        createCommitAndPush(testRepo, "refs/heads/master", "msg", "file", "content");

    assertThat(testRefOperationListener.getCount()).isEqualTo(1);
  }

  @Test
  public void aMagicRefUpdateShouldTriggerARefOperationValidationOnChangesBranch()
      throws Exception {
    PushOneCommit.Result r = createChange("refs/for/master");

    assertThat(testRefOperationListener.getCount()).isEqualTo(1);
  }
}
