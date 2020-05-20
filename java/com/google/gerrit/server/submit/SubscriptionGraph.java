package com.google.gerrit.server.submit;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmoduleSubscription;
import java.util.Collection;
import java.util.Set;

/** A container which stores subscription relationship. */
public interface SubscriptionGraph {

  interface Factory {
    SubscriptionGraph create(
        MergeOpRepoManager orm, Set<BranchNameKey> updatedBranches, boolean enableSubscription)
        throws SubmoduleConflictException;
  }

  // Get all {@code SubmoduleSubscription}s with {@code branch} as a super branch.
  Collection<SubmoduleSubscription> subscribedBy(BranchNameKey branch);

  // See if a branch is a superproject branch affected.
  boolean hasSubscription(BranchNameKey branch);

  // Get all superprojects affected.
  Set<Project.NameKey> getAffectedSuperProjects();

  // See if a project is a superproject affected.
  boolean isAffected(Project.NameKey project);

  // Get all subscriptions which contains {@code project} as a superpeoject.
  Set<BranchNameKey> getAffectedBranches(Project.NameKey project);

  // Get all affected branches sorted by traversal order.
  ImmutableSet<BranchNameKey> getSortedBranches();

  // Check if a branch is subscribed by other branches, only used for delete precondition check in
  // plugin.
  boolean hasSuperproject(BranchNameKey branch);
}
