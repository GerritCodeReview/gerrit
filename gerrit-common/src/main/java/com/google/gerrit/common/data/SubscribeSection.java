// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.common.annotations.GwtIncompatible;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.transport.RefSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Portion of a {@link Project} describing superproject subscription rules. */
@GwtIncompatible("Unemulated org.eclipse.jgit.transport.RefSpec")
public class SubscribeSection {

  private final List<RefSpec> refSpecs;
  private final Project.NameKey project;

  public SubscribeSection(Project.NameKey p) {
    project = p;
    refSpecs = new ArrayList<>();
  }

  public void addRefSpec(RefSpec spec) {
    refSpecs.add(spec);
  }

  public void addRefSpec(String spec) {
    refSpecs.add(new RefSpec(spec));
  }

  public Project.NameKey getProject() {
    return project;
  }

  /**
   * Determines if the <code>branch</code> could trigger a
   * superproject update as allowed via this subscribe section.
   *
   * @param branch the branch to check
   * @return if the branch could trigger a superproject update
   */
  public boolean appliesTo(Branch.NameKey branch) {
    for (RefSpec r : refSpecs) {
      if (r.matchSource(branch.get())) {
        return true;
      }
    }
    return false;
  }

  public Collection<RefSpec> getRefSpecs() {
    return Collections.unmodifiableCollection(refSpecs);
  }

  @Override
  public String toString() {
    StringBuilder ret = new StringBuilder();
    ret.append("[SubscribeSection, project=");
    ret.append(project);
    ret.append(", refs=[");
    for (RefSpec r : refSpecs) {
      ret.append(r.toString());
      ret.append(", ");
    }
    ret.append("]");
    return ret.toString();
  }
}
