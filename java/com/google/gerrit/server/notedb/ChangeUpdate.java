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

package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.entities.RefNames.changeMetaRef;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_ATTENTION;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_BRANCH;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_CHANGE_ID;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_CHERRY_PICK_OF;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_COMMIT;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_COPIED_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_CURRENT;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_CUSTOM_KEYED_VALUE;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_GROUPS;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_PATCH_SET_DESCRIPTION;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_PRIVATE;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_REAL_USER;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_REVERT_OF;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_SUBJECT;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_SUBMISSION_ID;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_TAG;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_TOPIC;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_WORK_IN_PROGRESS;
import static com.google.gerrit.server.notedb.NoteDbUtil.sanitizeFooter;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.collect.TreeBasedTable;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.server.ChangeDraftUpdate;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.approval.PatchSetApprovalUuidGenerator;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A delta to apply to a change.
 *
 * <p>This delta will become two unique commits: one in the AllUsers repo that will contain the
 * draft comments on this change and one in the notes branch that will contain approvals, reviewers,
 * change status, subject, submit records, the change message, and published comments. There are
 * limitations on the set of modifications that can be handled in a single update. In particular,
 * there is a single author and timestamp for each update.
 *
 * <p>This class is not thread-safe.
 *
 * <p>NOTE: This class also serializes the change in a custom storage format, used in NoteDB. All
 * changes to the storage format must be both forward and backward compatible, see comment on {@link
 * ChangeNotesParser}.
 *
 * <p>Such changes include e.g. introducing/removing footers, modifying footer formats, mutations of
 * the attached {@link ChangeRevisionNote}.
 */
public class ChangeUpdate extends AbstractChangeUpdate {
  public interface Factory {
    ChangeUpdate create(ChangeNotes notes, CurrentUser user, Instant when);

    ChangeUpdate create(
        ChangeNotes notes, CurrentUser user, Instant when, Comparator<String> labelNameComparator);
  }

  public static final int MAX_CUSTOM_KEY_LENGTH = 100;
  public static final int MAX_CUSTOM_KEYED_VALUE_LENGTH = 1000;
  public static final int MAX_CUSTOM_KEYED_VALUES = 100;

  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeDraftUpdate.ChangeDraftUpdateFactory draftUpdateFactory;
  private final RobotCommentUpdate.Factory robotCommentUpdateFactory;
  private final DeleteCommentRewriter.Factory deleteCommentRewriterFactory;
  private final ServiceUserClassifier serviceUserClassifier;
  private final PatchSetApprovalUuidGenerator patchSetApprovalUuidGenerator;

  private final Table<String, Account.Id, Optional<PatchSetApproval>> approvals;
  private final List<PatchSetApproval> copiedApprovals = new ArrayList<>();
  private final Map<Account.Id, ReviewerStateInternal> reviewers = new LinkedHashMap<>();
  private final Map<Address, ReviewerStateInternal> reviewersByEmail = new LinkedHashMap<>();
  private final List<HumanComment> comments = new ArrayList<>();

  private String commitSubject;
  private String subject;
  private String changeId;
  private String branch;
  private Change.Status status;
  private List<SubmitRecord> submitRecords;
  private String submissionId;
  private String topic;
  private String commit;
  private Map<Account.Id, AttentionSetUpdate> plannedAttentionSetUpdates;
  private boolean ignoreFurtherAttentionSetUpdates;
  private Set<String> hashtags;
  private TreeMap<String, String> customKeyedValues = new TreeMap<>();
  private String changeMessage;
  private String tag;
  private PatchSetState psState;
  private Iterable<String> groups;
  private String pushCert;
  private boolean isAllowWriteToNewtRef;
  private String psDescription;
  private boolean currentPatchSet;
  private Boolean isPrivate;
  private Boolean workInProgress;
  private Integer revertOf;
  // If null, the update does not modify the field. Otherwise, it updates the field with the
  // new value or resets if cherryPickOf == Optional.empty().
  private Optional<String> cherryPickOf;

  private ChangeDraftUpdate draftUpdate;
  private RobotCommentUpdate robotCommentUpdate;
  private DeleteCommentRewriter deleteCommentRewriter;
  private DeleteChangeMessageRewriter deleteChangeMessageRewriter;
  private List<SubmitRequirementResult> submitRequirementResults;

  private ImmutableList.Builder<AttentionSetUpdate> attentionSetUpdatesBuilder =
      ImmutableList.builder();

  @SuppressWarnings("UnusedMethod")
  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.ChangeDraftUpdateFactory draftUpdateFactory,
      RobotCommentUpdate.Factory robotCommentUpdateFactory,
      DeleteCommentRewriter.Factory deleteCommentRewriterFactory,
      ProjectCache projectCache,
      ServiceUserClassifier serviceUserClassifier,
      PatchSetApprovalUuidGenerator patchSetApprovalUuidGenerator,
      @Assisted ChangeNotes notes,
      @Assisted CurrentUser user,
      @Assisted Instant when,
      ChangeNoteUtil noteUtil) {
    this(
        serverIdent,
        updateManagerFactory,
        draftUpdateFactory,
        robotCommentUpdateFactory,
        deleteCommentRewriterFactory,
        serviceUserClassifier,
        patchSetApprovalUuidGenerator,
        notes,
        user,
        when,
        projectCache
            .get(notes.getProjectName())
            .orElseThrow(illegalState(notes.getProjectName()))
            .getLabelTypes()
            .nameComparator(),
        noteUtil);
  }

  private static Table<String, Account.Id, Optional<PatchSetApproval>> approvals(
      Comparator<String> nameComparator) {
    return TreeBasedTable.create(nameComparator, naturalOrder());
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.ChangeDraftUpdateFactory draftUpdateFactory,
      RobotCommentUpdate.Factory robotCommentUpdateFactory,
      DeleteCommentRewriter.Factory deleteCommentRewriterFactory,
      ServiceUserClassifier serviceUserClassifier,
      PatchSetApprovalUuidGenerator patchSetApprovalUuidGenerator,
      @Assisted ChangeNotes notes,
      @Assisted CurrentUser user,
      @Assisted Instant when,
      @Assisted Comparator<String> labelNameComparator,
      ChangeNoteUtil noteUtil) {
    super(notes, user, serverIdent, noteUtil, when);
    this.updateManagerFactory = updateManagerFactory;
    this.draftUpdateFactory = draftUpdateFactory;
    this.robotCommentUpdateFactory = robotCommentUpdateFactory;
    this.deleteCommentRewriterFactory = deleteCommentRewriterFactory;
    this.serviceUserClassifier = serviceUserClassifier;
    this.patchSetApprovalUuidGenerator = patchSetApprovalUuidGenerator;
    this.approvals = approvals(labelNameComparator);
  }

  public ObjectId commit() throws IOException {
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (NoteDbUpdateManager updateManager = updateManagerFactory.create(getProjectName())) {
        updateManager.add(this);
        updateManager.execute();
      }
    }
    return getResult();
  }

  public void setChangeId(String changeId) {
    String old = getChange().getKey().get();
    checkArgument(
        old.equals(changeId),
        "The Change-Id was already set to %s, so we cannot set this Change-Id: %s",
        old,
        changeId);
    this.changeId = changeId;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public void setStatus(Change.Status status) {
    checkArgument(status != Change.Status.MERGED, "use merge(RequestId, Iterable<SubmitRecord>)");
    this.status = status;
  }

  public void fixStatusToMerged(SubmissionId submissionId) {
    checkArgument(submissionId != null, "submission id must be set for merged changes");
    this.status = Change.Status.MERGED;
    this.submissionId = submissionId.toString();
  }

  public void putApproval(String label, short value) {
    putApprovalFor(getAccountId(), label, value);
  }

  public void putApprovalFor(Account.Id reviewer, String label, short value) {
    PatchSetApproval psa =
        PatchSetApproval.builder()
            .key(PatchSetApproval.key(getPatchSetId(), reviewer, LabelId.create(label)))
            .value(value)
            .granted(when)
            .uuid(patchSetApprovalUuidGenerator.get(getPatchSetId(), reviewer, label, value, when))
            .build();
    approvals.put(label, reviewer, Optional.of(psa));
  }

  public ImmutableTable<String, Account.Id, Optional<PatchSetApproval>> getApprovals() {
    return ImmutableTable.copyOf(approvals);
  }

  void removeApproval(String label) {
    removeApprovalFor(getAccountId(), label);
  }

  public void removeApprovalFor(Account.Id reviewer, String label) {
    approvals.put(label, reviewer, Optional.empty());
  }

  /**
   * We expect the {@code copied} flag of {@code copiedPatchSetApproval} to be set, since this
   * method is only meant for copied approvals.
   */
  public void putCopiedApproval(PatchSetApproval copiedPatchSetApproval) {
    checkArgument(copiedPatchSetApproval.copied(), "Approval that should be copied is not copied.");
    copiedApprovals.add(copiedPatchSetApproval);
  }

  public void removeCopiedApprovalFor(
      @Nullable Account.Id realUserId, Account.Id reviewerId, String label) {
    PatchSetApproval.Builder psaBuilder =
        PatchSetApproval.builder()
            .copied(true)
            .key(PatchSetApproval.key(getPatchSetId(), reviewerId, LabelId.create(label)))
            .value(0)
            .uuid(Optional.empty())
            .granted(when);

    if (realUserId != null) {
      psaBuilder.realAccountId(realUserId);
    }

    copiedApprovals.add(psaBuilder.build());
  }

  public void merge(SubmissionId submissionId, Iterable<SubmitRecord> submitRecords) {
    this.status = Change.Status.MERGED;
    this.submissionId = submissionId.toString();
    this.submitRecords = ImmutableList.copyOf(submitRecords);
    checkArgument(!this.submitRecords.isEmpty(), "no submit records specified at submit time");
  }

  public void setSubjectForCommit(String commitSubject) {
    this.commitSubject = commitSubject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  @VisibleForTesting
  ObjectId getCommit() {
    return ObjectId.fromString(commit);
  }

  public void setChangeMessage(String changeMessage) {
    this.changeMessage = changeMessage;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public void setPsDescription(String psDescription) {
    this.psDescription = psDescription;
  }

  public void putSubmitRequirementResults(Collection<SubmitRequirementResult> rs) {
    if (submitRequirementResults == null) {
      submitRequirementResults = new ArrayList<>();
    }
    submitRequirementResults.addAll(rs);
  }

  public void putComment(Comment.Status status, HumanComment c) {
    verifyComment(c);
    createDraftUpdateIfNull();
    if (status == HumanComment.Status.DRAFT) {
      draftUpdate.putDraftComment(c);
    } else {
      comments.add(c);
      draftUpdate.markDraftCommentAsPublished(c);
    }
  }

  public void putRobotComment(RobotComment c) {
    verifyComment(c);
    createRobotCommentUpdateIfNull();
    robotCommentUpdate.putComment(c);
  }

  public void deleteComment(HumanComment c) {
    verifyComment(c);
    createDraftUpdateIfNull().deleteComment(c);
  }

  public void deleteCommentByRewritingHistory(String uuid, String newMessage) {
    deleteCommentRewriter =
        deleteCommentRewriterFactory.create(getChange().getId(), uuid, newMessage);
  }

  public void deleteChangeMessageByRewritingHistory(String targetMessageId, String newMessage) {
    deleteChangeMessageRewriter =
        new DeleteChangeMessageRewriter(getChange().getId(), targetMessageId, newMessage);
  }

  @VisibleForTesting
  ChangeDraftUpdate createDraftUpdateIfNull() {
    if (draftUpdate == null) {
      ChangeNotes notes = getNotes();
      if (notes != null) {
        draftUpdate = draftUpdateFactory.create(notes, accountId, realAccountId, authorIdent, when);
      } else {
        // tests will always take the notes != null path above.
        draftUpdate =
            draftUpdateFactory.create(getChange(), accountId, realAccountId, authorIdent, when);
      }
    }
    return draftUpdate;
  }

  private void createRobotCommentUpdateIfNull() {
    if (robotCommentUpdate == null) {
      ChangeNotes notes = getNotes();
      if (notes != null) {
        robotCommentUpdate =
            robotCommentUpdateFactory.create(notes, accountId, realAccountId, authorIdent, when);
      } else {
        robotCommentUpdate =
            robotCommentUpdateFactory.create(
                getChange(), accountId, realAccountId, authorIdent, when);
      }
    }
  }

  public void setTopic(String topic) throws ValidationException {

    if (isIllegalTopic(topic)) {
      throw new ValidationException("topic can't contain quotation marks.");
    }
    this.topic = Strings.nullToEmpty(topic);
  }

  public void setCommit(RevWalk rw, ObjectId id) throws IOException {
    setCommit(rw, id, null);
  }

  public void setCommit(RevWalk rw, ObjectId id, String pushCert) throws IOException {
    RevCommit commit = rw.parseCommit(id);
    rw.parseBody(commit);
    this.commit = commit.name();
    subject = commit.getShortMessage();
    this.pushCert = pushCert;
  }

  public void setHashtags(Set<String> hashtags) {
    this.hashtags = hashtags;
  }

  public void addCustomKeyedValue(String key, String value) throws ValidationException {
    if (key.length() > MAX_CUSTOM_KEY_LENGTH) {
      throw new ValidationException("Custom Key is too long.");
    }
    if (value.length() > MAX_CUSTOM_KEYED_VALUE_LENGTH) {
      throw new ValidationException("Custom Keyed value is too long.");
    }
    this.customKeyedValues.put(key, value);
  }

  public void deleteCustomKeyedValue(String key) throws ValidationException {
    if (key.length() > MAX_CUSTOM_KEY_LENGTH) {
      throw new ValidationException("Custom Key is too long.");
    }
    this.customKeyedValues.put(key, "");
  }

  /**
   * Adds attention set updates that should be stored in NoteDb.
   *
   * <p>If invoked multiple times with attention set updates for the same user, only the attention
   * set update of the first invocation is stored for this user and further attention set updates
   * for this user are silently ignored. This means if callers invoke this method multiple times
   * with attention set updates for the same user, they must ensure that the first call is being
   * done with the attention set update that should take precedence.
   *
   * @param updates Attention set updates that should be performed. The updates must not have any
   *     timestamp set ({@link AttentionSetUpdate#timestamp()} must return {@code null}). This is
   *     because the timestamp of all performed updates is always the timestamp of when the NoteDb
   *     commit is created. Each of the provided updates must be for a different user, if there are
   *     multiple updates for the same user the update is rejected.
   * @throws IllegalArgumentException thrown if any of the provided updates has a timestamp set, or
   *     if the provided set of updates contains multiple updates for the same user
   */
  public void addToPlannedAttentionSetUpdates(Set<AttentionSetUpdate> updates) {
    if (updates == null || updates.isEmpty() || ignoreFurtherAttentionSetUpdates) {
      // No updates to do. Robots don't change attention set.
      return;
    }
    checkArgument(
        updates.stream().noneMatch(a -> a.timestamp() != null),
        "must not specify timestamp for write");

    checkArgument(
        updates.stream().map(AttentionSetUpdate::account).distinct().count() == updates.size(),
        "must not specify multiple updates for single user");

    if (plannedAttentionSetUpdates == null) {
      plannedAttentionSetUpdates = new HashMap<>();
    }

    Set<Account.Id> currentAccountUpdates =
        plannedAttentionSetUpdates.values().stream()
            .map(AttentionSetUpdate::account)
            .collect(Collectors.toSet());
    updates.stream()
        .filter(u -> !currentAccountUpdates.contains(u.account()))
        .forEach(u -> plannedAttentionSetUpdates.putIfAbsent(u.account(), u));
  }

  public void addToPlannedAttentionSetUpdates(AttentionSetUpdate update) {
    addToPlannedAttentionSetUpdates(ImmutableSet.of(update));
  }

  public ImmutableList<AttentionSetUpdate> getAttentionSetUpdates() {
    return attentionSetUpdatesBuilder.build();
  }

  public Map<Account.Id, ReviewerStateInternal> getReviewers() {
    return reviewers;
  }

  public void putReviewer(Account.Id reviewer, ReviewerStateInternal type) {
    checkArgument(type != ReviewerStateInternal.REMOVED, "invalid ReviewerType");
    reviewers.put(reviewer, type);
  }

  public void removeReviewer(Account.Id reviewer) {
    reviewers.put(reviewer, ReviewerStateInternal.REMOVED);
  }

  public void putReviewerByEmail(Address reviewer, ReviewerStateInternal type) {
    checkArgument(type != ReviewerStateInternal.REMOVED, "invalid ReviewerType");
    reviewersByEmail.put(reviewer, type);
  }

  public void removeReviewerByEmail(Address reviewer) {
    reviewersByEmail.put(reviewer, ReviewerStateInternal.REMOVED);
  }

  public void setPatchSetState(PatchSetState psState) {
    this.psState = psState;
  }

  public void setCurrentPatchSet() {
    this.currentPatchSet = true;
  }

  public void setGroups(List<String> groups) {
    requireNonNull(groups, "groups may not be null");
    this.groups = groups;
  }

  public void setRevertOf(int revertOf) {
    int ownId = getId().get();
    checkArgument(ownId != revertOf, "A change cannot revert itself");
    this.revertOf = revertOf;
    rootOnly = true;
  }

  public void setCherryPickOf(String cherryPickOf) {
    checkArgument(cherryPickOf != null, "use resetCherryPickOf");
    this.cherryPickOf = Optional.of(cherryPickOf);
  }

  public void resetCherryPickOf() {
    this.cherryPickOf = Optional.empty();
  }

  /** Returns the tree id for the updated tree */
  @Nullable
  private ObjectId storeRevisionNotes(RevWalk rw, ObjectInserter inserter, ObjectId curr)
      throws ConfigInvalidException, IOException {
    if (submitRequirementResults == null && comments.isEmpty() && pushCert == null) {
      return null;
    }
    RevisionNoteMap<ChangeRevisionNote> rnm = getRevisionNoteMap(rw, curr);

    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);
    for (HumanComment c : comments) {
      c.tag = tag;
      cache.get(c.getCommitId()).putComment(c);
    }
    if (submitRequirementResults != null) {
      if (submitRequirementResults.isEmpty()) {
        ObjectId latestPsCommitId =
            Iterables.getLast(getNotes().getPatchSets().values()).commitId();
        cache.get(latestPsCommitId).createEmptySubmitRequirementResults();
      } else {
        // Clear any previously stored SRs first. The SRs in this update will overwrite any
        // previously stored SRs (e.g. if the change is abandoned (SRs stored) -> un-abandoned ->
        // merged).
        submitRequirementResults.stream()
            .map(SubmitRequirementResult::patchSetCommitId)
            .distinct()
            .forEach(commit -> cache.get(commit).clearSubmitRequirementResults());
        for (SubmitRequirementResult sr : submitRequirementResults) {
          cache.get(sr.patchSetCommitId()).putSubmitRequirementResult(sr);
        }
      }
    }
    if (pushCert != null) {
      checkState(commit != null);
      cache.get(ObjectId.fromString(commit)).setPushCertificate(pushCert);
    }
    Map<ObjectId, RevisionNoteBuilder> builders = cache.getBuilders();
    checkComments(rnm.revisionNotes, builders);

    for (Map.Entry<ObjectId, RevisionNoteBuilder> e : builders.entrySet()) {
      ObjectId data = inserter.insert(OBJ_BLOB, e.getValue().build(noteUtil.getChangeNoteJson()));
      rnm.noteMap.set(e.getKey(), data);
    }

    return rnm.noteMap.writeTree(inserter);
  }

  private RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, IOException {
    if (curr.equals(ObjectId.zeroId())) {
      return RevisionNoteMap.emptyMap();
    }
    // The old ChangeNotes may have already parsed the revision notes. We can reuse them as long as
    // the ref hasn't advanced.
    ChangeNotes notes = getNotes();
    if (notes != null && notes.revisionNoteMap != null) {
      ObjectId idFromNotes = firstNonNull(notes.load().getRevision(), ObjectId.zeroId());
      if (idFromNotes.equals(curr)) {
        return notes.revisionNoteMap;
      }
    }
    NoteMap noteMap = NoteMap.read(rw.getObjectReader(), rw.parseCommit(curr));
    // Even though reading from changes might not be enabled, we need to
    // parse any existing revision notes so we can merge them.
    return RevisionNoteMap.parse(
        noteUtil.getChangeNoteJson(), rw.getObjectReader(), noteMap, HumanComment.Status.PUBLISHED);
  }

  private void checkComments(
      Map<ObjectId, ChangeRevisionNote> existingNotes,
      Map<ObjectId, RevisionNoteBuilder> toUpdate) {
    // Prohibit various kinds of illegal operations on comments.
    Set<Comment.Key> existing = new HashSet<>();
    List<Comment> draftsToFix = new ArrayList();
    for (ChangeRevisionNote rn : existingNotes.values()) {
      for (Comment c : rn.getEntities()) {
        existing.add(c.key);
        draftsToFix.add(c);
      }
    }
    if (draftUpdate != null) {
      // Take advantage of an existing update on All-Users to prune any
      // published comments from drafts. NoteDbUpdateManager takes care of
      // ensuring that this update is applied before its dependent draft
      // update.
      //
      // Deleting aggressively in this way, combined with filtering out
      // duplicate published/draft comments in ChangeNotes#getDraftComments,
      // makes up for the fact that updates between the change repo and
      // All-Users are not atomic.
      //
      // TODO(dborowitz): We might want to distinguish between deleted
      // drafts that we're fixing up after the fact by putting them in a
      // separate commit. But note that we don't care much about the commit
      // graph of the draft ref, particularly because the ref is completely
      // deleted when all drafts are gone.
      draftUpdate.markAllDraftCommentsAsFixed(draftsToFix);
    }

    for (RevisionNoteBuilder b : toUpdate.values()) {
      for (Comment c : b.put.values()) {
        if (existing.contains(c.key)) {
          throw new StorageException("Cannot update existing published comment: " + c);
        }
      }
    }
  }

  @Override
  protected String getRefName() {
    return changeMetaRef(getId());
  }

  @Override
  protected boolean bypassMaxUpdates() {
    return isAbandonChange() || isAttentionSetChangeOnly();
  }

  private boolean isAbandonChange() {
    return status != null && status.isClosed();
  }

  private boolean isAttentionSetChangeOnly() {
    return (plannedAttentionSetUpdates != null
        && plannedAttentionSetUpdates.size() > 0
        && doesNotHaveChangesAffectingAttentionSet());
  }

  private boolean doesNotHaveChangesAffectingAttentionSet() {
    return comments.isEmpty()
        && reviewers.isEmpty()
        && reviewersByEmail.isEmpty()
        && approvals.isEmpty()
        && workInProgress == null;
  }

  @Override
  protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws IOException {
    checkState(
        deleteCommentRewriter == null && deleteChangeMessageRewriter == null,
        "cannot update and rewrite ref in one BatchUpdate");

    PatchSet.Id patchSetId = psId != null ? psId : getChange().currentPatchSetId();
    StringBuilder msg = new StringBuilder();
    if (commitSubject != null) {
      msg.append(commitSubject);
    } else {
      msg.append("Update patch set ").append(patchSetId.get());
    }
    msg.append("\n\n");

    if (changeMessage != null) {
      msg.append(changeMessage);
      msg.append("\n\n");
    }

    addPatchSetFooter(msg, patchSetId);

    if (currentPatchSet) {
      addFooter(msg, FOOTER_CURRENT, Boolean.TRUE);
    }

    if (psDescription != null) {
      addFooter(msg, FOOTER_PATCH_SET_DESCRIPTION, psDescription);
    }

    if (changeId != null) {
      addFooter(msg, FOOTER_CHANGE_ID, changeId);
    }

    if (subject != null) {
      addFooter(msg, FOOTER_SUBJECT, subject);
    }

    if (branch != null) {
      addFooter(msg, FOOTER_BRANCH, branch);
    }

    if (status != null) {
      addFooter(msg, FOOTER_STATUS, status.name().toLowerCase(Locale.US));
      if (status.equals(Change.Status.ABANDONED)) {
        clearAttentionSet("Change was abandoned");
      }
      if (status.equals(Change.Status.MERGED)) {
        clearAttentionSet("Change was submitted");
      }
    }

    if (topic != null) {
      addFooter(msg, FOOTER_TOPIC, topic);
    }

    if (commit != null) {
      addFooter(msg, FOOTER_COMMIT, commit);
    }

    Joiner comma = Joiner.on(',');
    if (hashtags != null) {
      addFooter(msg, FOOTER_HASHTAGS, comma.join(hashtags));
    }

    for (Map.Entry<String, String> entry : customKeyedValues.entrySet()) {
      addFooter(msg, FOOTER_CUSTOM_KEYED_VALUE, entry.getKey() + "=" + entry.getValue());
    }

    if (tag != null) {
      addFooter(msg, FOOTER_TAG, tag);
    }

    if (groups != null) {
      addFooter(msg, FOOTER_GROUPS, comma.join(groups));
    }

    for (Map.Entry<Account.Id, ReviewerStateInternal> e : reviewers.entrySet()) {
      addFooter(msg, e.getValue().getFooterKey());
      noteUtil.appendAccountIdIdentString(msg, e.getKey()).append('\n');
    }

    applyReviewerUpdatesToAttentionSet();

    for (Map.Entry<Address, ReviewerStateInternal> e : reviewersByEmail.entrySet()) {
      addFooter(msg, e.getValue().getByEmailFooterKey(), e.getKey().toString());
    }

    for (Table.Cell<String, Account.Id, Optional<PatchSetApproval>> c : approvals.cellSet()) {
      addLabelFooter(msg, c);
    }
    for (PatchSetApproval patchSetApproval : copiedApprovals) {
      addCopiedLabelFooter(msg, patchSetApproval);
    }

    if (submissionId != null) {
      addFooter(msg, FOOTER_SUBMISSION_ID, submissionId);
    }

    if (submitRecords != null) {
      for (SubmitRecord rec : submitRecords) {
        addFooter(msg, FOOTER_SUBMITTED_WITH).append(rec.status);
        if (rec.errorMessage != null) {
          msg.append(' ').append(sanitizeFooter(rec.errorMessage));
        }
        msg.append('\n');
        if (rec.ruleName != null) {
          addFooter(msg, FOOTER_SUBMITTED_WITH).append("Rule-Name: ").append(rec.ruleName);
          msg.append('\n');
        }
        if (rec.labels != null) {
          for (SubmitRecord.Label label : rec.labels) {
            // Label names/values are safe to append without sanitizing.
            addFooter(msg, FOOTER_SUBMITTED_WITH)
                .append(label.status)
                .append(": ")
                .append(label.label);
            if (label.appliedBy != null) {
              msg.append(": ");
              noteUtil.appendAccountIdIdentString(msg, label.appliedBy);
            }
            msg.append('\n');
          }
        }
      }
    }

    if (!Objects.equals(accountId, realAccountId)) {
      addFooter(msg, FOOTER_REAL_USER);
      noteUtil.appendAccountIdIdentString(msg, realAccountId).append('\n');
    }

    if (isPrivate != null) {
      addFooter(msg, FOOTER_PRIVATE, isPrivate);
    }

    if (workInProgress != null) {
      addFooter(msg, FOOTER_WORK_IN_PROGRESS, workInProgress);
      if (workInProgress) {
        clearAttentionSet("Change was marked work in progress");
      } else {
        addAllReviewersToAttentionSet();
      }
    }

    if (revertOf != null) {
      addFooter(msg, FOOTER_REVERT_OF, revertOf);
    }

    if (cherryPickOf != null) {
      if (cherryPickOf.isPresent()) {
        addFooter(msg, FOOTER_CHERRY_PICK_OF, cherryPickOf.get());
      } else {
        // Update cherryPickOf with an empty value.
        addFooter(msg, FOOTER_CHERRY_PICK_OF).append('\n');
      }
    }

    boolean hasAttentionSeUpdates = updateAttentionSet(msg);
    if (isEmptyWithoutAttentionSet() && !hasAttentionSeUpdates) {
      return NO_OP_UPDATE;
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setMessage(msg.toString());
    try {
      ObjectId treeId = storeRevisionNotes(rw, ins, curr);
      if (treeId != null) {
        cb.setTreeId(treeId);
      }
    } catch (ConfigInvalidException e) {
      throw new StorageException(e);
    }
    return cb;
  }

  private void addLabelFooter(
      StringBuilder msg, Cell<String, Account.Id, Optional<PatchSetApproval>> c) {
    addFooter(msg, FOOTER_LABEL);
    String label = c.getRowKey();
    Account.Id reviewerId = c.getColumnKey();
    // Label names/values are safe to append without sanitizing.
    boolean isRemoval = !c.getValue().isPresent();
    if (isRemoval) {
      msg.append('-').append(label);
      // Since vote removals do not need to be referenced, e.g. by the copy approvals, they do not
      // require a UUID.
    } else {
      short value = c.getValue().get().value();
      msg.append(LabelVote.create(label, value).formatWithEquals());
      msg.append(", ");
      msg.append(c.getValue().get().uuid().get());
    }
    if (!reviewerId.equals(getAccountId())) {
      noteUtil.appendAccountIdIdentString(msg.append(' '), reviewerId);
    }
    msg.append('\n');
  }

  private void addCopiedLabelFooter(StringBuilder msg, PatchSetApproval patchSetApproval) {
    if (patchSetApproval.value() == 0) {
      addFooter(msg, FOOTER_COPIED_LABEL);

      // Mark the copied approval as deleted.
      msg.append('-').append(patchSetApproval.label());

      noteUtil.appendAccountIdIdentString(msg.append(' '), patchSetApproval.accountId());

      // In the non-copied labels, we don't need to pass the real account id since it's already
      // in FOOTER_REAL_USER. Here, we want to retain the original real account id.
      if (!patchSetApproval.realAccountId().equals(patchSetApproval.accountId())) {
        noteUtil.appendAccountIdIdentString(msg.append(","), patchSetApproval.realAccountId());
      }

      msg.append('\n');
      return;
    }
    addFooter(msg, FOOTER_COPIED_LABEL);
    // Label names/values are safe to append without sanitizing.
    msg.append(
        LabelVote.create(patchSetApproval.label(), patchSetApproval.value()).formatWithEquals());
    // Might be copied from the vote that was generated before UUID was introduced.
    if (patchSetApproval.uuid().isPresent()) {
      msg.append(", ");
      msg.append(patchSetApproval.uuid().get());
    }
    Account.Id id = patchSetApproval.accountId();
    noteUtil.appendAccountIdIdentString(msg.append(' '), id);

    // In the non-copied labels, we don't need to pass the real account id since it's already
    // in FOOTER_REAL_USER. Here, we want to retain the original real account id.
    if (!patchSetApproval.realAccountId().equals(patchSetApproval.accountId())) {
      noteUtil.appendAccountIdIdentString(msg.append(","), patchSetApproval.realAccountId());
    }

    // In the non-copied labels, we don't need to pass the tag since it's already in
    // FOOTER_TAG, but in this chase we want to retain the original tag, and not the current tag.
    if (patchSetApproval.tag().isPresent()) {
      msg.append(":\"" + sanitizeFooter(patchSetApproval.tag().get()) + "\"");
    }

    msg.append('\n');
  }

  private void clearAttentionSet(String reason) {
    if (getNotes().getAttentionSet() == null) {
      return;
    }
    AttentionSetUtil.additionsOnly(getNotes().getAttentionSet()).stream()
        .map(
            a ->
                AttentionSetUpdate.createForWrite(
                    a.account(), AttentionSetUpdate.Operation.REMOVE, reason))
        .forEach(this::addToPlannedAttentionSetUpdates);
  }

  private void applyReviewerUpdatesToAttentionSet() {
    if ((workInProgress != null && workInProgress == true)
        || getNotes().getChange().isWorkInProgress()
        || status == Change.Status.MERGED) {
      // Attention set shouldn't change here for changes that are work in progress or are about to
      // be submitted or when the caller is a robot.
      return;
    }

    Set<AttentionSetUpdate> updates = new HashSet<>();
    Set<Account.Id> currentReviewers =
        getNotes().getReviewers().byState(ReviewerStateInternal.REVIEWER);
    for (Map.Entry<Account.Id, ReviewerStateInternal> reviewer : reviewers.entrySet()) {
      Account.Id reviewerId = reviewer.getKey();

      ReviewerStateInternal reviewerState = reviewer.getValue();
      // Only add new reviewers to the attention set. Also, don't add the owner because the owner
      // can only be a "dummy" reviewer for legacy reasons.
      if (reviewerState.equals(ReviewerStateInternal.REVIEWER)
          && !currentReviewers.contains(reviewerId)
          && !reviewerId.equals(getChange().getOwner())) {
        updates.add(
            AttentionSetUpdate.createForWrite(
                reviewerId, AttentionSetUpdate.Operation.ADD, "Reviewer was added"));
      }
      boolean reviewerRemoved =
          !reviewerState.equals(ReviewerStateInternal.REVIEWER)
              && currentReviewers.contains(reviewerId);
      boolean ccRemoved = reviewerState.equals(ReviewerStateInternal.REMOVED);
      if (reviewerRemoved || ccRemoved) {
        updates.add(
            AttentionSetUpdate.createForWrite(
                reviewerId, AttentionSetUpdate.Operation.REMOVE, "Reviewer/Cc was removed"));
      }
    }
    addToPlannedAttentionSetUpdates(updates);
  }

  private void addAllReviewersToAttentionSet() {
    getNotes().getReviewers().byState(ReviewerStateInternal.REVIEWER).stream()
        .map(
            r ->
                AttentionSetUpdate.createForWrite(
                    r, AttentionSetUpdate.Operation.ADD, "Change was marked ready for review"))
        .forEach(this::addToPlannedAttentionSetUpdates);
  }

  /**
   * Any updates to the attention set must be done in {@link #addToPlannedAttentionSetUpdates}. This
   * method is called after all the updates are finished to do the updates once and for real.
   *
   * <p>Changing the behaviour of this method might affect the way a ChangeUpdate is considered to
   * be an "Attention Set Change Only". Make sure the {@link #isAttentionSetChangeOnly} logic is
   * amended as well if needed.
   *
   * @return True if one or more attention set updates are appended to the {@code msg}, and false
   *     otherwise.
   */
  private boolean updateAttentionSet(StringBuilder msg) {
    if (plannedAttentionSetUpdates == null) {
      plannedAttentionSetUpdates = new HashMap<>();
    }
    Set<Account.Id> currentUsersInAttentionSet =
        AttentionSetUtil.additionsOnly(getNotes().getAttentionSet()).stream()
            .map(AttentionSetUpdate::account)
            .collect(Collectors.toSet());

    // Current reviewers/ccs are the reviewers/ccs before the update + the new reviewers/ccs - the
    // deleted reviewers/ccs.
    Set<Account.Id> currentReviewers =
        Stream.concat(
                getNotes().getReviewers().all().stream(),
                reviewers.entrySet().stream()
                    .filter(r -> r.getValue().asReviewerState() != ReviewerState.REMOVED)
                    .map(r -> r.getKey()))
            .collect(Collectors.toSet());
    currentReviewers.removeAll(
        reviewers.entrySet().stream()
            .filter(r -> r.getValue().asReviewerState() == ReviewerState.REMOVED)
            .map(r -> r.getKey())
            .collect(ImmutableSet.toImmutableSet()));

    removeInactiveUsersFromAttentionSet(currentReviewers);

    boolean hasUpdates = false;

    for (AttentionSetUpdate attentionSetUpdate : plannedAttentionSetUpdates.values()) {
      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.ADD
          && currentUsersInAttentionSet.contains(attentionSetUpdate.account())) {
        // Skip users that are already in the attention set: no need to re-add them.
        continue;
      }

      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.REMOVE
          && !currentUsersInAttentionSet.contains(attentionSetUpdate.account())) {
        // Skip users that are not in the attention set: no need to remove them.
        continue;
      }

      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.ADD
          && serviceUserClassifier.isServiceUser(attentionSetUpdate.account())) {
        // Skip adding robots to the attention set.
        continue;
      }

      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.ADD
          && approvals.rowKeySet().contains(LabelId.legacySubmit().get())) {
        // On submit, we sometimes can add the person who submitted the change as a reviewer, and in
        // turn it will add that person to the attention set.
        // This ensures we don't add users to the attention set on submit.
        continue;
      }

      // Don't add accounts that are not active in the change to the attention set.
      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.ADD
          && !isActiveOnChange(currentReviewers, attentionSetUpdate.account())) {
        continue;
      }

      addFooter(msg, FOOTER_ATTENTION, noteUtil.attentionSetUpdateToJson(attentionSetUpdate));
      attentionSetUpdatesBuilder.add(attentionSetUpdate);
      hasUpdates = true;
    }
    return hasUpdates;
  }

  private void removeInactiveUsersFromAttentionSet(Set<Account.Id> currentReviewers) {
    Set<Account.Id> inActiveUsersInTheAttentionSet =
        // get the current attention set.
        getNotes().getAttentionSet().stream()
            .filter(a -> a.operation().equals(Operation.ADD))
            .map(a -> a.account())
            // remove users that are currently being removed from the attention set.
            .filter(
                a ->
                    plannedAttentionSetUpdates.getOrDefault(a, /* defaultValue= */ null) == null
                        || plannedAttentionSetUpdates.get(a).operation().equals(Operation.REMOVE))
            // remove users that are still active on the change.
            .filter(a -> !isActiveOnChange(currentReviewers, a))
            .collect(ImmutableSet.toImmutableSet());

    // We override the flag, as we never want such users in the attention set.
    ignoreFurtherAttentionSetUpdates = false;

    addToPlannedAttentionSetUpdates(
        inActiveUsersInTheAttentionSet.stream()
            .map(
                a ->
                    AttentionSetUpdate.createForWrite(
                        a,
                        Operation.REMOVE,
                        /* reason= */ "Only change owner, uploader, reviewers, and cc can "
                            + "be in the attention set"))
            .collect(ImmutableSet.toImmutableSet()));

    ignoreFurtherAttentionSetUpdates = true;
  }

  /**
   * Returns whether {@code accountId} is active on a change based on the {@code currentReviewers}.
   * Activity is defined as being a part of the reviewers, an uploader, or an owner of a change.
   */
  private boolean isActiveOnChange(Set<Account.Id> currentReviewers, Account.Id accountId) {
    return currentReviewers.contains(accountId)
        || getChange().getOwner().equals(accountId)
        || getNotes().getCurrentPatchSet().uploader().equals(accountId);
  }

  /**
   * When set, default attention set rules are ignored (E.g, adding reviewers -> adds to attention
   * set, etc).
   */
  public void ignoreFurtherAttentionSetUpdates() {
    ignoreFurtherAttentionSetUpdates = true;
  }

  private void addPatchSetFooter(StringBuilder sb, PatchSet.Id ps) {
    addFooter(sb, FOOTER_PATCH_SET).append(ps.get());
    if (psState != null) {
      sb.append(" (").append(psState.name().toLowerCase(Locale.US)).append(')');
    }
    sb.append('\n');
  }

  @Override
  protected Project.NameKey getProjectName() {
    return getChange().getProject();
  }

  @Override
  public boolean isEmpty() {
    return isEmptyWithoutAttentionSet() && plannedAttentionSetUpdates == null;
  }

  private boolean isEmptyWithoutAttentionSet() {
    return commitSubject == null
        && approvals.isEmpty()
        && copiedApprovals.isEmpty()
        && changeMessage == null
        && comments.isEmpty()
        && reviewers.isEmpty()
        && reviewersByEmail.isEmpty()
        && changeId == null
        && branch == null
        && status == null
        && submissionId == null
        && submitRecords == null
        && hashtags == null
        && customKeyedValues.isEmpty()
        && topic == null
        && commit == null
        && psState == null
        && groups == null
        && tag == null
        && psDescription == null
        && !currentPatchSet
        && isPrivate == null
        && workInProgress == null
        && revertOf == null
        && cherryPickOf == null;
  }

  ChangeDraftUpdate getDraftUpdate() {
    return draftUpdate;
  }

  RobotCommentUpdate getRobotCommentUpdate() {
    return robotCommentUpdate;
  }

  DeleteCommentRewriter getDeleteCommentRewriter() {
    return deleteCommentRewriter;
  }

  DeleteChangeMessageRewriter getDeleteChangeMessageRewriter() {
    return deleteChangeMessageRewriter;
  }

  public void setAllowWriteToNewRef(boolean allow) {
    isAllowWriteToNewtRef = allow;
  }

  @Override
  public boolean allowWriteToNewRef() {
    return isAllowWriteToNewtRef;
  }

  public void setPrivate(boolean isPrivate) {
    this.isPrivate = isPrivate;
  }

  public void setWorkInProgress(boolean workInProgress) {
    this.workInProgress = workInProgress;
  }

  private static StringBuilder addFooter(StringBuilder sb, FooterKey footer) {
    return sb.append(footer.getName()).append(": ");
  }

  private static void addFooter(StringBuilder sb, FooterKey footer, Object... values) {
    addFooter(sb, footer);
    for (Object value : values) {
      sb.append(sanitizeFooter(Objects.toString(value)));
    }
    sb.append('\n');
  }

  private static boolean isIllegalTopic(String topic) {
    return (topic != null && topic.contains("\""));
  }
}
