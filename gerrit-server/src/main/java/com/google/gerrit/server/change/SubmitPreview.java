// Copyright (C) 2016 The Android Open Source Project
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

import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;

import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeOpRepoManager;
import com.google.gerrit.server.git.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.BundleWriter;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Singleton
public class SubmitPreview implements RestReadView<RevisionResource> {
  private final Provider<ReviewDb> dbProvider;
  private final Provider<MergeOp> mergeOpProvider;

  @Inject
  SubmitPreview(Provider<ReviewDb> dbProvider,
      Provider<MergeOp> mergeOpProvider) {
    this.dbProvider = dbProvider;
    this.mergeOpProvider = mergeOpProvider;
  }

  @Override
  public BinaryResult apply(RevisionResource rsrc) {
    final Change change = rsrc.getChange();
    if (!change.getStatus().isOpen()) {
      return reject("change is " + Submit.status(change));
    }
    ChangeControl control = rsrc.getControl();
    if (!control.getUser().isIdentifiedUser()) {
      return reject("Anonymous users cannot submit");
    }
    try (BinaryResult b = getBundles(rsrc, new SubmitInput());) {
      b.disableGzip()
          .setContentType("application/zip")
          .setAttachmentName("submit-preview-"
              + change.getChangeId() + ".zip");
      return b;
    } catch (OrmException | RestApiException | IOException e) {
      return reject(e.getMessage());
    }
  }

  private BinaryResult getBundles(RevisionResource rsrc,
      final SubmitInput input) throws OrmException, RestApiException {
    ReviewDb db = dbProvider.get();
    ChangeControl control = rsrc.getControl();
    final IdentifiedUser caller = control.getUser().asIdentifiedUser();
    final Change change = rsrc.getChange();

    BinaryResult bin;
    try (final MergeOp op = mergeOpProvider.get()) {
      op.merge(db, change, caller, false, input, true);
      final MergeOpRepoManager orm = op.getMergeOpRepoManager();
      final Set<Project.NameKey> projects = op.getAllProjects();

      bin = new BinaryResult() {
        @Override
        public void writeTo(OutputStream out) throws IOException {
          ZipOutputStream zos = new ZipOutputStream(out);
          for (Project.NameKey p : projects) {
            OpenRepo or = orm.getRepo(p);
            BundleWriter bw = new BundleWriter(or.getRepo());
            bw.setObjectCountCallback(null);
            bw.setPackConfig(null);
            Collection<ReceiveCommand> refs = or.getUpdate().getRefUpdates();
            for (ReceiveCommand r : refs) {
              bw.include(r.getRefName(), r.getNewId());
              if (!r.getOldId().equals(ObjectId.zeroId())) {
                bw.assume((RevCommit) r.getOldId());
              }
            }
            // This naming scheme cannot produce directory/file conflicts
            // as no projects contains ".git/":
            String fname = p.get() + ".git";
            ZipEntry e = new ZipEntry(fname);
            zos.putNextEntry(e);
            bw.writeBundle(NullProgressMonitor.INSTANCE, zos);
            zos.closeEntry();
          }
          zos.finish();
        }
      };
    }
    return bin;
  }

  private BinaryResult reject(final String message) {
    return BinaryResult.create(message + "\n");
  }
}
