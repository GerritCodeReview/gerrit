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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ArchiveFormat;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.ioutil.LimitedByteArrayOutputStream;
import com.google.gerrit.server.ioutil.LimitedByteArrayOutputStream.LimitExceededException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.submit.MergeOp;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.BundleWriter;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.kohsuke.args4j.Option;

@Singleton
public class PreviewSubmit implements RestReadView<RevisionResource> {
  private static final int MAX_DEFAULT_BUNDLE_SIZE = 100 * 1024 * 1024;

  private final Provider<MergeOp> mergeOpProvider;
  private final AllowedFormats allowedFormats;
  private int maxBundleSize;
  private String format;

  @Option(name = "--format")
  public void setFormat(String f) {
    this.format = f;
  }

  @Inject
  PreviewSubmit(
      Provider<MergeOp> mergeOpProvider,
      AllowedFormats allowedFormats,
      @GerritServerConfig Config cfg) {
    this.mergeOpProvider = mergeOpProvider;
    this.allowedFormats = allowedFormats;
    this.maxBundleSize = cfg.getInt("download", "maxBundleSize", MAX_DEFAULT_BUNDLE_SIZE);
  }

  @Override
  public Response<BinaryResult> apply(RevisionResource rsrc)
      throws RestApiException, UpdateException, IOException, ConfigInvalidException,
          PermissionBackendException {
    if (Strings.isNullOrEmpty(format)) {
      throw new BadRequestException("format is not specified");
    }
    ArchiveFormat f = allowedFormats.extensions.get("." + format);
    if (f == null && format.equals("tgz")) {
      // Always allow tgz, even when the allowedFormats doesn't contain it.
      // Then we allow at least one format even if the list of allowed
      // formats is empty.
      f = ArchiveFormat.TGZ;
    }
    if (f == null) {
      throw new BadRequestException("unknown archive format");
    }

    Change change = rsrc.getChange();
    if (!change.isNew()) {
      throw new PreconditionFailedException("change is " + ChangeUtil.status(change));
    }
    if (!rsrc.getUser().isIdentifiedUser()) {
      throw new MethodNotAllowedException("Anonymous users cannot submit");
    }

    return Response.ok(getBundles(rsrc, f));
  }

  private BinaryResult getBundles(RevisionResource rsrc, ArchiveFormat f)
      throws RestApiException, UpdateException, IOException, ConfigInvalidException,
          PermissionBackendException {
    IdentifiedUser caller = rsrc.getUser().asIdentifiedUser();
    Change change = rsrc.getChange();

    @SuppressWarnings("resource") // Returned BinaryResult takes ownership and handles closing.
    MergeOp op = mergeOpProvider.get();
    try {
      op.merge(change, caller, false, new SubmitInput(), true);
      BinaryResult bin = new SubmitPreviewResult(op, f, maxBundleSize);
      bin.disableGzip()
          .setContentType(f.getMimeType())
          .setAttachmentName("submit-preview-" + change.getChangeId() + "." + format);
      return bin;
    } catch (RestApiException
        | UpdateException
        | IOException
        | ConfigInvalidException
        | RuntimeException
        | PermissionBackendException e) {
      op.close();
      throw e;
    }
  }

  private static class SubmitPreviewResult extends BinaryResult {

    private final MergeOp mergeOp;
    private final ArchiveFormat archiveFormat;
    private final int maxBundleSize;

    private SubmitPreviewResult(MergeOp mergeOp, ArchiveFormat archiveFormat, int maxBundleSize) {
      this.mergeOp = mergeOp;
      this.archiveFormat = archiveFormat;
      this.maxBundleSize = maxBundleSize;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
      try (ArchiveOutputStream aos = archiveFormat.createArchiveOutputStream(out)) {
        MergeOpRepoManager orm = mergeOp.getMergeOpRepoManager();
        for (Project.NameKey p : mergeOp.getAllProjects()) {
          OpenRepo or = orm.getRepo(p);
          BundleWriter bw = new BundleWriter(or.getCodeReviewRevWalk().getObjectReader());
          bw.setObjectCountCallback(null);
          bw.setPackConfig(new PackConfig(or.getRepo()));
          Collection<ReceiveCommand> refs = or.getUpdate().getRefUpdates().values();
          for (ReceiveCommand r : refs) {
            bw.include(r.getRefName(), r.getNewId());
            ObjectId oldId = r.getOldId();
            if (!oldId.equals(ObjectId.zeroId())
                // Probably the client doesn't already have NoteDb data.
                && !RefNames.isNoteDbMetaRef(r.getRefName())) {
              bw.assume(or.getCodeReviewRevWalk().parseCommit(oldId));
            }
          }
          LimitedByteArrayOutputStream bos = new LimitedByteArrayOutputStream(maxBundleSize, 1024);
          bw.writeBundle(NullProgressMonitor.INSTANCE, bos);
          // This naming scheme cannot produce directory/file conflicts
          // as no projects contains ".git/":
          String path = p.get() + ".git";
          archiveFormat.putEntry(aos, path, bos.toByteArray());
        }
      } catch (LimitExceededException e) {
        throw new NotImplementedException("The bundle is too big to generate at the server");
      } catch (NoSuchProjectException e) {
        throw new IOException(e);
      }
    }

    @Override
    public void close() throws IOException {
      mergeOp.close();
    }
  }
}
