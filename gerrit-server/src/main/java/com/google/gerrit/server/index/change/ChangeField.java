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

package com.google.gerrit.server.index.change;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.SchemaUtil;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gson.Gson;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.OrmException;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.revwalk.FooterLine;

/**
 * Fields indexed on change documents.
 *
 * <p>Each field corresponds to both a field name supported by {@link ChangeQueryBuilder} for
 * querying that field, and a method on {@link ChangeData} used for populating the corresponding
 * document fields in the secondary index.
 *
 * <p>Field names are all lowercase alphanumeric plus underscore; index implementations may create
 * unambiguous derived field names containing other characters.
 */
public class ChangeField {
  public static final int NO_ASSIGNEE = -1;

  private static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();

  /** Legacy change ID. */
  public static final FieldDef<ChangeData, Integer> LEGACY_ID =
      new FieldDef.Single<ChangeData, Integer>("legacy_id", FieldType.INTEGER, true) {
        @Override
        public Integer get(ChangeData input, FillArgs args) {
          return input.getId().get();
        }
      };

  /** Newer style Change-Id key. */
  public static final FieldDef<ChangeData, String> ID =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_CHANGE_ID, FieldType.PREFIX, false) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getKey().get();
        }
      };

  /** Change status string, in the same format as {@code status:}. */
  public static final FieldDef<ChangeData, String> STATUS =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_STATUS, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return ChangeStatusPredicate.canonicalize(c.getStatus());
        }
      };

  /** Project containing the change. */
  public static final FieldDef<ChangeData, String> PROJECT =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_PROJECT, FieldType.EXACT, true) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getProject().get();
        }
      };

  /** Project containing the change, as a prefix field. */
  public static final FieldDef<ChangeData, String> PROJECTS =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_PROJECTS, FieldType.PREFIX, false) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getProject().get();
        }
      };

  /** Reference (aka branch) the change will submit onto. */
  public static final FieldDef<ChangeData, String> REF =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_REF, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getDest().get();
        }
      };

  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> EXACT_TOPIC =
      new FieldDef.Single<ChangeData, String>("topic4", FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          return getTopic(input);
        }
      };

  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> FUZZY_TOPIC =
      new FieldDef.Single<ChangeData, String>("topic5", FieldType.FULL_TEXT, false) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          return getTopic(input);
        }
      };

  /** Submission id assigned by MergeOp. */
  public static final FieldDef<ChangeData, String> SUBMISSIONID =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_SUBMISSIONID, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getSubmissionId();
        }
      };

  /** Last update time since January 1, 1970. */
  public static final FieldDef<ChangeData, Timestamp> UPDATED =
      new FieldDef.Single<ChangeData, Timestamp>("updated2", FieldType.TIMESTAMP, true) {
        @Override
        public Timestamp get(ChangeData input, FillArgs args) throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getLastUpdatedOn();
        }
      };

  /** List of full file paths modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> PATH =
      new FieldDef.Repeatable<ChangeData, String>(
          // Named for backwards compatibility.
          ChangeQueryBuilder.FIELD_FILE, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return firstNonNull(input.currentFilePaths(), ImmutableList.<String>of());
        }
      };

  public static Set<String> getFileParts(ChangeData cd) throws OrmException {
    List<String> paths = cd.currentFilePaths();
    if (paths == null) {
      return ImmutableSet.of();
    }
    Splitter s = Splitter.on('/').omitEmptyStrings();
    Set<String> r = new HashSet<>();
    for (String path : paths) {
      for (String part : s.split(path)) {
        r.add(part);
      }
    }
    return r;
  }

  /** Hashtags tied to a change */
  public static final FieldDef<ChangeData, Iterable<String>> HASHTAG =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_HASHTAG, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return input.hashtags().stream().map(String::toLowerCase).collect(toSet());
        }
      };

  /** Hashtags with original case. */
  public static final FieldDef<ChangeData, Iterable<byte[]>> HASHTAG_CASE_AWARE =
      new FieldDef.Repeatable<ChangeData, byte[]>("_hashtag", FieldType.STORED_ONLY, true) {
        @Override
        public Iterable<byte[]> get(ChangeData input, FillArgs args) throws OrmException {
          return input.hashtags().stream().map(t -> t.getBytes(UTF_8)).collect(toSet());
        }
      };

  /** Components of each file path modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> FILE_PART =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_FILEPART, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return getFileParts(input);
        }
      };

  /** Owner/creator of the change. */
  public static final FieldDef<ChangeData, Integer> OWNER =
      new FieldDef.Single<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_OWNER, FieldType.INTEGER, false) {
        @Override
        public Integer get(ChangeData input, FillArgs args) throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getOwner().get();
        }
      };

  /** The user assigned to the change. */
  public static final FieldDef<ChangeData, Integer> ASSIGNEE =
      new FieldDef.Single<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_ASSIGNEE, FieldType.INTEGER, false) {
        @Override
        public Integer get(ChangeData input, FillArgs args) throws OrmException {
          Account.Id id = input.change().getAssignee();
          return id != null ? id.get() : NO_ASSIGNEE;
        }
      };

  /** Reviewer(s) associated with the change. */
  public static final FieldDef<ChangeData, Iterable<String>> REVIEWER =
      new FieldDef.Repeatable<ChangeData, String>("reviewer2", FieldType.EXACT, true) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return getReviewerFieldValues(input.reviewers());
        }
      };

  @VisibleForTesting
  static List<String> getReviewerFieldValues(ReviewerSet reviewers) {
    List<String> r = new ArrayList<>(reviewers.asTable().size() * 2);
    for (Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> c :
        reviewers.asTable().cellSet()) {
      String v = getReviewerFieldValue(c.getRowKey(), c.getColumnKey());
      r.add(v);
      r.add(v + ',' + c.getValue().getTime());
    }
    return r;
  }

  public static String getReviewerFieldValue(ReviewerStateInternal state, Account.Id id) {
    return state.toString() + ',' + id;
  }

  public static ReviewerSet parseReviewerFieldValues(Iterable<String> values) {
    ImmutableTable.Builder<ReviewerStateInternal, Account.Id, Timestamp> b =
        ImmutableTable.builder();
    for (String v : values) {
      int f = v.indexOf(',');
      if (f < 0) {
        continue;
      }
      int l = v.lastIndexOf(',');
      if (l == f) {
        continue;
      }
      b.put(
          ReviewerStateInternal.valueOf(v.substring(0, f)),
          Account.Id.parse(v.substring(f + 1, l)),
          new Timestamp(Long.valueOf(v.substring(l + 1, v.length()))));
    }
    return ReviewerSet.fromTable(b.build());
  }

  /** Commit ID of any patch set on the change, using prefix match. */
  public static final FieldDef<ChangeData, Iterable<String>> COMMIT =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_COMMIT, FieldType.PREFIX, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return getRevisions(input);
        }
      };

  /** Commit ID of any patch set on the change, using exact match. */
  public static final FieldDef<ChangeData, Iterable<String>> EXACT_COMMIT =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_EXACTCOMMIT, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return getRevisions(input);
        }
      };

  private static Set<String> getRevisions(ChangeData cd) throws OrmException {
    Set<String> revisions = new HashSet<>();
    for (PatchSet ps : cd.patchSets()) {
      if (ps.getRevision() != null) {
        revisions.add(ps.getRevision().get());
      }
    }
    return revisions;
  }

  /** Tracking id extracted from a footer. */
  public static final FieldDef<ChangeData, Iterable<String>> TR =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_TR, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          try {
            List<FooterLine> footers = input.commitFooters();
            if (footers == null) {
              return ImmutableSet.of();
            }
            return Sets.newHashSet(args.trackingFooters.extract(footers).values());
          } catch (IOException e) {
            throw new OrmException(e);
          }
        }
      };

  /** List of labels on the current patch set. */
  @Deprecated
  public static final FieldDef<ChangeData, Iterable<String>> LABEL =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_LABEL, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return getLabels(input, false);
        }
      };

  /** List of labels on the current patch set including change owner votes. */
  public static final FieldDef<ChangeData, Iterable<String>> LABEL2 =
      new FieldDef.Repeatable<ChangeData, String>("label2", FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return getLabels(input, true);
        }
      };

  private static Iterable<String> getLabels(ChangeData input, boolean owners) throws OrmException {
    Set<String> allApprovals = new HashSet<>();
    Set<String> distinctApprovals = new HashSet<>();
    for (PatchSetApproval a : input.currentApprovals()) {
      if (a.getValue() != 0 && !a.isLegacySubmit()) {
        allApprovals.add(formatLabel(a.getLabel(), a.getValue(), a.getAccountId()));
        if (owners && input.change().getOwner().equals(a.getAccountId())) {
          allApprovals.add(
              formatLabel(a.getLabel(), a.getValue(), ChangeQueryBuilder.OWNER_ACCOUNT_ID));
        }
        distinctApprovals.add(formatLabel(a.getLabel(), a.getValue()));
      }
    }
    allApprovals.addAll(distinctApprovals);
    return allApprovals;
  }

  public static Set<String> getAuthorParts(ChangeData cd) throws OrmException {
    try {
      return SchemaUtil.getPersonParts(cd.getAuthor());
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  public static Set<String> getCommitterParts(ChangeData cd) throws OrmException {
    try {
      return SchemaUtil.getPersonParts(cd.getCommitter());
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  /**
   * The exact email address, or any part of the author name or email address, in the current patch
   * set.
   */
  public static final FieldDef<ChangeData, Iterable<String>> AUTHOR =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_AUTHOR, FieldType.FULL_TEXT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return getAuthorParts(input);
        }
      };

  /**
   * The exact email address, or any part of the committer name or email address, in the current
   * patch set.
   */
  public static final FieldDef<ChangeData, Iterable<String>> COMMITTER =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_COMMITTER, FieldType.FULL_TEXT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return getCommitterParts(input);
        }
      };

  public static class ChangeProtoField extends FieldDef.Single<ChangeData, byte[]> {
    public static final ProtobufCodec<Change> CODEC = CodecFactory.encoder(Change.class);

    private ChangeProtoField() {
      super("_change", FieldType.STORED_ONLY, true);
    }

    @Override
    public byte[] get(ChangeData input, FieldDef.FillArgs args) throws OrmException {
      Change c = input.change();
      if (c == null) {
        return null;
      }
      return CODEC.encodeToByteArray(c);
    }
  }

  /** Serialized change object, used for pre-populating results. */
  public static final ChangeProtoField CHANGE = new ChangeProtoField();

  public static class PatchSetApprovalProtoField extends FieldDef.Repeatable<ChangeData, byte[]> {
    public static final ProtobufCodec<PatchSetApproval> CODEC =
        CodecFactory.encoder(PatchSetApproval.class);

    private PatchSetApprovalProtoField() {
      super("_approval", FieldType.STORED_ONLY, true);
    }

    @Override
    public Iterable<byte[]> get(ChangeData input, FillArgs args) throws OrmException {
      return toProtos(CODEC, input.currentApprovals());
    }
  }

  /** Serialized approvals for the current patch set, used for pre-populating results. */
  public static final PatchSetApprovalProtoField APPROVAL = new PatchSetApprovalProtoField();

  public static String formatLabel(String label, int value) {
    return formatLabel(label, value, null);
  }

  public static String formatLabel(String label, int value, Account.Id accountId) {
    return label.toLowerCase()
        + (value >= 0 ? "+" : "")
        + value
        + (accountId != null ? "," + formatAccount(accountId) : "");
  }

  private static String formatAccount(Account.Id accountId) {
    if (ChangeQueryBuilder.OWNER_ACCOUNT_ID.equals(accountId)) {
      return ChangeQueryBuilder.ARG_ID_OWNER;
    }
    return Integer.toString(accountId.get());
  }

  /** Commit message of the current patch set. */
  public static final FieldDef<ChangeData, String> COMMIT_MESSAGE =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_MESSAGE, FieldType.FULL_TEXT, false) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          try {
            return input.commitMessage();
          } catch (IOException e) {
            throw new OrmException(e);
          }
        }
      };

  /** Summary or inline comment. */
  public static final FieldDef<ChangeData, Iterable<String>> COMMENT =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_COMMENT, FieldType.FULL_TEXT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          Set<String> r = new HashSet<>();
          for (Comment c : input.publishedComments()) {
            r.add(c.message);
          }
          for (ChangeMessage m : input.messages()) {
            r.add(m.getMessage());
          }
          return r;
        }
      };

  /** Whether the change is mergeable. */
  public static final FieldDef<ChangeData, String> MERGEABLE =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_MERGEABLE, FieldType.EXACT, true) {
        @Override
        public String get(ChangeData input, FillArgs args) throws OrmException {
          Boolean m = input.isMergeable();
          if (m == null) {
            return null;
          }
          return m ? "1" : "0";
        }
      };

  /** The number of inserted lines in this change. */
  public static final FieldDef<ChangeData, Integer> ADDED =
      new FieldDef.Single<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_ADDED, FieldType.INTEGER_RANGE, true) {
        @Override
        public Integer get(ChangeData input, FillArgs args) throws OrmException {
          return input.changedLines().isPresent() ? input.changedLines().get().insertions : null;
        }
      };

  /** The number of deleted lines in this change. */
  public static final FieldDef<ChangeData, Integer> DELETED =
      new FieldDef.Single<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_DELETED, FieldType.INTEGER_RANGE, true) {
        @Override
        public Integer get(ChangeData input, FillArgs args) throws OrmException {
          return input.changedLines().isPresent() ? input.changedLines().get().deletions : null;
        }
      };

  /** The total number of modified lines in this change. */
  public static final FieldDef<ChangeData, Integer> DELTA =
      new FieldDef.Single<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_DELTA, FieldType.INTEGER_RANGE, false) {
        @Override
        public Integer get(ChangeData input, FillArgs args) throws OrmException {
          return input.changedLines().map(c -> c.insertions + c.deletions).orElse(null);
        }
      };

  /** Users who have commented on this change. */
  public static final FieldDef<ChangeData, Iterable<Integer>> COMMENTBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_COMMENTBY, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args) throws OrmException {
          Set<Integer> r = new HashSet<>();
          for (ChangeMessage m : input.messages()) {
            if (m.getAuthor() != null) {
              r.add(m.getAuthor().get());
            }
          }
          for (Comment c : input.publishedComments()) {
            r.add(c.author.getId().get());
          }
          return r;
        }
      };

  /** Star labels on this change in the format: &lt;account-id&gt;:&lt;label&gt; */
  public static final FieldDef<ChangeData, Iterable<String>> STAR =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_STAR, FieldType.EXACT, true) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return Iterables.transform(
              input.stars().entries(),
              (Map.Entry<Account.Id, String> e) -> {
                return StarredChangesUtil.StarField.create(e.getKey(), e.getValue()).toString();
              });
        }
      };

  /** Users that have starred the change with any label. */
  public static final FieldDef<ChangeData, Iterable<Integer>> STARBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_STARBY, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args) throws OrmException {
          return Iterables.transform(input.stars().keySet(), Account.Id::get);
        }
      };

  /** Opaque group identifiers for this change's patch sets. */
  public static final FieldDef<ChangeData, Iterable<String>> GROUP =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_GROUP, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          Set<String> r = Sets.newHashSetWithExpectedSize(1);
          for (PatchSet ps : input.patchSets()) {
            r.addAll(ps.getGroups());
          }
          return r;
        }
      };

  public static class PatchSetProtoField extends FieldDef.Repeatable<ChangeData, byte[]> {
    public static final ProtobufCodec<PatchSet> CODEC = CodecFactory.encoder(PatchSet.class);

    private PatchSetProtoField() {
      super("_patch_set", FieldType.STORED_ONLY, true);
    }

    @Override
    public Iterable<byte[]> get(ChangeData input, FieldDef.FillArgs args) throws OrmException {
      return toProtos(CODEC, input.patchSets());
    }
  }

  /** Serialized patch set object, used for pre-populating results. */
  public static final PatchSetProtoField PATCH_SET = new PatchSetProtoField();

  /** Users who have edits on this change. */
  public static final FieldDef<ChangeData, Iterable<Integer>> EDITBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_EDITBY, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args) throws OrmException {
          return input.editsByUser().stream().map(Account.Id::get).collect(toSet());
        }
      };

  /** Users who have draft comments on this change. */
  public static final FieldDef<ChangeData, Iterable<Integer>> DRAFTBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_DRAFTBY, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args) throws OrmException {
          return input.draftsByUser().stream().map(Account.Id::get).collect(toSet());
        }
      };

  /**
   * Users the change was reviewed by since the last author update.
   *
   * <p>A change is considered reviewed by a user if the latest update by that user is newer than
   * the latest update by the change author. Both top-level change messages and new patch sets are
   * considered to be updates.
   *
   * <p>If the latest update is by the change owner, then the special value {@link #NOT_REVIEWED} is
   * emitted.
   */
  public static final FieldDef<ChangeData, Iterable<Integer>> REVIEWEDBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_REVIEWEDBY, FieldType.INTEGER, true) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args) throws OrmException {
          Set<Account.Id> reviewedBy = input.reviewedBy();
          if (reviewedBy.isEmpty()) {
            return ImmutableSet.of(NOT_REVIEWED);
          }
          List<Integer> result = new ArrayList<>(reviewedBy.size());
          for (Account.Id id : reviewedBy) {
            result.add(id.get());
          }
          return result;
        }
      };

  // Submit rule options in this class should never use fastEvalLabels. This
  // slows down indexing slightly but produces correct search results.
  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_LENIENT =
      SubmitRuleOptions.defaults().allowClosed(true).allowDraft(true).build();

  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_STRICT =
      SubmitRuleOptions.defaults().build();

  /**
   * JSON type for storing SubmitRecords.
   *
   * <p>Stored fields need to use a stable format over a long period; this type insulates the index
   * from implementation changes in SubmitRecord itself.
   */
  static class StoredSubmitRecord {
    static class StoredLabel {
      String label;
      SubmitRecord.Label.Status status;
      Integer appliedBy;
    }

    SubmitRecord.Status status;
    List<StoredLabel> labels;
    String errorMessage;

    StoredSubmitRecord(SubmitRecord rec) {
      this.status = rec.status;
      this.errorMessage = rec.errorMessage;
      if (rec.labels != null) {
        this.labels = new ArrayList<>(rec.labels.size());
        for (SubmitRecord.Label label : rec.labels) {
          StoredLabel sl = new StoredLabel();
          sl.label = label.label;
          sl.status = label.status;
          sl.appliedBy = label.appliedBy != null ? label.appliedBy.get() : null;
          this.labels.add(sl);
        }
      }
    }

    private SubmitRecord toSubmitRecord() {
      SubmitRecord rec = new SubmitRecord();
      rec.status = status;
      rec.errorMessage = errorMessage;
      if (labels != null) {
        rec.labels = new ArrayList<>(labels.size());
        for (StoredLabel label : labels) {
          SubmitRecord.Label srl = new SubmitRecord.Label();
          srl.label = label.label;
          srl.status = label.status;
          srl.appliedBy = label.appliedBy != null ? new Account.Id(label.appliedBy) : null;
          rec.labels.add(srl);
        }
      }
      return rec;
    }
  }

  public static final FieldDef<ChangeData, Iterable<String>> SUBMIT_RECORD =
      new FieldDef.Repeatable<ChangeData, String>("submit_record", FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args) throws OrmException {
          return formatSubmitRecordValues(input);
        }
      };

  public static final FieldDef<ChangeData, Iterable<byte[]>> STORED_SUBMIT_RECORD_STRICT =
      new FieldDef.Repeatable<ChangeData, byte[]>(
          "full_submit_record_strict", FieldType.STORED_ONLY, true) {
        @Override
        public Iterable<byte[]> get(ChangeData input, FillArgs args) throws OrmException {
          return storedSubmitRecords(input, SUBMIT_RULE_OPTIONS_STRICT);
        }
      };

  public static final FieldDef<ChangeData, Iterable<byte[]>> STORED_SUBMIT_RECORD_LENIENT =
      new FieldDef.Repeatable<ChangeData, byte[]>(
          "full_submit_record_lenient", FieldType.STORED_ONLY, true) {
        @Override
        public Iterable<byte[]> get(ChangeData input, FillArgs args) throws OrmException {
          return storedSubmitRecords(input, SUBMIT_RULE_OPTIONS_LENIENT);
        }
      };

  public static void parseSubmitRecords(
      Collection<String> values, SubmitRuleOptions opts, ChangeData out) {
    checkArgument(!opts.fastEvalLabels());
    List<SubmitRecord> records = parseSubmitRecords(values);
    if (records.isEmpty()) {
      // Assume no values means the field is not in the index;
      // SubmitRuleEvaluator ensures the list is non-empty.
      return;
    }
    out.setSubmitRecords(opts, records);

    // Cache the fastEvalLabels variant as well so it can be used by
    // ChangeJson.
    out.setSubmitRecords(opts.toBuilder().fastEvalLabels(true).build(), records);
  }

  @VisibleForTesting
  static List<SubmitRecord> parseSubmitRecords(Collection<String> values) {
    return values
        .stream()
        .map(v -> GSON.fromJson(v, StoredSubmitRecord.class).toSubmitRecord())
        .collect(toList());
  }

  @VisibleForTesting
  static List<byte[]> storedSubmitRecords(List<SubmitRecord> records) {
    return Lists.transform(records, r -> GSON.toJson(new StoredSubmitRecord(r)).getBytes(UTF_8));
  }

  private static Iterable<byte[]> storedSubmitRecords(ChangeData cd, SubmitRuleOptions opts)
      throws OrmException {
    return storedSubmitRecords(cd.submitRecords(opts));
  }

  public static List<String> formatSubmitRecordValues(ChangeData cd) throws OrmException {
    return formatSubmitRecordValues(
        cd.submitRecords(SUBMIT_RULE_OPTIONS_STRICT), cd.change().getOwner());
  }

  @VisibleForTesting
  static List<String> formatSubmitRecordValues(List<SubmitRecord> records, Account.Id changeOwner) {
    List<String> result = new ArrayList<>();
    for (SubmitRecord rec : records) {
      result.add(rec.status.name());
      if (rec.labels == null) {
        continue;
      }
      for (SubmitRecord.Label label : rec.labels) {
        String sl = label.status.toString() + ',' + label.label.toLowerCase();
        result.add(sl);
        String slc = sl + ',';
        if (label.appliedBy != null) {
          result.add(slc + label.appliedBy.get());
          if (label.appliedBy.equals(changeOwner)) {
            result.add(slc + ChangeQueryBuilder.OWNER_ACCOUNT_ID.get());
          }
        }
      }
    }
    return result;
  }

  public static final Integer NOT_REVIEWED = -1;

  private static String getTopic(ChangeData input) throws OrmException {
    Change c = input.change();
    if (c == null) {
      return null;
    }
    return firstNonNull(c.getTopic(), "");
  }

  private static <T> List<byte[]> toProtos(ProtobufCodec<T> codec, Collection<T> objs)
      throws OrmException {
    List<byte[]> result = Lists.newArrayListWithCapacity(objs.size());
    ByteArrayOutputStream out = new ByteArrayOutputStream(256);
    try {
      for (T obj : objs) {
        out.reset();
        CodedOutputStream cos = CodedOutputStream.newInstance(out);
        codec.encode(obj, cos);
        cos.flush();
        result.add(out.toByteArray());
      }
    } catch (IOException e) {
      throw new OrmException(e);
    }
    return result;
  }
}
