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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
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

import org.eclipse.jgit.transport.BundleWriter;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.kohsuke.args4j.Option;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Singleton
public class SubmitPreview implements RestReadView<RevisionResource> {
  private final Provider<ReviewDb> dbProvider;
  private final Provider<MergeOp> mergeOpProvider;

  private final List<String> allowedFormats;
  private final Date now;

  private String format;

  @Option(name = "--format")
  public void setFormat(String f) {
    this.format = f;
  }

  @Inject
  SubmitPreview(Provider<ReviewDb> dbProvider,
      Provider<MergeOp> mergeOpProvider) {
    this.dbProvider = dbProvider;
    this.mergeOpProvider = mergeOpProvider;
    this.allowedFormats = Arrays.asList("zip", "tar");
    this.now = new Date();
  }

  @Override
  public BinaryResult apply(RevisionResource rsrc) throws RestApiException {
    if (Strings.isNullOrEmpty(format)) {
      throw new BadRequestException("format is not specified");
    }
    if (!allowedFormats.contains(format)) {
      throw new BadRequestException("unknown archive format;" +
          "allowed formats: " + Joiner.on(", ").join(allowedFormats));
    }

    Change change = rsrc.getChange();
    if (!change.getStatus().isOpen()) {
      throw new PreconditionFailedException("change is " + Submit.status(change));
    }
    ChangeControl control = rsrc.getControl();
    if (!control.getUser().isIdentifiedUser()) {
      throw new MethodNotAllowedException("Anonymous users cannot submit");
    }
    try (BinaryResult b = getBundles(rsrc)) {
      b.disableGzip()
          .setContentType(getMimeType())
          .setAttachmentName("submit-preview-"
              + change.getChangeId() + "." + format);
      return b;
    } catch (OrmException | IOException e) {
      throw new RestApiException("Error generating submit preview");
    }
  }

  private ArchiveOutputStream getArchiveOutputStream(String format,
      OutputStream out) {
    if (format == "zip") {
      return new ZipArchiveOutputStream(out);
    } else if (format == "tar") {
      return new TarArchiveOutputStream(out);
    } else {
      return null;
    }
  }

  private String getMimeType() {
    if (format == "zip") {
      return "application/x-zip";
    } else if (format == "tar") {
      return "application/x-tar";
    } else {
      return null;
    }
  }


  private ArchiveEntry prepareArchiveEntry(final String fileName) {
    if (format == "zip") {
      return new ZipArchiveEntry(fileName);
    } else if (format == "tar") {
      return new TarArchiveEntry(fileName);
    } else {
      return null;
    }
  }

  private BinaryResult getBundles(RevisionResource rsrc)
      throws OrmException, RestApiException {
    ReviewDb db = dbProvider.get();
    ChangeControl control = rsrc.getControl();
    IdentifiedUser caller = control.getUser().asIdentifiedUser();
    Change change = rsrc.getChange();


    BinaryResult bin;
    try (MergeOp op = mergeOpProvider.get()) {
      op.merge(db, change, caller, false, new SubmitInput(), true);
      final MergeOpRepoManager orm = op.getMergeOpRepoManager();
      final Set<Project.NameKey> projects = op.getAllProjects();

      bin = new BinaryResult() {
        @Override
        public void writeTo(OutputStream out) throws IOException {
          ArchiveOutputStream aos = getArchiveOutputStream(format, out);

          for (Project.NameKey p : projects) {
            OpenRepo or = orm.getRepo(p);
            BundleWriter bw = new BundleWriter(or.getRepo());
            bw.setObjectCountCallback(null);
            bw.setPackConfig(null);
            Collection<ReceiveCommand> refs = or.getUpdate().getRefUpdates();
            for (ReceiveCommand r : refs) {
              bw.include(r.getRefName(), r.getNewId());
              if (!r.getOldId().equals(ObjectId.zeroId())) {
                bw.assume(or.getCodeReviewRevWalk().parseCommit(r.getOldId()));
              }
            }
            // This naming scheme cannot produce directory/file conflicts
            // as no projects contains ".git/":
            aos.putArchiveEntry(prepareArchiveEntry(p.get() + ".git"));
            bw.writeBundle(NullProgressMonitor.INSTANCE, aos);
            aos.closeArchiveEntry();
          }
          aos.finish();
        }
      };
    }
    return bin;
  }
}
