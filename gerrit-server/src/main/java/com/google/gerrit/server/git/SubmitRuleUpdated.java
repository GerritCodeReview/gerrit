// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.SubmitInfoUpdatedListener.SubmitInfo;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubmitRuleUpdated {
  private static final Logger log = LoggerFactory.getLogger(SubmitRuleUpdated.class);

  private final DynamicSet<SubmitInfoUpdatedListener> listeners;

  @Inject
  SubmitRuleUpdated(DynamicSet<SubmitInfoUpdatedListener> listeners) {
    this.listeners = listeners;
  }

  public void fire(final Project.NameKey projectName,
      final SubmitInfo oldSubmitInfo, final SubmitInfo newSubmitInfo) {
    if ((newSubmitInfo != null && !newSubmitInfo.equals(oldSubmitInfo)) ||
        (newSubmitInfo == null && oldSubmitInfo != null)) {
      SubmitInfoUpdatedListener.Event event =
          new SubmitInfoUpdatedListener.Event() {
            @Override
            public Project.NameKey getProjectName() {
              return projectName;
            }

            @Override
            public SubmitInfo getOldSubmitInfo() {
              return oldSubmitInfo;
            }

            @Override
            public SubmitInfo getNewSubmitInfo() {
              return newSubmitInfo;
            }
          };
      for (SubmitInfoUpdatedListener l : listeners) {
        try {
          l.onSubmitInfoUpdate(event);
        } catch (RuntimeException e) {
          log.warn("Failure in SubmitInfoUpdatedListener", e);
        }
      }
    }
  }
}
