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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.DIRECT_PUSH;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.PluginPushOption;
import com.google.gerrit.server.ValidationOptionsListener;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationInfo;
import com.google.gerrit.server.git.validators.CommitValidationInfoListener;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.update.context.RefUpdateContext;
import java.util.List;

/**
 * Class to host common test extension implementations.
 *
 * <p>To test the invocation of an extension point tests usually register a test implementation for
 * the extension that records the parameters with which it has been called.
 *
 * <p>If the same extension point is triggered by different actions, these test extension
 * implementations may be needed in different test classes. To avoid duplicating them in the test
 * classes, they can be added to this class and then be reused from the different tests.
 */
public class TestExtensions {
  public static class TestCommitValidationListener implements CommitValidationListener {
    public CommitReceivedEvent receiveEvent;

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      this.receiveEvent = receiveEvent;
      return ImmutableList.of();
    }
  }

  public static class TestValidationOptionsListener implements ValidationOptionsListener {
    public ImmutableListMultimap<String, String> validationOptions;

    @Override
    public void onPatchSetCreation(
        BranchNameKey projectAndBranch,
        PatchSet.Id patchSetId,
        ImmutableListMultimap<String, String> validationOptions) {
      this.validationOptions = validationOptions;
    }
  }

  public static class TestCommitValidationInfoListener implements CommitValidationInfoListener {
    public ImmutableMap<String, CommitValidationInfo> validationInfoByValidator;
    public CommitReceivedEvent receiveEvent;
    @Nullable public PatchSet.Id patchSetId;
    public boolean hasChangeModificationRefContext;
    public boolean hasDirectPushRefContext;

    @Override
    public void commitValidated(
        ImmutableMap<String, CommitValidationInfo> validationInfoByValidator,
        CommitReceivedEvent receiveEvent,
        PatchSet.Id patchSetId) {
      this.validationInfoByValidator = validationInfoByValidator;
      this.receiveEvent = receiveEvent;
      this.patchSetId = patchSetId;
      this.hasChangeModificationRefContext = RefUpdateContext.hasOpen(CHANGE_MODIFICATION);
      this.hasDirectPushRefContext = RefUpdateContext.hasOpen(DIRECT_PUSH);
    }
  }

  public static class TestPluginPushOption implements PluginPushOption {
    private final String name;
    private final String description;
    private final Boolean enabled;

    public TestPluginPushOption(String name, String description, Boolean enabled) {
      this.name = name;
      this.description = description;
      this.enabled = enabled;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public boolean isOptionEnabled(ChangeNotes changeNotes) {
      return enabled;
    }
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>This class contains only static classes and hence never needs to be instantiated.
   */
  private TestExtensions() {}
}
