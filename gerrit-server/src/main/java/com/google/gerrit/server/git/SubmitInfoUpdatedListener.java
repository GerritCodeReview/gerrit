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

import com.google.common.base.Objects;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.project.ProjectState;

import org.eclipse.jgit.lib.ObjectId;

@ExtensionPoint
public interface SubmitInfoUpdatedListener {

  public interface Event {
    Project.NameKey getProjectName();
    SubmitInfo getOldSubmitInfo();
    SubmitInfo getNewSubmitInfo();
  }

  void onSubmitInfoUpdate(Event event);

  public class SubmitInfo {
    private final SubmitType submitType;
    private final boolean contentMerge;
    private final ObjectId ruleId;

    public SubmitInfo(ProjectState projectState) {
      this.submitType = projectState.getProject().getSubmitType();
      this.contentMerge = projectState.isUseContentMerge();
      this.ruleId = projectState.getConfig().getRulesId();
    }

    public SubmitInfo(ProjectConfig cfg, ProjectState parentProjectState) {
      this.submitType = cfg.getProject().getSubmitType();
      switch (cfg.getProject().getUseContentMerge()) {
        case TRUE:
          this.contentMerge = true;
          break;
        case FALSE:
          this.contentMerge = false;
          break;
        case INHERIT:
          this.contentMerge =
              parentProjectState != null
                  ? parentProjectState.isUseContentMerge()
                  : false;
          break;
        default:
          throw new IllegalStateException(
              "unexpected value for InheritedBoolean: "
                  + cfg.getProject().getUseContentMerge());
      }
      this.ruleId = cfg.getRulesId();
    }

    public SubmitInfo(SubmitType submitType, boolean contentMerge,
        ObjectId ruleId) {
      this.submitType = submitType;
      this.contentMerge = contentMerge;
      this.ruleId = ruleId;
    }

    public SubmitType getSubmitType() {
      return submitType;
    }

    public boolean isContentMerge() {
      return contentMerge;
    }

    public ObjectId getRuleId() {
      return ruleId;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof SubmitInfo)) {
        return false;
      }
      SubmitInfo other = (SubmitInfo)o;
      return submitType.equals(other.submitType)
          && contentMerge == other.contentMerge
          && (ruleId != null ? ruleId.equals(other.ruleId) : other.ruleId == null);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(submitType, contentMerge, ruleId);
    }

    @Override
    public String toString() {
      return "submitType: " + submitType + "; contentMerge: " + contentMerge
          + "; ruleId: " + (ruleId != null ? ruleId.getName() : null);
    }
  }
}
