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

import com.google.gerrit.pgm.util.SiteProgram;
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
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.kohsuke.args4j.Option;

public class DumpIndexDocument extends SiteProgram {
  @Option(name = "--index", usage = "Index kind (accounts, changes, groups)")
  private String index;

  @Option(name = "--index-schema-version", usage = "Schema version to dump")
  private String indexVersion;

  @Option(name = "--stdout", usage = "Dump to stdout (default is to write to index.json)")
  private boolean stdout;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();

    Gson gson = new Gson();
    JsonWriter writer =
        new JsonWriter(
            stdout
                ? new PrintWriter(System.out)
                : new BufferedWriter(new FileWriter("index.json")));
    writer.setIndent("  ");

    Path path = getSitePath().resolve("index").resolve(index + "_00" + indexVersion);
    // TODO(davido): Handle closed sub index for change index
    if (index.equals("changes")) {
      path = path.resolve("open");
    }

    try (IndexReader reader = DirectoryReader.open(FSDirectory.open(path))) {
      writer.beginArray();
      for (int i = 0; i < reader.numDocs(); i++) {
        writeDocument(gson, reader.document(i), writer);
      }
      writer.endArray();
    }
    writer.flush();

    return 0;
  }

  private void writeDocument(Gson gson, Document document, JsonWriter writer) throws IOException {
    writer.beginObject();
    for (IndexableField field : document.getFields()) {
      writer.name(field.name());
      writeValue(gson, field, writer);
    }
    writer.endObject();
  }

  private void writeValue(Gson gson, IndexableField field, JsonWriter writer) throws IOException {
    // TODO(davido): Decode other POI protobuf fields
    if (field.name().equals(ChangeField.APPROVAL.getName())) {
      gson.getAdapter(PatchSetApproval.class).write(writer, decodeProtos(field, APPROVAL_CODEC));
      return;
    }
    writer.value(field.toString());
  }

  private static <T> T decodeProtos(IndexableField f, ProtobufCodec<T> c) {
    BytesRef r = f.binaryValue();
    return c.decode(r.bytes, r.offset, r.length);
  }
}
