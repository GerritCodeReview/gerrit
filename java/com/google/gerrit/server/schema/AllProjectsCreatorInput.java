package com.google.gerrit.server.schema;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.UsedAt;
import java.util.List;
import java.util.Optional;

/** Input for initializing an "All-Projects". */
@AutoValue
public abstract class AllProjectsCreatorInput {
  @UsedAt(UsedAt.Project.GOOGLE)
  public static LabelType getDefaultCodeReviewLabel() {
    LabelType type =
        new LabelType(
            "Code-Review",
            ImmutableList.of(
                new LabelValue((short) 2, "Looks good to me, approved"),
                new LabelValue((short) 1, "Looks good to me, but someone else must approve"),
                new LabelValue((short) 0, "No score"),
                new LabelValue((short) -1, "I would prefer this is not merged as is"),
                new LabelValue((short) -2, "This shall not be merged")));
    type.setCopyMinScore(true);
    type.setCopyAllScoresOnTrivialRebase(true);
    return type;
  }

  /** The administrator group which gets default permissions granted. */
  public abstract GroupReference adminGroup();

  /** The group which gets stream-events permission granted and appropriate properties set. */
  public abstract GroupReference batchGroup();

  /** The commit message used when commit the project config change. */
  public abstract Optional<String> commitMessage();

  /** The first change-id used in this host. */
  public abstract int firstChangeId();

  /** The "Code-Review" label to be defined in the All-Projects. */
  public abstract LabelType codeReviewLabel();

  /** Other labels to be defined in the All-Projects. */
  public abstract ImmutableList<LabelType> additionalLabelType();

  public abstract Builder toBuilder();

  static Builder builder() {
    return new AutoValue_AllProjectsCreatorInput.Builder()
        .codeReviewLabel(getDefaultCodeReviewLabel())
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
