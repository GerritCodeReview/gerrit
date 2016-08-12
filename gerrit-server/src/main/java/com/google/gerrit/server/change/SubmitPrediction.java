// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static java.nio.charset.StandardCharsets.UTF_8;
import org.eclipse.jgit.lib.NullProgressMonitor;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeOpRepoManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gson.stream.JsonWriter;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.BundleWriter;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Singleton
public class SubmitPrediction implements
RestReadView<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(SubmitPrediction.class);

  private final Provider<ReviewDb> dbProvider;
  private final Provider<MergeOp> mergeOpProvider;

  @Inject
  SubmitPrediction(Provider<ReviewDb> dbProvider,
      Provider<MergeOp> mergeOpProvider) {
    this.dbProvider = dbProvider;
    this.mergeOpProvider = mergeOpProvider;
  }

  @Override
  public BinaryResult apply(RevisionResource rsrc) {
    BinaryResult bin;
    final Change change = rsrc.getChange();
    if (!change.getStatus().isOpen()) {
      bin = reject("change is " + Submit.status(change));
    } else {
      try {
        SubmitInput input = new SubmitInput();
        bin = getBundles(rsrc, input);
        log.debug("got the bundle");
      } catch (OrmException | RestApiException e) {
        bin = reject(e.getMessage());
      }
    }

    log.debug("setting up for return");
    bin.disableGzip()
      .setContentType("application/zip")
      .setAttachmentName("submit-prediction" + change.getChangeId() + ".zip");
    return bin;
  }

  private void addIndex(HashMap<String, Project.NameKey> map,
      ZipOutputStream zos) throws IOException {
    ZipEntry e = new ZipEntry("index.json");
    zos.putNextEntry(e);
    try (Writer w = new OutputStreamWriter(zos, UTF_8);
        JsonWriter json = new JsonWriter(w)) {
      json.beginObject();
      json.name("bundles");
      json.beginArray();
      for (String s : map.keySet()) {
        json.beginObject();
        json.name("project").value(map.get(s).get());
        json.name("filename").value(s);
        json.endObject();
      }
      json.endArray();
      json.endObject();
      json.flush();
      //json.close();
      zos.closeEntry();
      zos.finish();
    }
  }

  private BinaryResult getBundles(RevisionResource rsrc,
      final SubmitInput input) throws OrmException, RestApiException {
    ReviewDb db = dbProvider.get();
    ChangeControl control = rsrc.getControl();
    final IdentifiedUser caller = control.getUser().asIdentifiedUser();
    final HashMap<String, Project.NameKey> bundleIndex = new HashMap<>();
    final Change change = rsrc.getChange();

    BinaryResult bin;
    try (final MergeOp op = mergeOpProvider.get()) {
      op.merge(db, change, caller, true, input, true);
      final MergeOpRepoManager orm = op.getMergeOpRepoManager();
      final Set<Project.NameKey> projects = op.getAllProjects();
      bin = new BinaryResult() {
        @Override
        public void writeTo(OutputStream out) throws IOException {
          int bundleCounter = 0;
          ZipOutputStream zos = new ZipOutputStream(out);
          for (Project.NameKey p : projects) {
            Collection<Project.NameKey> c = new HashSet<>();
            BundleWriter bw = new BundleWriter(orm.getRepo(p).repo);
            log.debug("bw for "+ p);
            bw.setObjectCountCallback(null);
            bw.setPackConfig(null);
            for (BatchUpdate u : orm.batchUpdates(c)) {
              log.debug("u for "+ u);
              List<ReceiveCommand> refs = u.getRefUpdates();
              for (ReceiveCommand r : refs) {
                log.debug("r "+ r);
                bw.include(r.getRefName(), r.getNewId());
                bw.assume((RevCommit) r.getOldId());
              }
            }
            String fname = "git-" + String.valueOf(bundleCounter) + ".bundle";
            bundleCounter++;
            bundleIndex.put(fname, p);
            ZipEntry e = new ZipEntry(fname);
            zos.putNextEntry(e);
            bw.writeBundle(NullProgressMonitor.INSTANCE, zos);
            zos.closeEntry();
          }
          addIndex(bundleIndex, zos);
        }
      };
    }
    return bin;
  }

  private BinaryResult reject(final String message) {
    BinaryResult bin = new BinaryResult() {
      @Override
      public void writeTo(OutputStream out) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(out);
        try (Writer w = new OutputStreamWriter(zos, UTF_8);
            JsonWriter json = new JsonWriter(w)) {
          zos.putNextEntry(new ZipEntry("index.json"));
          json.beginObject();
          json.name("error").value(message);
          json.endObject();
          json.flush();
          zos.closeEntry();
          zos.finish();
        }
      }
    };
    return bin;
  }
}
