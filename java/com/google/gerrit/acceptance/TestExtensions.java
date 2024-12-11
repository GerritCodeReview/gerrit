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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.ValidationOptionsListener;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
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

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>This class contains only static classes and hence never needs to be instantiated.
   */
  private TestExtensions() {}
}
