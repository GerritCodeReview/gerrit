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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.ListResultSet;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;

@NoHttpd
@UseSsh
public class QueryIT extends AbstractDaemonTest {
  private static Gson gson = new Gson();

  @Test
  public void basicQueryJSON() throws Exception {
    String changeId1 = createChange().getChangeId();
    String changeId2 = createChange().getChangeId();

    List<ChangeAttribute> changes = executeSuccessfulQuery("1234");
    assertThat(changes).isEmpty();

    changes = executeSuccessfulQuery(changeId1);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).project).isEqualTo(project.toString());
    assertThat(changes.get(0).id).isEqualTo(changeId1);

    changes = executeSuccessfulQuery(changeId1 + " OR " + changeId2);
    assertThat(changes).hasSize(2);
    assertThat(changes.get(0).project).isEqualTo(project.toString());
    assertThat(changes.get(0).id).isEqualTo(changeId2);
    assertThat(changes.get(1).project).isEqualTo(project.toString());
    assertThat(changes.get(1).id).isEqualTo(changeId1);

    changes = executeSuccessfulQuery("--start=1 " + changeId1 + " OR " + changeId2);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).project).isEqualTo(project.toString());
    assertThat(changes.get(0).id).isEqualTo(changeId1);
  }

  @Test
  public void allApprovalsOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets).isNull();

    changes = executeSuccessfulQuery("--all-approvals " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).approvals).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).approvals).hasSize(1);
  }

  @Test
  public void allReviewersOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    ReviewerInput in = new ReviewerInput();
    in.reviewer = user.email();
    gApi.changes().id(changeId).addReviewer(in);

    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).allReviewers).isNull();

    changes = executeSuccessfulQuery("--all-reviewers " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).allReviewers).isNotNull();
    assertThat(changes.get(0).allReviewers).hasSize(1);
  }

  @Test
  public void commitMessageOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    List<ChangeAttribute> changes = executeSuccessfulQuery("--commit-message " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).commitMessage).isNotNull();
    assertThat(changes.get(0).commitMessage).contains(PushOneCommit.SUBJECT);
  }

  @Test
  public void currentPatchSetOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    amendChange(changeId);

    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).currentPatchSet).isNull();

    changes = executeSuccessfulQuery("--current-patch-set " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).currentPatchSet).isNotNull();
    assertThat(changes.get(0).currentPatchSet.number).isEqualTo(2);

    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    changes = executeSuccessfulQuery("--current-patch-set " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).currentPatchSet).isNotNull();
    assertThat(changes.get(0).currentPatchSet.approvals).isNotNull();
    assertThat(changes.get(0).currentPatchSet.approvals).hasSize(1);
  }

  @Test
  public void patchSetsOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    amendChange(changeId);
    amendChange(changeId);

    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets).isNull();

    changes = executeSuccessfulQuery("--patch-sets " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets).isNotNull();
    assertThat(changes.get(0).patchSets).hasSize(3);
  }

  @Test
  public void shouldFailWithFilesWithoutPatchSetsOrCurrentPatchSetsOption() throws Exception {
    String changeId = createChange().getChangeId();
    adminSshSession.exec("gerrit query --files " + changeId);
    adminSshSession.assertFailure("needs --patch-sets or --current-patch-set");
  }

  @Test
  public void fileOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();

    List<ChangeAttribute> changes =
        executeSuccessfulQuery("--current-patch-set --files " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).currentPatchSet.files).isNotNull();
    assertThat(changes.get(0).currentPatchSet.files).hasSize(2);

    changes = executeSuccessfulQuery("--patch-sets --files " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets.get(0).files).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).files).hasSize(2);

    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    changes = executeSuccessfulQuery("--patch-sets --files --all-approvals " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets.get(0).files).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).files).hasSize(2);
    assertThat(changes.get(0).patchSets.get(0).approvals).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).approvals).hasSize(1);
  }

  @Test
  public void commentOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();

    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).comments).isNull();

    changes = executeSuccessfulQuery("--comments " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).comments).isNotNull();
    assertThat(changes.get(0).comments).hasSize(1);
  }

  @Test
  public void commentOptionsInCurrentPatchSetJSON() throws Exception {
    String changeId = createChange().getChangeId();

    ReviewInput review = new ReviewInput();
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.path = PushOneCommit.FILE_NAME;
    comment.side = Side.REVISION;
    comment.message = "comment 1";
    review.comments = new HashMap<>();
    review.comments.put(comment.path, Lists.newArrayList(comment));
    gApi.changes().id(changeId).current().review(review);

    List<ChangeAttribute> changes = executeSuccessfulQuery("--current-patch-set " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).currentPatchSet.comments).isNull();

    changes = executeSuccessfulQuery("--current-patch-set --comments " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).currentPatchSet.comments).isNotNull();
    assertThat(changes.get(0).currentPatchSet.comments).hasSize(1);
  }

  @Test
  public void commentOptionInPatchSetsJSON() throws Exception {
    String changeId = createChange().getChangeId();

    ReviewInput review = new ReviewInput();
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.path = PushOneCommit.FILE_NAME;
    comment.side = Side.REVISION;
    comment.message = "comment 1";
    review.comments = new HashMap<>();
    review.comments.put(comment.path, Lists.newArrayList(comment));
    gApi.changes().id(changeId).current().review(review);

    List<ChangeAttribute> changes = executeSuccessfulQuery("--patch-sets " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets.get(0).comments).isNull();

    changes = executeSuccessfulQuery("--patch-sets --comments " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets.get(0).comments).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).comments).hasSize(1);

    changes = executeSuccessfulQuery("--patch-sets --comments --files " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets.get(0).comments).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).comments).hasSize(1);
    assertThat(changes.get(0).patchSets.get(0).files).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).files).hasSize(2);

    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    changes = executeSuccessfulQuery("--patch-sets --comments --files --all-approvals " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets.get(0).comments).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).comments).hasSize(1);
    assertThat(changes.get(0).patchSets.get(0).files).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).files).hasSize(2);
    assertThat(changes.get(0).patchSets.get(0).approvals).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).approvals).hasSize(1);
  }

  @Test
  public void dependenciesOptionJSON() throws Exception {
    String changeId1 = createChange().getChangeId();
    String changeId2 = createChange().getChangeId();
    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId1);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).dependsOn).isNull();

    changes = executeSuccessfulQuery("--dependencies " + changeId1);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).dependsOn).isNull();

    changes = executeSuccessfulQuery(changeId2);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).dependsOn).isNull();

    changes = executeSuccessfulQuery("--dependencies " + changeId2);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).dependsOn).isNotNull();
    assertThat(changes.get(0).dependsOn).hasSize(1);
  }

  @Test
  public void submitRecordsOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).submitRecords).isNull();

    changes = executeSuccessfulQuery("--submit-records " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).submitRecords).isNotNull();
    assertThat(changes.get(0).submitRecords).hasSize(1);
  }

  @Test
  public void allChangeOptionsAreServedWithoutExceptions() throws Exception {
    PushOneCommit.Result r = createChange();
    // Merge the change so that the result has more data and potentially went through more
    // computation while formatting the output, such as labels, reviewers etc.
    merge(r);
    for (ListChangesOption option : ListChangesOption.values()) {
      assertWithMessage("Option: " + option)
          .that(gApi.changes().query(r.getChangeId()).withOption(option).get())
          .hasSize(1);
    }
  }

  @Test
  public void chooseCheapestDatasource() throws Exception {
    try (AutoCloseable ignored = installPlugin("myplugin", SamplePluginModule.class)) {
      for (int i = 0; i < 5; i++) {
        createChange();
      }
      List<ChangeAttribute> changes =
          executeSuccessfulQuery("status:open " + CheapSource.FIELD + "_myplugin:foo");
      assertThat(changes).hasSize(0);
    }
  }

  @Test
  public void allApprovalsAllPatchSetsOptionsWithCopyConditionJSON() throws Exception {
    // Copy min Code-Review votes
    try (ProjectConfigUpdate u = updateProject(Project.NameKey.parse("All-Projects"))) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("is:MIN"));
      u.save();
    }

    // Create a change and add Code-Review -2 on first patch-set
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.reject());

    // Create second patch-set
    amendChange(changeId);

    // Assert that second patch-set has Code-Review -2 vote
    List<ChangeAttribute> changes =
        executeSuccessfulQuery("--all-approvals --patch-sets " + changeId);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).patchSets).hasSize(2);
    assertThat(changes.get(0).patchSets.get(1).approvals).isNotNull();
    assertThat(changes.get(0).patchSets.get(1).approvals).hasSize(1);
    assertThat(changes.get(0).patchSets.get(1).approvals.get(0).type)
        .isEqualTo(LabelId.CODE_REVIEW);
    assertThat(changes.get(0).patchSets.get(1).approvals.get(0).value).isEqualTo("-2");
  }

  protected static class SamplePluginModule extends AbstractModule {
    @Override
    public void configure() {
      bind(ChangeQueryBuilder.ChangeOperatorFactory.class)
          .annotatedWith(Exports.named(CheapSource.FIELD))
          .to(CheapSourceOperator.class);
    }
  }

  protected static class CheapSourceOperator implements ChangeQueryBuilder.ChangeOperatorFactory {
    @Override
    public Predicate<ChangeData> create(ChangeQueryBuilder builder, String value)
        throws QueryParseException {
      return new CheapSource(value);
    }
  }

  protected static class CheapSource extends OperatorPredicate<ChangeData>
      implements Matchable<ChangeData>, ChangeDataSource {
    public static final String FIELD = "cheapsource";

    public CheapSource(String value) {
      super(FIELD, value);
    }

    @Override
    public int getCardinality() {
      return 1;
    }

    @Override
    public ResultSet<ChangeData> read() {
      return new ListResultSet<>(new ArrayList<>());
    }

    @Override
    public ResultSet<FieldBundle> readRaw() {
      return null;
    }

    @Override
    public boolean match(ChangeData object) {
      return true;
    }

    @Override
    public int getCost() {
      return 1;
    }

    @Override
    public boolean hasChange() {
      return false;
    }
  }

  private List<ChangeAttribute> executeSuccessfulQuery(String params, SshSession session)
      throws Exception {
    String rawResponse = session.exec("gerrit query --format=JSON " + params);
    session.assertSuccess();
    return getChanges(rawResponse);
  }

  private List<ChangeAttribute> executeSuccessfulQuery(String params) throws Exception {
    return executeSuccessfulQuery(params, adminSshSession);
  }

  private static List<ChangeAttribute> getChanges(String rawResponse) {
    List<String> lines = Splitter.on("\n").omitEmptyStrings().splitToList(rawResponse);
    List<ChangeAttribute> changes = new ArrayList<>(lines.size() - 1);
    for (int i = 0; i < lines.size() - 1; i++) {
      changes.add(gson.fromJson(lines.get(i), ChangeAttribute.class));
    }
    return changes;
  }
}
