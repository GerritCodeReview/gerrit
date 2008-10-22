// Copyright 2008 Google Inc.
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

package com.google.codereview.manager.unpack;

import com.google.codereview.internal.CompletePatchset.CompletePatchsetRequest;
import com.google.codereview.internal.CompletePatchset.CompletePatchsetResponse;
import com.google.codereview.internal.UploadPatchsetFile.UploadPatchsetFileRequest;
import com.google.codereview.internal.UploadPatchsetFile.UploadPatchsetFileResponse;
import com.google.codereview.manager.Backend;
import com.google.codereview.manager.StopProcessingException;
import com.google.codereview.rpc.SimpleController;
import com.google.protobuf.ByteString;
import com.google.protobuf.RpcCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectLoader;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;


class PatchSetUploader implements Runnable {
  private static final Log LOG = LogFactory.getLog(PatchSetUploader.class);
  private static final int MAX_DATA_SIZE = 1022 * 1024; // bytes
  private static final String EMPTY_BLOB_ID;
  private static final ByteString EMPTY_DEFLATE;

  static {
    final MessageDigest md = Constants.newMessageDigest();
    md.update(Constants.encodeASCII("blob 0\0"));
    EMPTY_BLOB_ID = ObjectId.fromRaw(md.digest()).name();
    EMPTY_DEFLATE = deflate(new byte[0]);
  }

  private final Backend server;
  private final Repository db;
  private final RevCommit commit;
  private final String commitName;
  private final String patchsetKey;
  private ByteString.Output compressedFilenames;
  private Writer filenameOut;

  PatchSetUploader(final Backend be, final Repository sourceRepo,
      final RevCommit sourceCommit, final String destPatchsetKey) {
    server = be;
    db = sourceRepo;
    commit = sourceCommit;
    commitName = commit.getId().name();
    patchsetKey = destPatchsetKey;
  }

  private String logkey() {
    return db.getDirectory().getAbsolutePath() + " " + commitName;
  }

  public void run() {
    LOG.debug(logkey() + " begin");
    try {
      runImpl();
    } catch (RuntimeException e) {
      LOG.fatal(logkey() + " failure", e);
    } catch (Error e) {
      LOG.fatal(logkey() + " failure", e);
    }
  }

  private void runImpl() {
    try {
      compressedFilenames = ByteString.newOutput();
      filenameOut =
          new OutputStreamWriter(new DeflaterOutputStream(compressedFilenames,
              new Deflater(Deflater.DEFAULT_COMPRESSION)), "UTF-8");
    } catch (IOException e) {
      LOG.error(logkey() + " cannot initialize filename compression", e);
      return;
    }

    try {
      final DiffReader dr = new DiffReader(db, commit);
      try {
        boolean first = true;
        FileDiff file;
        while ((file = dr.next()) != null) {
          storeOneDiff(file);

          if (first) {
            first = false;
          } else {
            filenameOut.write('\0');
          }
          filenameOut.write(file.getFilename());
        }
      } finally {
        dr.close();
      }
      filenameOut.close();
    } catch (StopProcessingException halt) {
      return;
    } catch (IOException err) {
      LOG.error(logkey() + " diff failed", err);
      return;
    }

    final CompletePatchsetRequest.Builder req;
    req = CompletePatchsetRequest.newBuilder();
    req.setPatchsetKey(patchsetKey);
    req.setCompressedFilenames(compressedFilenames.toByteString());
    final SimpleController ctrl = new SimpleController();
    server.getChangeService().completePatchset(ctrl, req.build(),
        new RpcCallback<CompletePatchsetResponse>() {
          public void run(final CompletePatchsetResponse rsp) {
            LOG.debug(logkey() + " complete");
          }
        });
    if (ctrl.failed()) {
      final String why = ctrl.errorText();
      LOG.error(logkey() + " completing failed: " + why);
    }
  }

  private void storeOneDiff(final FileDiff diff) throws StopProcessingException {
    final UploadPatchsetFileRequest req = toFileRequest(diff);
    final SimpleController ctrl = new SimpleController();
    server.getChangeService().uploadPatchsetFile(ctrl, req,
        new RpcCallback<UploadPatchsetFileResponse>() {
          public void run(final UploadPatchsetFileResponse rsp) {
            final UploadPatchsetFileResponse.CodeType sc = rsp.getStatusCode();
            final String fn = req.getFileName();
            final String pk = req.getPatchsetKey();

            if (sc == UploadPatchsetFileResponse.CodeType.CREATED) {
              LOG.debug(logkey() + " uploaded " + fn);
            } else if (sc == UploadPatchsetFileResponse.CodeType.CLOSED) {
              ctrl.setFailed("patchset closed " + pk);
            } else if (sc == UploadPatchsetFileResponse.CodeType.UNKNOWN_PATCHSET) {
              ctrl.setFailed("patchset unknown " + pk);
            } else if (sc == UploadPatchsetFileResponse.CodeType.PATCHING_ERROR) {
              ctrl.setFailed("server cannot apply patch");
            } else {
              ctrl.setFailed("Unknown status " + sc.name() + " " + pk);
            }
          }
        });
    if (ctrl.failed()) {
      final String fn = req.getFileName();
      final String why = ctrl.errorText();
      LOG.error(logkey() + " uploading " + fn + " failed: " + why);
      throw new StopProcessingException(why);
    }
  }

  private UploadPatchsetFileRequest toFileRequest(final FileDiff diff) {
    final UploadPatchsetFileRequest.Builder req;

    req = UploadPatchsetFileRequest.newBuilder();
    req.setPatchsetKey(patchsetKey);
    req.setFileName(diff.getFilename());
    req.setStatus(diff.getStatus());

    if (!diff.isBinary() && !diff.isTruncated()) {
      final ObjectId baseId = diff.getBaseId();

      if (baseId == null || ObjectId.equals(baseId, ObjectId.zeroId())) {
        req.setBaseId(EMPTY_BLOB_ID);
        req.setBaseZ(EMPTY_DEFLATE);
      } else {
        try {
          final ObjectLoader ldr = db.openBlob(baseId);
          if (ldr == null) {
            LOG.fatal(logkey() + " missing " + baseId.name());
            throw new StopProcessingException("No " + baseId.name());
          }

          final byte[] base = ldr.getCachedBytes();
          if (base.length + diff.getPatchSize() > MAX_DATA_SIZE) {
            diff.truncatePatch();
          } else {
            req.setBaseId(baseId.name());
            req.setBaseZ(deflate(base));
          }
        } catch (IOException err) {
          LOG.fatal(logkey() + " cannot read base " + baseId.name(), err);
          throw new StopProcessingException("No " + baseId.name());
        }
      }
    }

    if (!diff.isBinary() && !diff.isTruncated()) {
      final ObjectId finalId = diff.getFinalId();
      if (finalId == null || ObjectId.equals(finalId, ObjectId.zeroId())) {
        req.setFinalId(EMPTY_BLOB_ID);
      } else {
        req.setFinalId(finalId.name());
      }
    }

    final byte[] rawpatch = diff.getPatch();
    req.setPatchZ(deflate(rawpatch));
    req.setPatchId(hashOf(rawpatch));

    return req.build();
  }

  private static ByteString deflate(final byte[] buf) {
    final ByteString.Output r = ByteString.newOutput();
    final DeflaterOutputStream out = new DeflaterOutputStream(r);
    try {
      out.write(buf);
      out.close();
    } catch (IOException err) {
      // This should not happen.
      throw new StopProcessingException("Unexpected IO error", err);
    }
    return r.toByteString();
  }

  private static String hashOf(final byte[] in) {
    final MessageDigest md = Constants.newMessageDigest();
    md.update(in, 0, in.length);
    return ObjectId.fromRaw(md.digest()).name();
  }
}
