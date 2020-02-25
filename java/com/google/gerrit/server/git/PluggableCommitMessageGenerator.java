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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.inject.Inject;
import org.eclipse.jgit.revwalk.RevCommit;

/** Helper to call plugins that want to change the commit message before a change is merged. */
public class PluggableCommitMessageGenerator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DynamicSet<ChangeMessageModifier> changeMessageModifiers;

  @Inject
  PluggableCommitMessageGenerator(DynamicSet<ChangeMessageModifier> changeMessageModifiers) {
    this.changeMessageModifiers = changeMessageModifiers;
  }

  /**
   * Returns the commit message as modified by plugins. The returned message can be equal to {@code
   * originalMessage} in case no plugins are registered or the registered plugins decided not to
   * modify the message.
   */
  public String generate(
      RevCommit original, RevCommit mergeTip, BranchNameKey dest, String originalMessage) {
    requireNonNull(original.getRawBuffer());
    if (mergeTip != null) {
      requireNonNull(mergeTip.getRawBuffer());
    }

    int count = 0;
    String current = originalMessage;
    for (Extension<ChangeMessageModifier> ext : changeMessageModifiers.entries()) {
      ChangeMessageModifier changeMessageModifier = ext.get();
      String className = changeMessageModifier.getClass().getName();
      current = changeMessageModifier.onSubmit(current, original, mergeTip, dest);
      checkState(
          current != null,
          "%s.onSubmit from plugin %s returned null instead of new commit message",
          className,
          ext.getPluginName());
      count++;
      logger.atFine().log(
          "Invoked %s from plugin %s, message length now %d",
          className, ext.getPluginName(), current.length());
    }
    logger.atFine().log(
        "Invoked %d ChangeMessageModifiers on message with original length %d",
        count, originalMessage.length());
    return current;
  }
}
