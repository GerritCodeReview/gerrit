// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static com.google.gerrit.server.index.change.ChangeField.APPROVAL_CODEC;
import static com.google.gerrit.server.index.change.ChangeField.CHANGE_CODEC;
import static com.google.gerrit.server.index.change.ChangeField.PATCH_SET_CODEC;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.google.gwtorm.protobuf.ProtobufCodec;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.kohsuke.args4j.Option;

public class DumpIndexDocument extends SiteProgram {
  private static final Pattern UNINTERESTING_FIELDS =
      Pattern.compile(
          Joiner.on("|")
              .join(
                  ChangeField.REF_STATE.getName(),
                  ChangeField.REF_STATE_PATTERN.getName(),
                  ChangeField.STORED_SUBMIT_RECORD_LENIENT.getName(),
                  ChangeField.STORED_SUBMIT_RECORD_STRICT.getName()));

  @Option(name = "--index", usage = "Index kind (accounts, changes, groups, projects)")
  private String index;

  @Option(name = "--index-schema-version", usage = "Schema version to dump")
  private String indexVersion;

  @Option(name = "--output-file", usage = "Output file name (default is to write to stdout)")
  private String outputFile;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();

    Gson g = new Gson();
    JsonWriter w =
        new JsonWriter(
            Strings.isNullOrEmpty(outputFile)
                ? new PrintWriter(System.out)
                : new BufferedWriter(new FileWriter(outputFile)));
    w.setIndent("  ");

    Path p = getSitePath().resolve("index").resolve(index + "_00" + indexVersion);
    // TODO(davido): Handle closed sub index for change index
    if (index.equals("changes")) {
      p = p.resolve("open");
    }

    try (IndexReader r = DirectoryReader.open(FSDirectory.open(p))) {
      w.beginArray();
      for (int i = 0; i < r.numDocs(); i++) {
        writeDocument(g, r.document(i), w);
      }
      w.endArray();
    }
    w.flush();

    return 0;
  }

  private static void writeDocument(Gson g, Document d, JsonWriter w) throws IOException {
    w.beginObject();
    for (IndexableField f : d.getFields()) {
      if (UNINTERESTING_FIELDS.matcher(f.name()).matches()) {
        continue;
      }
      w.name(f.name());
      writeValue(g, f, w);
    }
    w.endObject();
  }

  private static void writeValue(Gson g, IndexableField f, JsonWriter w) throws IOException {
    String n = f.name();
    if (n.equals(ChangeField.CHANGE.getName())) {
      g.getAdapter(Change.class).write(w, decodeProtos(f, CHANGE_CODEC));
    } else if (n.equals(ChangeField.PATCH_SET.getName())) {
      g.getAdapter(PatchSet.class).write(w, decodeProtos(f, PATCH_SET_CODEC));
    } else if (n.equals(ChangeField.APPROVAL.getName())) {
      g.getAdapter(PatchSetApproval.class).write(w, decodeProtos(f, APPROVAL_CODEC));
    } else {
      w.value(f.toString());
    }
  }

  private static <T> T decodeProtos(IndexableField f, ProtobufCodec<T> c) {
    BytesRef r = f.binaryValue();
    return c.decode(r.bytes, r.offset, r.length);
  }
}
