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

package com.google.gerrit.extensions.api.projects;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.common.BatchLabelInput;
import com.google.gerrit.extensions.common.BatchSubmitRequirementInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.ListTagSortOption;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ProjectApi {
  ProjectApi create() throws RestApiException;

  ProjectApi create(ProjectInput in) throws RestApiException;

  ProjectInfo get() throws RestApiException;

  String description() throws RestApiException;

  void description(DescriptionInput in) throws RestApiException;

  ProjectAccessInfo access() throws RestApiException;

  @CanIgnoreReturnValue
  ProjectAccessInfo access(ProjectAccessInput p) throws RestApiException;

  ChangeInfo accessChange(ProjectAccessInput p) throws RestApiException;

  AccessCheckInfo checkAccess(AccessCheckInput in) throws RestApiException;

  CheckProjectResultInfo check(CheckProjectInput in) throws RestApiException;

  ConfigInfo config() throws RestApiException;

  @CanIgnoreReturnValue
  ConfigInfo config(ConfigInput in) throws RestApiException;

  @CanIgnoreReturnValue
  ChangeInfo configReview(ConfigInput in) throws RestApiException;

  Map<String, Set<String>> commitsIn(Collection<String> commits, Collection<String> refs)
      throws RestApiException;

  ListRefsRequest<BranchInfo> branches();

  ListRefsRequest<TagInfo> tags();

  void deleteBranches(DeleteBranchesInput in) throws RestApiException;

  void deleteTags(DeleteTagsInput in) throws RestApiException;

  abstract class ListRefsRequest<T extends RefInfo> {
    protected int limit;
    protected int start;
    protected boolean descendingOrder;
    protected String substring;
    protected String regex;
    protected String nextPageToken;
    protected ListTagSortOption sortBy = ListTagSortOption.REF;

    public abstract List<T> get() throws RestApiException;

    public ListRefsRequest<T> withLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public ListRefsRequest<T> withStart(int start) {
      this.start = start;
      return this;
    }

    public ListRefsRequest<T> withDescendingOrder(boolean descendingOrder) {
      this.descendingOrder = descendingOrder;
      return this;
    }

    public ListRefsRequest<T> withSortBy(ListTagSortOption sortBy) {
      this.sortBy = sortBy;
      return this;
    }

    public ListRefsRequest<T> withNextPageToken(String token) {
      this.nextPageToken = token;
      return this;
    }

    public ListRefsRequest<T> withSubstring(String substring) {
      this.substring = substring;
      return this;
    }

    public ListRefsRequest<T> withRegex(String regex) {
      this.regex = regex;
      return this;
    }

    public int getLimit() {
      return limit;
    }

    public int getStart() {
      return start;
    }

    public boolean getDescendingOrder() {
      return descendingOrder;
    }

    public ListTagSortOption getSortBy() {
      return sortBy;
    }

    public String getNextPageToken() {
      return nextPageToken;
    }

    public String getSubstring() {
      return substring;
    }

    public String getRegex() {
      return regex;
    }
  }

  List<ProjectInfo> children() throws RestApiException;

  List<ProjectInfo> children(boolean recursive) throws RestApiException;

  List<ProjectInfo> children(int limit) throws RestApiException;

  ChildProjectApi child(String name) throws RestApiException;

  /**
   * Look up a branch by refname.
   *
   * <p><strong>Note:</strong> This method eagerly reads the branch. Methods that mutate the branch
   * do not necessarily re-read the branch. Therefore, calling a getter method on an instance after
   * calling a mutation method on that same instance is not guaranteed to reflect the mutation. It
   * is not recommended to store references to {@code BranchApi} instances.
   *
   * @param ref branch name, with or without "refs/heads/" prefix.
   * @throws RestApiException if a problem occurred reading the project.
   * @return API for accessing the branch.
   */
  BranchApi branch(String ref) throws RestApiException;

  /**
   * Look up a tag by refname.
   *
   * <p>
   *
   * @param ref tag name, with or without "refs/tags/" prefix.
   * @throws RestApiException if a problem occurred reading the project.
   * @return API for accessing the tag.
   */
  TagApi tag(String ref) throws RestApiException;

  /**
   * Lookup a commit by its {@code ObjectId} string.
   *
   * @param commit the {@code ObjectId} string.
   * @return API for accessing the commit.
   */
  CommitApi commit(String commit) throws RestApiException;

  /**
   * Lookup a dashboard by its name.
   *
   * @param name the name.
   * @return API for accessing the dashboard.
   */
  DashboardApi dashboard(String name) throws RestApiException;

  /**
   * Get the project's default dashboard.
   *
   * @return API for accessing the dashboard.
   */
  DashboardApi defaultDashboard() throws RestApiException;

  /**
   * Set the project's default dashboard.
   *
   * @param name the dashboard to set as default.
   */
  void defaultDashboard(String name) throws RestApiException;

  /** Remove the project's default dashboard. */
  void removeDefaultDashboard() throws RestApiException;

  abstract class ListDashboardsRequest {
    public abstract List<DashboardInfo> get() throws RestApiException;
  }

  ListDashboardsRequest dashboards() throws RestApiException;

  /** Get the name of the branch to which {@code HEAD} points. */
  String head() throws RestApiException;

  /**
   * Set the project's {@code HEAD}.
   *
   * @param head the HEAD
   */
  void head(String head) throws RestApiException;

  /** Get the name of the project's parent. */
  String parent() throws RestApiException;

  /**
   * Set the project's parent.
   *
   * @param parent the parent
   */
  void parent(String parent) throws RestApiException;

  /**
   * Reindex the project and children in case {@code indexChildren} is specified.
   *
   * @param indexChildren decides if children should be indexed recursively
   */
  void index(boolean indexChildren) throws RestApiException;

  /** Reindexes all changes of the project. */
  void indexChanges() throws RestApiException;

  ListLabelsRequest labels() throws RestApiException;

  abstract class ListLabelsRequest {
    protected boolean inherited;

    protected String voteableOnRef;

    public abstract List<LabelDefinitionInfo> get() throws RestApiException;

    public ListLabelsRequest withInherited(boolean inherited) {
      this.inherited = inherited;
      return this;
    }

    public ListLabelsRequest withVoteableOnRef(String voteableOnRef) {
      this.voteableOnRef = voteableOnRef;
      return this;
    }
  }

  LabelApi label(String labelName) throws RestApiException;

  ListSubmitRequirementsRequest submitRequirements() throws RestApiException;

  abstract class ListSubmitRequirementsRequest {
    protected boolean inherited;

    public abstract List<SubmitRequirementInfo> get() throws RestApiException;

    public ListSubmitRequirementsRequest withInherited(boolean inherited) {
      this.inherited = inherited;
      return this;
    }
  }

  SubmitRequirementApi submitRequirement(String name) throws RestApiException;

  /**
   * Adds, updates and deletes label definitions in a batch.
   *
   * @param input input that describes additions, updates and deletions of label definitions
   */
  void labels(BatchLabelInput input) throws RestApiException;

  /** Same as {@link #labels(BatchLabelInput)}, but creates a change with required updates. */
  @CanIgnoreReturnValue
  ChangeInfo labelsReview(BatchLabelInput input) throws RestApiException;

  /**
   * Adds, updates and deletes submit requirements definitions in a batch.
   *
   * @param input input that describes additions, updates and deletions of submit requirements
   */
  void submitRequirements(BatchSubmitRequirementInput input) throws RestApiException;

  /**
   * Creates a change with required submit requirements updates.
   *
   * <p>See {@link #submitRequirements(BatchSubmitRequirementInput)} for details
   */
  @CanIgnoreReturnValue
  ChangeInfo submitRequirementsReview(BatchSubmitRequirementInput input) throws RestApiException;
}
