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

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.OrmException;
import com.google.protobuf.CodedOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Fields indexed on change documents.
 * <p>
 * Each field corresponds to both a field name supported by
 * {@link ChangeQueryBuilder} for querying that field, and a method on
 * {@link ChangeData} used for populating the corresponding document fields in
 * the secondary index.
 */
public class ChangeField {
  /** Legacy change ID. */
  public static final FieldDef<ChangeData, Integer> LEGACY_ID =
      new FieldDef.Single<ChangeData, Integer>("_id",
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
          return input.change().getKey().get();
        }
      };

  /** Change status string, in the same format as {@code status:}. */
  public static final FieldDef<ChangeData, String> STATUS =
      new FieldDef.Single<ChangeData, String>(ChangeQueryBuilder.FIELD_STATUS,
          FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return ChangeStatusPredicate.VALUES.get(
              input.change().getStatus());
        }
      };

  /** Project containing the change. */
  public static final FieldDef<ChangeData, String> PROJECT =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_PROJECT, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change().getProject().get();
        }
      };

  /** Project containing the change, as a prefix field. */
  public static final FieldDef<ChangeData, String> PROJECTS =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_PROJECTS, FieldType.PREFIX, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change().getProject().get();
        }
      };

  /** Reference (aka branch) the change will submit onto. */
  public static final FieldDef<ChangeData, String> REF =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_REF, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change().getDest().get();
        }
      };

  @Deprecated
  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> LEGACY_TOPIC =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_TOPIC, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change().getTopic();
        }
      };

  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> TOPIC =
      new FieldDef.Single<ChangeData, String>(
          "topic2", FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return Objects.firstNonNull(input.change().getTopic(), "");
        }
      };

  // Same value as UPDATED, but implementations truncated to minutes.
  @Deprecated
  /** Last update time since January 1, 1970. */
  public static final FieldDef<ChangeData, Timestamp> LEGACY_UPDATED =
      new FieldDef.Single<ChangeData, Timestamp>(
          "updated", FieldType.TIMESTAMP, true) {
        @Override
        public Timestamp get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change().getLastUpdatedOn();
        }
      };


  /** Last update time since January 1, 1970. */
  public static final FieldDef<ChangeData, Timestamp> UPDATED =
      new FieldDef.Single<ChangeData, Timestamp>(
          "updated2", FieldType.TIMESTAMP, true) {
        @Override
        public Timestamp get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change().getLastUpdatedOn();
        }
      };

  @Deprecated
  public static long legacyParseSortKey(String sortKey) {
    if ("z".equals(sortKey)) {
      return Long.MAX_VALUE;
    }
    return Long.parseLong(sortKey.substring(0, 8), 16);
  }

  /** Legacy sort key field. */
  @Deprecated
  public static final FieldDef<ChangeData, Long> LEGACY_SORTKEY =
      new FieldDef.Single<ChangeData, Long>(
          "sortkey", FieldType.LONG, true) {
        @Override
        public Long get(ChangeData input, FillArgs args)
            throws OrmException {
          return legacyParseSortKey(input.change().getSortKey());
        }
      };

  /**
   * Sort key field.
   * <p>
   * Redundant with {@link #UPDATED} and {@link #LEGACY_ID}, but secondary index
   * implementations may not be able to search over tuples of values.
   */
  @Deprecated
  public static final FieldDef<ChangeData, Long> SORTKEY =
      new FieldDef.Single<ChangeData, Long>(
          "sortkey2", FieldType.LONG, true) {
        @Override
        public Long get(ChangeData input, FillArgs args)
            throws OrmException {
          return ChangeUtil.parseSortKey(input.change().getSortKey());
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
          return input.currentFilePaths();
        }
      };

  public static Set<String> getFileParts(ChangeData cd) throws OrmException {
    Splitter s = Splitter.on('/').omitEmptyStrings();
    Set<String> r = Sets.newHashSet();
    for (String path : cd.currentFilePaths()) {
      for (String part : s.split(path)) {
        r.add(part);
      }
    }
    return r;
  }

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
          return input.change().getOwner().get();
        }
      };

  /** Reviewer(s) associated with the change. */
  public static final FieldDef<ChangeData, Iterable<Integer>> REVIEWER =
      new FieldDef.Repeatable<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_REVIEWER, FieldType.INTEGER, false) {
        @Override
        public Iterable<Integer> get(ChangeData input, FillArgs args)
            throws OrmException {
          Set<Integer> r = Sets.newHashSet();
          if (!args.allowsDrafts &&
              input.change().getStatus() == Change.Status.DRAFT) {
            return r;
          }
          for (PatchSetApproval a : input.approvals().values()) {
            r.add(a.getAccountId().get());
          }
          return r;
        }
      };

  /** Commit id of any PatchSet on the change */
  public static final FieldDef<ChangeData, Iterable<String>> COMMIT =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_COMMIT, FieldType.PREFIX, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          Set<String> revisions = Sets.newHashSet();
          for (PatchSet ps : input.patches()) {
            if (ps.getRevision() != null) {
              revisions.add(ps.getRevision().get());
            }
          }
          return revisions;
        }
      };

  /** Tracking id extracted from a footer. */
  public static final FieldDef<ChangeData, Iterable<String>> TR =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_TR, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          try {
            return Sets.newHashSet(args.trackingFooters.extract(
                input.commitFooters()).values());
          } catch (NoSuchChangeException | IOException e) {
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
  public static final FieldDef<ChangeData, String> REVIEWED =
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

  public static class ChangeProtoField extends FieldDef.Single<ChangeData, byte[]> {
    public static final ProtobufCodec<Change> CODEC =
        CodecFactory.encoder(Change.class);

    private ChangeProtoField() {
      super("_change", FieldType.STORED_ONLY, true);
    }

    @Override
    public byte[] get(ChangeData input, FieldDef.FillArgs args)
        throws OrmException {
      return CODEC.encodeToByteArray(input.change());
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
          } catch (NoSuchChangeException | IOException e) {
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
          for (PatchLineComment c : input.comments()) {
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
          ChangeQueryBuilder.FIELD_MERGEABLE, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change().isMergeable() ? "1" : null;
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
