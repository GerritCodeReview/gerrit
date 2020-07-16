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

package com.google.gerrit.entities;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;

/** Portion of a {@link Project} describing superproject subscription rules. */
@AutoValue
public abstract class SubscribeSection {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public abstract Project.NameKey project();

  protected abstract ImmutableList<RefSpec> matchingRefSpecs();

  protected abstract ImmutableList<RefSpec> multiMatchRefSpecs();

  public static Builder builder(Project.NameKey project) {
    return new AutoValue_SubscribeSection.Builder().project(project);
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder project(Project.NameKey project);

    abstract ImmutableList.Builder<RefSpec> matchingRefSpecsBuilder();

    abstract ImmutableList.Builder<RefSpec> multiMatchRefSpecsBuilder();

    public Builder addMatchingRefSpec(String matchingRefSpec) {
      matchingRefSpecsBuilder()
          .add(new RefSpec(matchingRefSpec, RefSpec.WildcardMode.REQUIRE_MATCH));
      return this;
    }

    public Builder addMultiMatchRefSpec(String multiMatchRefSpec) {
      multiMatchRefSpecsBuilder()
          .add(new RefSpec(multiMatchRefSpec, RefSpec.WildcardMode.ALLOW_MISMATCH));
      return this;
    }

    public abstract SubscribeSection build();
  }

  /**
   * Determines if the <code>branch</code> could trigger a superproject update as allowed via this
   * subscribe section.
   *
   * @param branch the branch to check
   * @return if the branch could trigger a superproject update
   */
  public boolean appliesTo(BranchNameKey branch) {
    for (RefSpec r : matchingRefSpecs()) {
      if (r.matchSource(branch.branch())) {
        return true;
      }
    }
    for (RefSpec r : multiMatchRefSpecs()) {
      if (r.matchSource(branch.branch())) {
        return true;
      }
    }
    return false;
  }

  public Collection<String> matchingRefSpecsAsString() {
    return matchingRefSpecs().stream().map(RefSpec::toString).collect(toImmutableList());
  }

  public Collection<String> multiMatchRefSpecsAsString() {
    return multiMatchRefSpecs().stream().map(RefSpec::toString).collect(toImmutableList());
  }

  /** Evaluates what the destination branches for the subscription are. */
  public ImmutableSet<BranchNameKey> getDestinationBranches(
      BranchNameKey src, Collection<Ref> allRefsInRefsHeads) {
    Set<BranchNameKey> ret = new HashSet<>();
    logger.atFine().log("Inspecting SubscribeSection %s", this);
    for (RefSpec r : matchingRefSpecs()) {
      logger.atFine().log("Inspecting [matching] ref %s", r);
      if (!r.matchSource(src.branch())) {
        continue;
      }
      if (r.isWildcard()) {
        // refs/heads/*[:refs/somewhere/*]
        ret.add(BranchNameKey.create(project(), r.expandFromSource(src.branch()).getDestination()));
      } else {
        // e.g. refs/heads/master[:refs/heads/stable]
        String dest = r.getDestination();
        if (dest == null) {
          dest = r.getSource();
        }
        ret.add(BranchNameKey.create(project(), dest));
      }
    }

    for (RefSpec r : multiMatchRefSpecs()) {
      logger.atFine().log("Inspecting [all] ref %s", r);
      if (!r.matchSource(src.branch())) {
        continue;
      }
      for (Ref ref : allRefsInRefsHeads) {
        if (r.getDestination() != null && !r.matchDestination(ref.getName())) {
          continue;
        }
        BranchNameKey b = BranchNameKey.create(project(), ref.getName());
        if (!ret.contains(b)) {
          ret.add(b);
        }
      }
    }
    logger.atFine().log("Returning possible branches: %s for project %s", ret, project());
    return ImmutableSet.copyOf(ret);
  }

  @Override
  public final String toString() {
    StringBuilder ret = new StringBuilder();
    ret.append("[SubscribeSection, project=");
    ret.append(project());
    if (!matchingRefSpecs().isEmpty()) {
      ret.append(", matching=[");
      for (RefSpec r : matchingRefSpecs()) {
        ret.append(r.toString());
        ret.append(", ");
      }
    }
    if (!multiMatchRefSpecs().isEmpty()) {
      ret.append(", all=[");
      for (RefSpec r : multiMatchRefSpecs()) {
        ret.append(r.toString());
        ret.append(", ");
      }
    }
    ret.append("]");
    return ret.toString();
  }
}
