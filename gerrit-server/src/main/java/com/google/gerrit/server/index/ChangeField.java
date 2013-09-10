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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.SubmitRecord;
import com.google.gerrit.reviewdb.client.TrackingId;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.OrmException;
import com.google.protobuf.CodedOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
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
          return input.change(args.db).getKey().get();
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
              input.change(args.db).getStatus());
        }
      };

  /** Project containing the change. */
  public static final FieldDef<ChangeData, String> PROJECT =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_PROJECT, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change(args.db).getProject().get();
        }
      };

  /** Reference (aka branch) the change will submit onto. */
  public static final FieldDef<ChangeData, String> REF =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_REF, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change(args.db).getDest().get();
        }
      };

  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> TOPIC =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_TOPIC, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change(args.db).getTopic();
        }
      };

  /** Last update time since January 1, 1970. */
  public static final FieldDef<ChangeData, Timestamp> UPDATED =
      new FieldDef.Single<ChangeData, Timestamp>(
          "updated", FieldType.TIMESTAMP, true) {
        @Override
        public Timestamp get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change(args.db).getLastUpdatedOn();
        }
      };

  /** Sort key field, duplicates {@link #UPDATED}. */
  @Deprecated
  public static final FieldDef<ChangeData, Long> SORTKEY =
      new FieldDef.Single<ChangeData, Long>(
          "sortkey", FieldType.LONG, true) {
        @Override
        public Long get(ChangeData input, FillArgs args)
            throws OrmException {
          return ChangeUtil.parseSortKey(input.change(args.db).getSortKey());
        }
      };

  /** List of filenames modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> FILE =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_FILE, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.currentFilePaths(args.db, args.patchListCache);
        }
      };

  /** Owner/creator of the change. */
  public static final FieldDef<ChangeData, Integer> OWNER =
      new FieldDef.Single<ChangeData, Integer>(
          ChangeQueryBuilder.FIELD_OWNER, FieldType.INTEGER, false) {
        @Override
        public Integer get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change(args.db).getOwner().get();
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
          for (PatchSetApproval a : input.allApprovals(args.db)) {
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
          for (PatchSet ps : input.patches(args.db)) {
            revisions.add(ps.getRevision().get());
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
          Set<String> r = Sets.newHashSet();
          for (TrackingId id : input.trackingIds(args.db)) {
            r.add(id.getTrackingId());
          }
          return r;
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
          for (PatchSetApproval a : input.currentApprovals(args.db)) {
            if (a.getValue() != 0) {
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
          for (PatchSetApproval a : input.currentApprovals(args.db)) {
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
      return CODEC.encodeToByteArray(input.change(args.db));
    }
  }

  /** Serialized change object, used for pre-populating results. */
  public static final ChangeProtoField CHANGE = new ChangeProtoField();

  public static class SubmitLabelProtoField
      extends FieldDef.Repeatable<ChangeData, byte[]> {
    public static final ProtobufCodec<SubmitRecord.Label> CODEC =
        CodecFactory.encoder(SubmitRecord.Label.class);

    private SubmitLabelProtoField() {
      super("_label", FieldType.STORED_ONLY, true);
    }

    @Override
    public Iterable<byte[]> get(ChangeData input, FillArgs args)
        throws OrmException {
      // Flatten the highest-valued labels to mimic the results from ChangeJson
      // with standard labels.
      Map<String, SubmitRecord.Label> labels = Maps.newLinkedHashMap();
      for (SubmitRecord rec : getSubmitRecords(input, args)) {
        if (rec.labels == null) {
          continue;
        }
        for (SubmitRecord.Label r : rec.labels) {
          SubmitRecord.Label p = labels.get(r.label);
          if (p == null || p.status.compareTo(r.status) < 0) {
            labels.put(r.label, r);
          }
        }
      }
      return toProtos(CODEC, labels.values());
    }

    private List<SubmitRecord> getSubmitRecords(ChangeData input,
        FillArgs args) throws OrmException {
      ChangeControl ctl;
      try {
        // Use the ChangeControl for InternalUser. This will give bogus
        // results for whether or not the change is submittable, but does
        // not affect label calculation.
        ctl = args.changeControlFor(input.change(args.db));
      } catch (NoSuchChangeException e) {
        throw new OrmException(e);
      }
      if (ctl == null) {
        return ImmutableList.of();
      }
      PatchSet ps = input.currentPatchSet(args.db);
      if (ps == null) {
        return ImmutableList.of();
      }
      return ctl.canSubmit(args.db.get(), ps, input, true, true, true);
    }
  }

  /**
   * Serialized labels from the submit rule evaluator, used for pre-populating
   * results.
   */
  public static final SubmitLabelProtoField SUBMIT_RECORD_LABEL =
      new SubmitLabelProtoField();

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
            return input.commitMessage(args.repoManager, args.db);
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
          for (PatchLineComment c : input.comments(args.db)) {
            r.add(c.getMessage());
          }
          for (ChangeMessage m : input.messages(args.db)) {
            r.add(m.getMessage());
          }
          return r;
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
