// Copyright (C) 2011 The Android Open Source Project
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

import static org.apache.lucene.document.Field.Index.ANALYZED;
import static org.apache.lucene.document.Field.Index.NOT_ANALYZED;
import static org.apache.lucene.document.Field.Index.NOT_ANALYZED_NO_NORMS;

import com.google.gerrit.reviewdb.Change;
import com.google.gson.Gson;
import com.google.gwtjsonrpc.server.JsonServlet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;

import java.io.IOException;

public class UpdateTransaction {
  private final IndexWriter writer;

  private final Gson gson;
  private final Document changeDoc;
  private final Field json;
  private final Field project;
  private final Field ref;
  private final Field changeId;
  private final Field changeKey;
  private final Field status;
  private final Field owner;
  private final Field subject;
  private final NumericField lastUpdate;

  private Field topic;
  private boolean hasTopic;

  UpdateTransaction(IndexWriter writer) {
    this.writer = writer;

    gson = JsonServlet.defaultGsonBuilder().create();

    changeDoc = new Document();
    changeDoc.add(json = new Field("json", "", Store.YES, Index.NO));
    changeDoc.add(project = new Field("project", "", Store.NO, NOT_ANALYZED));
    changeDoc.add(ref = new Field("ref", "", Store.NO, NOT_ANALYZED));
    changeDoc.add(changeId = new Field("change-id", "", Store.NO, NOT_ANALYZED_NO_NORMS));
    changeDoc.add(changeKey = new Field("change-key", "", Store.NO, NOT_ANALYZED_NO_NORMS));
    changeDoc.add(status = new Field("status", "", Store.NO, NOT_ANALYZED));
    changeDoc.add(subject = new Field("subject", "", Store.NO, ANALYZED));
    changeDoc.add(owner = new Field("owner", "", Store.NO, NOT_ANALYZED));
    changeDoc.add(lastUpdate = new NumericField("last-update", Store.NO, true /* index */));
    topic = new Field("topic", "", Store.NO, NOT_ANALYZED_NO_NORMS);
  }

  public void add(Change src)
      throws IndexingException {
    try {
      use(src);
      writer.addDocument(changeDoc);
    } catch (CorruptIndexException e) {
      throw new IndexingException(e);
    } catch (IOException e) {
      throw new IndexingException(e);
    }
  }

  private void use(Change src) {
    json.setValue(gson.toJson(src));
    project.setValue(src.getProject().get());
    ref.setValue(src.getDest().get());
    changeId.setValue(src.getId().toString());
    changeKey.setValue(src.getKey().get());
    status.setValue(String.valueOf(src.getStatus().getCode()));
    subject.setValue(src.getSubject());
    owner.setValue(src.getOwner().toString());

    lastUpdate.setIntValue(encodeLastUpdated(src.getLastUpdatedOn().getTime()));

    if (src.getTopic() != null) {
      topic.setValue(src.getTopic());
      if (!hasTopic) {
        changeDoc.add(topic);
        hasTopic = true;
      }
    } else if (hasTopic) {
      changeDoc.removeFields(topic.name());
      hasTopic = false;
    }
  }

  static int encodeLastUpdated(long lastUpdated) {
    // The encoding uses minutes since Wed Oct 1 00:00:00 2008 UTC.
    // We overrun approximately 4,085 years later, so ~6093.
    //
    lastUpdated = ((lastUpdated / 1000L) - 1222819200L) / 60;

    // Invert the numeric so more recent dates have lower values, and
    // will naturally sort before older dates.
    //
    return Integer.MAX_VALUE - ((int) lastUpdated);
  }

  public void commit() throws IndexingException {
    try {
      writer.commit();
    } catch (CorruptIndexException e) {
      throw new IndexingException(e);
    } catch (IOException e) {
      throw new IndexingException(e);
    }
  }

  public void close() throws IndexingException {
    try {
      writer.close();
    } catch (CorruptIndexException e) {
      throw new IndexingException(e);
    } catch (IOException e) {
      throw new IndexingException(e);
    }
  }
}
