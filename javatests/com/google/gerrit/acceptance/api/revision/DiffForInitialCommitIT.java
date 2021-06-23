package com.google.gerrit.acceptance.api.revision;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

public class DiffForInitialCommitIT extends AbstractDaemonTest {
  @Inject private DiffOperations diffOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void getModifiedFilesForInitialCommit() throws Exception {
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(
                new CommitValidationListener() {
                  @Override
                  public List<CommitValidationMessage> onCommitReceived(
                      CommitReceivedEvent receiveEvent) throws CommitValidationException {
                    try {
                      diffOperations.listModifiedFilesAgainstParent(
                          project, receiveEvent.commit, null);
                    } catch (DiffNotAvailableException e) {
                      e.printStackTrace();
                      throw new CommitValidationException("getting modifed files failed", e);
                    }
                    return ImmutableList.of();
                  }
                })) {
      createChange();
    }
  }
}
