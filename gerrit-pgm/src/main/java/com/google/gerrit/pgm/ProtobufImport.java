// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.SINGLE_USER;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Iterables;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.schema.RelationModel;
import com.google.gwtorm.schema.java.JavaSchemaModel;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.protobuf.ByteString;
import com.google.protobuf.Parser;
import com.google.protobuf.UnknownFieldSet;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.kohsuke.args4j.Option;

/**
 * Import data from a protocol buffer dump into the database.
 *
 * <p>Takes as input a file containing protocol buffers concatenated together with varint length
 * encoding, as in {@link Parser#parseDelimitedFrom(InputStream)}. Each message contains a single
 * field with a tag corresponding to the relation ID in the {@link
 * com.google.gwtorm.server.Relation} annotation.
 *
 * <p><strong>Warning</strong>: This method blindly upserts data into the database. It should only
 * be used to restore a protobuf-formatted backup into a new, empty site.
 */
public class ProtobufImport extends SiteProgram {
  @Option(
    name = "--file",
    aliases = {"-f"},
    required = true,
    metaVar = "FILE",
    usage = "File to import from"
  )
  private File file;

  private final LifecycleManager manager = new LifecycleManager();
  private final Map<Integer, Relation> relations = new HashMap<>();

  @Inject private SchemaFactory<ReviewDb> schemaFactory;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();

    Injector dbInjector = createDbInjector(SINGLE_USER);
    manager.add(dbInjector);
    manager.start();
    RuntimeShutdown.add(
        new Runnable() {
          @Override
          public void run() {
            manager.stop();
          }
        });
    dbInjector.injectMembers(this);

    ProgressMonitor progress = new TextProgressMonitor();
    progress.beginTask("Importing entities", ProgressMonitor.UNKNOWN);
    try (ReviewDb db = schemaFactory.open()) {
      for (RelationModel model : new JavaSchemaModel(ReviewDb.class).getRelations()) {
        relations.put(model.getRelationID(), Relation.create(model, db));
      }

      Parser<UnknownFieldSet> parser = UnknownFieldSet.getDefaultInstance().getParserForType();
      try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
        UnknownFieldSet msg;
        while ((msg = parser.parseDelimitedFrom(in)) != null) {
          Map.Entry<Integer, UnknownFieldSet.Field> e =
              Iterables.getOnlyElement(msg.asMap().entrySet());
          Relation rel =
              checkNotNull(
                  relations.get(e.getKey()),
                  "unknown relation ID %s in message: %s",
                  e.getKey(),
                  msg);
          List<ByteString> values = e.getValue().getLengthDelimitedList();
          checkState(values.size() == 1, "expected one string field in message: %s", msg);
          upsert(rel, values.get(0));
          progress.update(1);
        }
      }
      progress.endTask();
    }

    return 0;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void upsert(Relation rel, ByteString s) throws OrmException {
    Collection ents = Collections.singleton(rel.codec().decode(s));
    try {
      // Not all relations support update; fall back manually.
      rel.access().insert(ents);
    } catch (OrmDuplicateKeyException e) {
      rel.access().delete(ents);
      rel.access().insert(ents);
    }
  }

  @AutoValue
  abstract static class Relation {
    private static Relation create(RelationModel model, ReviewDb db)
        throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            ClassNotFoundException {
      Method m = db.getClass().getMethod(model.getMethodName());
      Class<?> clazz = Class.forName(model.getEntityTypeClassName());
      return new AutoValue_ProtobufImport_Relation(
          (Access<?, ?>) m.invoke(db), CodecFactory.encoder(clazz));
    }

    abstract Access<?, ?> access();

    abstract ProtobufCodec<?> codec();
  }
}
