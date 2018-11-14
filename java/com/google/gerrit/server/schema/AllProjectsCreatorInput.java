package com.google.gerrit.server.schema;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import java.util.List;

@AutoValue
abstract class AllProjectsCreatorInput {

  /** The administrator group which gets default permissions granted. */
  public abstract GroupReference adminGroup();

  /** The group which gets stream-events permission granted and appropriate properties set. */
  public abstract GroupReference batchGroup();

  /** The commit message used when commit the project config change. */
  public abstract String commitMessage();

  /** The first change-id used in this host. */
  public abstract int firstChangeId();

  /** The "Code-Review" label to be defined in the All-Projects. */
  public abstract LabelType codeReviewLabel();

  /** Other labels to be defined in the All-Projects. */
  public abstract ImmutableList<LabelType> additionalLabelType();

  public abstract Builder toBuilder();

  static Builder builder() {
    return new AutoValue_AllProjectsCreatorInput.Builder()
        .firstChangeId(ReviewDb.FIRST_CHANGE_ID)
        .additionalLabelType(ImmutableList.of());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder adminGroup(GroupReference adminGroup);

    public abstract Builder batchGroup(GroupReference batchGroup);

    public abstract Builder commitMessage(String commitMessage);

    public abstract Builder firstChangeId(int firstChangeId);

    public abstract Builder codeReviewLabel(LabelType codeReviewLabel);

    public abstract Builder additionalLabelType(List<LabelType> additionalLabelType);

    abstract AllProjectsCreatorInput build();
  }
}
