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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.SchemaUtil;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.OrmException;
import com.google.protobuf.CodedOutputStream;

import org.eclipse.jgit.revwalk.FooterLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fields indexed on change documents.
 * <p>
 * Each field corresponds to both a field name supported by
 * {@link ChangeQueryBuilder} for querying that field, and a method on
 * {@link ChangeData} used for populating the corresponding document fields in
 * the secondary index.
 * <p>
 * Field names are all lowercase alphanumeric plus underscore; index
 * implementations may create unambiguous derived field names containing other
 * characters.
 */
public class ChangeField {
  /** Legacy change ID. */
  public static final FieldDef<ChangeData, Integer> LEGACY_ID =
      new FieldDef.Single<ChangeData, Integer>("legacy_id",
          FieldType.INTEGER, true) {
        @Override
        public Integer get(ChangeData input, FillArgs args) {
          return input.getId().get();
        }
      };

  /** Newer style Change-Id key. */
  public static final FieldDef<ChangeData, String> ID =
      new FieldDef.Single<ChangeData, String>(ChangeQueryBuilder.FIELD_CHANGE_ID,
          FieldType.PREFIX, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getKey().get();
        }
      };

  /** Change status string, in the same format as {@code status:}. */
  public static final FieldDef<ChangeData, String> STATUS =
      new FieldDef.Single<ChangeData, String>(ChangeQueryBuilder.FIELD_STATUS,
          FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
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
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
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
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
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
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getDest().get();
        }
      };

  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> EXACT_TOPIC =
      new FieldDef.Single<ChangeData, String>(
          "topic4", FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return getTopic(input);
        }
      };

  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> FUZZY_TOPIC =
      new FieldDef.Single<ChangeData, String>(
          "topic5", FieldType.FULL_TEXT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return getTopic(input);
        }
      };

  /** Submission id assigned by MergeOp. */
  public static final FieldDef<ChangeData, String> SUBMISSIONID =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_SUBMISSIONID, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getSubmissionId();
        }
      };

  /** Last update time since January 1, 1970. */
  public static final FieldDef<ChangeData, Timestamp> UPDATED =
      new FieldDef.Single<ChangeData, Timestamp>(
          "updated2", FieldType.TIMESTAMP, true) {
        @Override
        public Timestamp get(ChangeData input, FillArgs args)
            throws OrmException {
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
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return firstNonNull(input.currentFilePaths(),
              ImmutableList.<String> of());
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
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return ImmutableSet.copyOf(Iterables.transform(input.hashtags(),
              new Function<String, String>() {
            @Override
            public String apply(String input) {
              return input.toLowerCase();
            }
          }));
        }
      };

  /** Hashtags with original case. */
  public static final FieldDef<ChangeData, Iterable<byte[]>> HASHTAG_CASE_AWARE =
      new FieldDef.Repeatable<ChangeData, byte[]>(
          "_hashtag", FieldType.STORED_ONLY, true) {
        @Override
        public Iterable<byte[]> get(ChangeData input, FillArgs args)
            throws OrmException {
          return ImmutableSet.copyOf(Iterables.transform(input.hashtags(),
              new Function<String, byte[]>() {
            @Override
            public byte[] apply(String hashtag) {
              return hashtag.getBytes(UTF_8);
            }
          }));
        }
      };

  /** Components of each file path modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> FILE_PART =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_FILEPART, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return getFileParts(input);
        }
      };

  /** Owner/creator of the change. */
  public static final FieldDef<ChangeData, Integer> OWNER =
      new FieldDef.Single<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_OWNER, FieldType.INTEGER, false) {
        @Override
        public Integer get(ChangeData input, FillArgs args)
            throws OrmException {
          Change c = input.change();
          if (c == null) {
            return null;
          }
          return c.getOwner().get();
        }
      };

  /** Reviewer(s) associated with the change. */
  @Deprecated
  public static final FieldDef<ChangeData, Iterable<Integer>> LEGACY_REVIEWER =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_REVIEWER, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args)
            throws OrmException {
          Change c = input.change();
          if (c == null) {
            return ImmutableSet.of();
          }
          Set<Integer> r = new HashSet<>();
          if (!args.allowsDrafts && c.getStatus() == Change.Status.DRAFT) {
            return r;
          }
          for (PatchSetApproval a : input.approvals().values()) {
            r.add(a.getAccountId().get());
          }
          return r;
        }
      };

  /** Reviewer(s) associated with the change. */
  public static final FieldDef<ChangeData, Iterable<String>> REVIEWER =
      new FieldDef.Repeatable<ChangeData, String>(
          "reviewer2", FieldType.EXACT, true) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return getReviewerFieldValues(input.reviewers());
        }
      };

  @VisibleForTesting
  static List<String> getReviewerFieldValues(ReviewerSet reviewers) {
    List<String> r = new ArrayList<>(reviewers.asTable().size() * 2);
    for (Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> c
        : reviewers.asTable().cellSet()) {
      String v = getReviewerFieldValue(c.getRowKey(), c.getColumnKey());
      r.add(v);
      r.add(v + ',' + c.getValue().getTime());
    }
    return r;
  }

  public static String getReviewerFieldValue(ReviewerStateInternal state,
      Account.Id id) {
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
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return getRevisions(input);
        }
      };

  /** Commit ID of any patch set on the change, using exact match. */
  public static final FieldDef<ChangeData, Iterable<String>> EXACT_COMMIT =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_EXACTCOMMIT, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
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
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          try {
            List<FooterLine> footers = input.commitFooters();
            if (footers == null) {
              return ImmutableSet.of();
            }
            return Sets.newHashSet(
                args.trackingFooters.extract(footers).values());
          } catch (IOException e) {
            throw new OrmException(e);
          }
        }
      };

  /** List of labels on the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> LABEL =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_LABEL, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          Set<String> allApprovals = new HashSet<>();
          Set<String> distinctApprovals = new HashSet<>();
          for (PatchSetApproval a : input.currentApprovals()) {
            if (a.getValue() != 0 && !a.isLegacySubmit()) {
              allApprovals.add(formatLabel(a.getLabel(), a.getValue(),
                  a.getAccountId()));
              distinctApprovals.add(formatLabel(a.getLabel(), a.getValue()));
            }
          }
          allApprovals.addAll(distinctApprovals);
          return allApprovals;
        }
      };

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
   * The exact email address, or any part of the author name or email address,
   * in the current patch set.
   */
  public static final FieldDef<ChangeData, Iterable<String>> AUTHOR =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_AUTHOR, FieldType.FULL_TEXT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return getAuthorParts(input);
        }
      };

  /**
   * The exact email address, or any part of the committer name or email address,
   * in the current patch set.
   */
  public static final FieldDef<ChangeData, Iterable<String>> COMMITTER =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_COMMITTER, FieldType.FULL_TEXT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return getCommitterParts(input);
        }
      };

  public static class ChangeProtoField extends FieldDef.Single<ChangeData, byte[]> {
    public static final ProtobufCodec<Change> CODEC =
        CodecFactory.encoder(Change.class);

    private ChangeProtoField() {
      super("_change", FieldType.STORED_ONLY, true);
    }

    @Override
    public byte[] get(ChangeData input, FieldDef.FillArgs args)
        throws OrmException {
      Change c = input.change();
      if (c == null) {
        return null;
      }
      return CODEC.encodeToByteArray(c);
    }
  }

  /** Serialized change object, used for pre-populating results. */
  public static final ChangeProtoField CHANGE = new ChangeProtoField();

  public static class PatchSetApprovalProtoField
      extends FieldDef.Repeatable<ChangeData, byte[]> {
    public static final ProtobufCodec<PatchSetApproval> CODEC =
        CodecFactory.encoder(PatchSetApproval.class);

    private PatchSetApprovalProtoField() {
      super("_approval", FieldType.STORED_ONLY, true);
    }

    @Override
    public Iterable<byte[]> get(ChangeData input, FillArgs args)
        throws OrmException {
      return toProtos(CODEC, input.currentApprovals());
    }
  }

  /**
   * Serialized approvals for the current patch set, used for pre-populating
   * results.
   */
  public static final PatchSetApprovalProtoField APPROVAL =
      new PatchSetApprovalProtoField();

  public static String formatLabel(String label, int value) {
    return formatLabel(label, value, null);
  }

  public static String formatLabel(String label, int value, Account.Id accountId) {
    return label.toLowerCase() + (value >= 0 ? "+" : "") + value
        + (accountId != null ? "," + accountId.get() : "");
  }

  /** Commit message of the current patch set. */
  public static final FieldDef<ChangeData, String> COMMIT_MESSAGE =
      new FieldDef.Single<ChangeData, String>(ChangeQueryBuilder.FIELD_MESSAGE,
          FieldType.FULL_TEXT, false) {
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
      new FieldDef.Repeatable<ChangeData, String>(ChangeQueryBuilder.FIELD_COMMENT,
          FieldType.FULL_TEXT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          Set<String> r = new HashSet<>();
          for (PatchLineComment c : input.publishedComments()) {
            r.add(c.getMessage());
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
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
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
        public Integer get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.changedLines().isPresent()
              ? input.changedLines().get().insertions
              : null;
        }
      };

  /** The number of deleted lines in this change. */
  public static final FieldDef<ChangeData, Integer> DELETED =
      new FieldDef.Single<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_DELETED, FieldType.INTEGER_RANGE, true) {
        @Override
        public Integer get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.changedLines().isPresent()
              ? input.changedLines().get().deletions
              : null;
        }
      };

  /** The total number of modified lines in this change. */
  public static final FieldDef<ChangeData, Integer> DELTA =
      new FieldDef.Single<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_DELTA, FieldType.INTEGER_RANGE, false) {
        @Override
        public Integer get(ChangeData input, FillArgs args)
            throws OrmException {
          Optional<ChangedLines> changedLines = input.changedLines();
          return changedLines.isPresent()
              ? changedLines.get().insertions + changedLines.get().deletions
              : null;
        }
      };

  /** Users who have commented on this change. */
  public static final FieldDef<ChangeData, Iterable<Integer>> COMMENTBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_COMMENTBY, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args)
            throws OrmException {
          Set<Integer> r = new HashSet<>();
          for (ChangeMessage m : input.messages()) {
            if (m.getAuthor() != null) {
              r.add(m.getAuthor().get());
            }
          }
          for (PatchLineComment c : input.publishedComments()) {
            r.add(c.getAuthor().get());
          }
          return r;
        }
      };

  /** Users who have starred this change. */
  @Deprecated
  public static final FieldDef<ChangeData, Iterable<Integer>> STARREDBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_STARREDBY, FieldType.INTEGER, true) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args)
            throws OrmException {
          return Iterables.transform(input.starredBy(),
              new Function<Account.Id, Integer>() {
            @Override
            public Integer apply(Account.Id accountId) {
              return accountId.get();
            }
          });
        }
      };

  /**
   * Star labels on this change in the format: &lt;account-id&gt;:&lt;label&gt;
   */
  public static final FieldDef<ChangeData, Iterable<String>> STAR =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_STAR, FieldType.EXACT, true) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return Iterables.transform(input.stars().entries(),
              new Function<Map.Entry<Account.Id, String>, String>() {
            @Override
            public String apply(Map.Entry<Account.Id, String> e) {
              return StarredChangesUtil.StarField.create(
                  e.getKey(), e.getValue()).toString();
            }
          });
        }
      };

  /** Users that have starred the change with any label. */
  public static final FieldDef<ChangeData, Iterable<Integer>> STARBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_STARBY, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args)
            throws OrmException {
          return Iterables.transform(input.stars().keySet(),
              ReviewDbUtil.INT_KEY_FUNCTION);
        }
      };

  /** Opaque group identifiers for this change's patch sets. */
  public static final FieldDef<ChangeData, Iterable<String>> GROUP =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_GROUP, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          Set<String> r = Sets.newHashSetWithExpectedSize(1);
          for (PatchSet ps : input.patchSets()) {
            r.addAll(ps.getGroups());
          }
          return r;
        }
      };

  public static class PatchSetProtoField
      extends FieldDef.Repeatable<ChangeData, byte[]> {
    public static final ProtobufCodec<PatchSet> CODEC =
        CodecFactory.encoder(PatchSet.class);

    private PatchSetProtoField() {
      super("_patch_set", FieldType.STORED_ONLY, true);
    }

    @Override
    public Iterable<byte[]> get(ChangeData input, FieldDef.FillArgs args)
        throws OrmException {
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
        public Iterable<Integer> get(ChangeData input, FillArgs args)
            throws OrmException {
          return ImmutableSet.copyOf(Iterables.transform(input.editsByUser(),
              new Function<Account.Id, Integer>() {
            @Override
            public Integer apply(Account.Id account) {
              return account.get();
            }
          }));
        }
      };


  /** Users who have draft comments on this change. */
  public static final FieldDef<ChangeData, Iterable<Integer>> DRAFTBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_DRAFTBY, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args)
            throws OrmException {
          return ImmutableSet.copyOf(Iterables.transform(input.draftsByUser(),
              new Function<Account.Id, Integer>() {
            @Override
            public Integer apply(Account.Id account) {
              return account.get();
            }
          }));
        }
      };

  /**
   * Users the change was reviewed by since the last author update.
   * <p>
   * A change is considered reviewed by a user if the latest update by that user
   * is newer than the latest update by the change author. Both top-level change
   * messages and new patch sets are considered to be updates.
   * <p>
   * If the latest update is by the change owner, then the special value {@link
   * #NOT_REVIEWED} is emitted.
   */
  public static final FieldDef<ChangeData, Iterable<Integer>> REVIEWEDBY =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_REVIEWEDBY, FieldType.INTEGER, true) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args)
            throws OrmException {
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
