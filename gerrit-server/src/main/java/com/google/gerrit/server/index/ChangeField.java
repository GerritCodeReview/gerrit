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

package com.google.gerrit.server.index;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.OrmException;
import com.google.protobuf.CodedOutputStream;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.FooterLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
  @Deprecated
  /** Legacy change ID. */
  public static final FieldDef<ChangeData, Integer> LEGACY_ID =
      new FieldDef.Single<ChangeData, Integer>("_id",
          FieldType.INTEGER, true) {
        @Override
        public Integer get(ChangeData input, FillArgs args) {
          return input.getId().get();
        }
      };

  /** Legacy change ID without underscore prefix. */
  public static final FieldDef<ChangeData, Integer> LEGACY_ID2 =
      new FieldDef.Single<ChangeData, Integer>("legacy_id",
          FieldType.INTEGER, true) {
        @Override
        public Integer get(ChangeData input, FillArgs args) {
          return input.getId().get();
        }
      };

  /** Newer style Change-Id key. */
  public static final FieldDef<ChangeData, String> ID =
      new FieldDef.Single<ChangeData, String>("change_id",
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
          ChangeQueryBuilder.FIELD_PROJECT, FieldType.EXACT, false) {
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

  @Deprecated
  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> LEGACY_TOPIC2 =
      new FieldDef.Single<ChangeData, String>(
          "topic2", FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return getTopic(input);
        }
      };

  @Deprecated
  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> LEGACY_TOPIC3 =
      new FieldDef.Single<ChangeData, String>(
          "topic3", FieldType.PREFIX, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return getTopic(input);
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
          "submissionid", FieldType.EXACT, false) {
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
          "file", FieldType.EXACT, false) {
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
    Set<String> r = Sets.newHashSet();
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
          "hashtag", FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return ImmutableSet.copyOf(Iterables.transform(input.notes().load()
              .getHashtags(), new Function<String, String>() {

            @Override
            public String apply(String input) {
              return input.toLowerCase();
            }

          }));
        }
      };

  /** Components of each file path modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> FILE_PART =
      new FieldDef.Repeatable<ChangeData, String>(
          "filepart", FieldType.EXACT, false) {
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
  public static final FieldDef<ChangeData, Iterable<Integer>> REVIEWER =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_REVIEWER, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args)
            throws OrmException {
          Change c = input.change();
          if (c == null) {
            return ImmutableSet.of();
          }
          Set<Integer> r = Sets.newHashSet();
          if (!args.allowsDrafts && c.getStatus() == Change.Status.DRAFT) {
            return r;
          }
          for (PatchSetApproval a : input.approvals().values()) {
            r.add(a.getAccountId().get());
          }
          return r;
        }
      };

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
          "exactcommit", FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return getRevisions(input);
        }
      };

  private static Set<String> getRevisions(ChangeData cd) throws OrmException {
    Set<String> revisions = Sets.newHashSet();
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
            HashSet<String> values = new HashSet<>();
            List<FooterLine> footers = input.commitFooters();
            if (footers != null) {
              values.addAll(args.trackingFooters.extract(footers).values());
            }
            values.addAll(
                args.trackingValueExtractor.getValues(input.commitMessage()));
            return values;
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
          Set<String> allApprovals = Sets.newHashSet();
          Set<String> distinctApprovals = Sets.newHashSet();
          for (PatchSetApproval a : input.currentApprovals()) {
            if (a.getValue() != 0 && !a.isSubmit()) {
              allApprovals.add(formatLabel(a.getLabel(), a.getValue(),
                  a.getAccountId()));
              distinctApprovals.add(formatLabel(a.getLabel(), a.getValue()));
            }
          }
          allApprovals.addAll(distinctApprovals);
          return allApprovals;
        }
      };

  /** Set true if the change has a non-zero label score. */
  @Deprecated
  public static final FieldDef<ChangeData, String> LEGACY_REVIEWED =
      new FieldDef.Single<ChangeData, String>(
          "reviewed", FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          for (PatchSetApproval a : input.currentApprovals()) {
            if (a.getValue() != 0) {
              return "1";
            }
          }
          return null;
        }
      };

  private static Set<String> getPersonParts(PersonIdent person) {
    if (person == null) {
      return ImmutableSet.of();
    }
    HashSet<String> parts = Sets.newHashSet();
    String email = person.getEmailAddress().toLowerCase();
    parts.add(email);
    parts.addAll(Arrays.asList(email.split("@")));
    Splitter s = Splitter.on(CharMatcher.anyOf("@.- ")).omitEmptyStrings();
    Iterables.addAll(parts, s.split(email));
    Iterables.addAll(parts, s.split(person.getName().toLowerCase()));
    return parts;
  }

  public static Set<String> getAuthorParts(ChangeData cd) throws OrmException {
    try {
      return getPersonParts(cd.getAuthor());
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  public static Set<String> getCommitterParts(ChangeData cd) throws OrmException {
    try {
      return getPersonParts(cd.getCommitter());
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
          Set<String> r = Sets.newHashSet();
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
          "mergeable2", FieldType.EXACT, true) {
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

          return input.changedLines() != null
              ? input.changedLines().insertions
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
          return input.changedLines() != null
              ? input.changedLines().deletions
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
          ChangedLines changedLines = input.changedLines();
          return changedLines != null
              ? changedLines.insertions + changedLines.deletions
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

  /** Opaque group identifiers for this change's patch sets. */
  public static final FieldDef<ChangeData, Iterable<String>> GROUP =
      new FieldDef.Repeatable<ChangeData, String>(
          "group", FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          Set<String> r = Sets.newHashSetWithExpectedSize(1);
          for (PatchSet ps : input.patchSets()) {
            List<String> groups = ps.getGroups();
            if (groups != null) {
              r.addAll(groups);
            }
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
