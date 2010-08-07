// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.schema.backup;

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.jgit.util.IO;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Restore the database from a compressed series of protobuf objects. */
public class RestoreBackup {
  public static void restore(InputStream in, ReviewDb dst) throws IOException,
      OrmException {
    in = new BufferedInputStream(new GZIPInputStream(in, 8192));

    BackupDatabase<ReviewDb> bck = new BackupDatabase<ReviewDb>(ReviewDb.class);
    ReviewDb src = bck.open();

    dst.setAutoFlush(false);
    restoreImpl(in, src, dst);
    dst.flush();
  }

  @SuppressWarnings("unchecked")
  private static void restoreImpl(InputStream in, ReviewDb src, ReviewDb dst)
      throws IOException, OrmException {
    // Remove every row, we're about to overwrite them all.
    //
    for (Access<?, ?> s : dst.allRelations()) {
      List objects = s.iterateAllEntities().toList();
      s.delete(objects);
    }

    Map<Integer, BackupAccess<?, ?>> read = index(src);
    Map<Integer, Access<?, ?>> store = index(dst);

    // The first object should be a Counters.
    //
    Counters cnts = Counters.CODEC.decodeWithSize(in);

    // Remaining objects are length delimited until EOF.
    //
    Set<Integer> notKnown = new HashSet<Integer>();
    for (;;) {
      in.mark(1);
      if (in.read() == -1) {
        break;
      }

      in.reset();
      in.mark(32);
      int len = readRawVarint32(in);
      int id = readRawVarint32(in) >>> 3;

      BackupAccess<?, ?> r = read.get(id);
      Access<?, ?> w = store.get(id);

      if (r != null && w != null) {
        in.reset();
        ProtobufCodec pc = r.getObjectCodec();
        Set s = Collections.singleton(pc.decodeWithSize(in));
        w.upsert(s);

      } else {
        if (notKnown.add(id)) {
          System.err.println("warning: Skipping relation " + id);
        }
        in.reset();
        if (len != readRawVarint32(in)) {
          throw new IOException("Stream didn't reset before skipping");
        }
        IO.skipFully(in, len);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map index(ReviewDb src) {
    Map<Integer, Access<?, ?>> relations = new HashMap<Integer, Access<?, ?>>();
    for (Access<?, ?> a : src.allRelations()) {
      relations.put(a.getRelationID(), a);
    }
    return relations;
  }

  private static int readRawVarint32(InputStream in) throws IOException {
    int b = in.read();
    if (b == -1) {
      throw new InvalidProtocolBufferException("Truncated input");
    }

    if ((b & 0x80) == 0) {
      return b;
    }

    int result = b & 0x7f;
    int offset = 7;
    for (; offset < 32; offset += 7) {
      b = in.read();
      if (b == -1) {
        throw new InvalidProtocolBufferException("Truncated input");
      }
      result |= (b & 0x7f) << offset;
      if ((b & 0x80) == 0) {
        return result;
      }
    }

    // Keep reading up to 64 bits.
    for (; offset < 64; offset += 7) {
      b = in.read();
      if (b == -1) {
        throw new InvalidProtocolBufferException("Truncated input");
      }
      if ((b & 0x80) == 0) {
        return result;
      }
    }

    throw new InvalidProtocolBufferException("Malformed varint");
  }

  private RestoreBackup() {
  }
}
