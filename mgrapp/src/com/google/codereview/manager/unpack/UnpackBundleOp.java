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

import com.google.codereview.internal.AddPatchset.AddPatchSetRequest;
import com.google.codereview.internal.AddPatchset.AddPatchSetResponse;
import com.google.codereview.internal.NextReceivedBundle.BundleSegmentRequest;
import com.google.codereview.internal.NextReceivedBundle.BundleSegmentResponse;
import com.google.codereview.internal.NextReceivedBundle.NextReceivedBundleResponse;
import com.google.codereview.internal.NextReceivedBundle.ReplacePatchSet;
import com.google.codereview.internal.SubmitChange.SubmitChangeRequest;
import com.google.codereview.internal.SubmitChange.SubmitChangeResponse;
import com.google.codereview.internal.UpdateReceivedBundle.UpdateReceivedBundleRequest;
import com.google.codereview.internal.UpdateReceivedBundle.UpdateReceivedBundleRequest.CodeType;
import com.google.codereview.manager.Backend;
import com.google.codereview.manager.InvalidRepositoryException;
import com.google.codereview.rpc.SimpleController;
import com.google.codereview.util.GitMetaUtil;
import com.google.codereview.util.MutableBoolean;
import com.google.protobuf.RpcCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingBundlePrerequisiteException;
import org.spearce.jgit.lib.NullProgressMonitor;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevSort;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.transport.FetchConnection;
import org.spearce.jgit.transport.Transport;
import org.spearce.jgit.transport.TransportBundleStream;
import org.spearce.jgit.transport.URIish;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unpacks a bundle and imports the commits into the code review system. */
class UnpackBundleOp {
  private static final Log LOG = LogFactory.getLog(UnpackBundleOp.class);

  private static String refOf(final int changeId, final int patchsetId) {
    final StringBuilder b = new StringBuilder();
    final int dh = changeId % 100;
    b.append("refs/changes/");
    if (dh < 10) {
      b.append('0');
    }
    b.append(dh);
    b.append('/');
    b.append(changeId);
    b.append('/');
    b.append(patchsetId);
    return b.toString();
  }

  private final Backend server;
  private final NextReceivedBundleResponse in;
  private final Map<ObjectId, ReplacePatchSet> replacements;
  private Repository db;
  private ObjectId tip;
  private int changeId;
  private int patchsetId;
  private String patchsetKey;

  UnpackBundleOp(final Backend be, final NextReceivedBundleResponse bundleInfo) {
    server = be;
    in = bundleInfo;
    replacements = new HashMap<ObjectId, ReplacePatchSet>();
  }

  UpdateReceivedBundleRequest unpack() {
    final UpdateReceivedBundleRequest.Builder update;
    update = UpdateReceivedBundleRequest.newBuilder();
    update.setBundleKey(in.getBundleKey());
    try {
      unpackImpl();
      update.setStatusCode(CodeType.UNPACKED_OK);
    } catch (UnpackException ue) {
      update.setStatusCode(ue.status);
      update.setErrorDetails(ue.details);
      LOG.error("Unpacking bundle " + in.getBundleKey() + " failed.", ue);
    }
    return update.build();
  }

  private void unpackImpl() throws UnpackException {
    LOG.debug("Unpacking bundle " + in.getBundleKey());
    for (final ReplacePatchSet p : in.getReplaceList()) {
      replacements.put(ObjectId.fromString(p.getObjectId()), p);
    }
    openRepository();
    unpackTip();
    createChanges(newCommits());
  }

  private void openRepository() throws UnpackException {
    try {
      db = server.getRepositoryCache().get(in.getDestProject());
    } catch (InvalidRepositoryException notGit) {
      final String m = "Repository \"" + in.getDestProject() + "\" unknown.";
      throw new UnpackException(CodeType.UNKNOWN_PROJECT, m, notGit);
    }
  }

  private void unpackTip() throws UnpackException {
    final Transport bundleTransport = openBundle();
    try {
      final FetchConnection fc = bundleTransport.openFetch();
      if (fc.getRefs().size() > 1) {
        final String m = "Bundle contains more than one head";
        throw new UnpackException(CodeType.INVALID_BUNDLE, m);
      } else if (fc.getRefs().size() == 0) {
        final String m = "Bundle contains no heads";
        throw new UnpackException(CodeType.INVALID_BUNDLE, m);
      }

      fc.fetch(NullProgressMonitor.INSTANCE, fc.getRefs());
      tip = fc.getRefs().iterator().next().getObjectId();
      LOG.debug("Unpacked " + tip.name() + " from " + in.getBundleKey());
    } catch (MissingBundlePrerequisiteException e) {
      throw new UnpackException(CodeType.MISSING_BASE, e.getMessage(), e);
    } catch (IOException readError) {
      final String m = "Processing the bundle stream failed";
      throw new UnpackException(CodeType.INVALID_BUNDLE, m, readError);
    } finally {
      bundleTransport.close();
    }
  }

  private List<RevCommit> newCommits() throws UnpackException {
    final RevWalk rw = new RevWalk(db);
    rw.sort(RevSort.REVERSE, true);
    rw.sort(RevSort.TOPO, true);

    try {
      rw.markStart(rw.parseCommit(tip));
    } catch (IOException e) {
      final String m = "Chain " + tip.name() + " is corrupt";
      throw new UnpackException(CodeType.INVALID_BUNDLE, m, e);
    }

    for (final Ref r : db.getAllRefs().values()) {
      try {
        rw.markUninteresting(rw.parseCommit(r.getObjectId()));
      } catch (IncorrectObjectTypeException notCommit) {
        // These happen in some repositories like linux-2.6.git where
        // there is an annotated tag pointing at a tree, or in git.git
        // where there is an annotated tag pointing at a blob.
        //
        continue;
      } catch (IOException err) {
        final String m = "Local ref is invalid";
        throw new UnpackException(CodeType.SUSPEND_BUNDLE, m, err);
      }
    }

    try {
      final List<RevCommit> newList = new ArrayList<RevCommit>();
      RevCommit c;
      while ((c = rw.next()) != null) {
        // Ensure the parents are parsed so we know the parent's tree.
        // We need that later to compute a difference.
        //
        for (final RevCommit p : c.getParents()) {
          rw.parse(p);
        }
        newList.add(c);
      }
      return newList;
    } catch (IOException e) {
      final String m = "Chain " + tip.name() + " is corrupt";
      throw new UnpackException(CodeType.INVALID_BUNDLE, m, e);
    }
  }

  private void createChanges(final List<RevCommit> newCommits)
      throws UnpackException {
    for (final RevCommit c : newCommits) {
      final ReplacePatchSet r = replacements.get(c.getId().copy());
      if (r != null) {
        if (addPatchSet(c, r)) {
          createChangeRef(c);
          server.asyncExec(new PatchSetUploader(server, db, c, patchsetKey));
        }
      } else if (submitChange(c)) {
        createChangeRef(c);
        server.asyncExec(new PatchSetUploader(server, db, c, patchsetKey));
      }
    }
  }

  private boolean submitChange(final RevCommit c) throws UnpackException {
    final SubmitChangeRequest.Builder req = SubmitChangeRequest.newBuilder();

    req.setOwner(in.getOwner());
    req.setDestBranchKey(in.getDestBranchKey());
    req.setCommit(GitMetaUtil.toGitCommit(c));
    req.setBundleKey(in.getBundleKey());

    final MutableBoolean continueCreation = new MutableBoolean();
    final SimpleController ctrl = new SimpleController();
    server.getChangeService().submitChange(ctrl, req.build(),
        new RpcCallback<SubmitChangeResponse>() {
          public void run(final SubmitChangeResponse rsp) {
            final SubmitChangeResponse.CodeType sc = rsp.getStatusCode();

            if (sc == SubmitChangeResponse.CodeType.CREATED) {
              changeId = rsp.getChangeId();
              patchsetId = rsp.getPatchsetId();
              patchsetKey = rsp.getPatchsetKey();
              continueCreation.value = true;
              LOG.debug("Commit " + c.getId().name() + " is change " + changeId
                  + " patchset " + patchsetId);

            } else if (sc == SubmitChangeResponse.CodeType.PATCHSET_EXISTS) {
              LOG.debug("Commit " + c.getId().name() + " exists in data store");

            } else {
              ctrl.setFailed("Unknown status " + sc.name());
            }
          }
        });
    if (ctrl.failed()) {
      throw new UnpackException(CodeType.SUSPEND_BUNDLE, ctrl.errorText());
    }
    return continueCreation.value;
  }

  private boolean addPatchSet(final RevCommit c, final ReplacePatchSet replace)
      throws UnpackException {
    final AddPatchSetRequest.Builder req = AddPatchSetRequest.newBuilder();

    req.setOwner(in.getOwner());
    req.setDestBranchKey(in.getDestBranchKey());
    req.setChangeId(replace.getChangeId());
    req.setCommit(GitMetaUtil.toGitCommit(c));

    final MutableBoolean continueCreation = new MutableBoolean();
    final SimpleController ctrl = new SimpleController();
    server.getChangeService().addPatchSet(ctrl, req.build(),
        new RpcCallback<AddPatchSetResponse>() {
          public void run(final AddPatchSetResponse rsp) {
            final AddPatchSetResponse.CodeType sc = rsp.getStatusCode();

            if (sc == AddPatchSetResponse.CodeType.CREATED) {
              changeId = replace.getChangeId();
              patchsetId = rsp.getPatchsetId();
              patchsetKey = rsp.getPatchsetKey();
              continueCreation.value = true;
              LOG.debug("Commit " + c.getId().name() + " is change " + changeId
                  + " patchset " + patchsetId);

            } else if (sc == AddPatchSetResponse.CodeType.PATCHSET_EXISTS) {
              LOG.debug("Commit " + c.getId().name() + " exists in data store");

            } else if (sc == AddPatchSetResponse.CodeType.UNKNOWN_CHANGE) {
              LOG.debug("Change " + req.getChangeId() + " not found");

            } else {
              ctrl.setFailed("Unknown status " + sc.name());
            }
          }
        });
    if (ctrl.failed()) {
      throw new UnpackException(CodeType.SUSPEND_BUNDLE, ctrl.errorText());
    }
    return continueCreation.value;
  }

  private void createChangeRef(final RevCommit c) throws UnpackException {
    final String name = refOf(changeId, patchsetId);
    final RefUpdate.Result r;
    try {
      final RefUpdate u = db.updateRef(name);
      u.setNewObjectId(c.getId());
      u.setForceUpdate(true);
      u.setRefLogMessage("Change submitted", false);
      r = u.update();
    } catch (IOException err) {
      final String m = "Failure creating " + name;
      throw new UnpackException(CodeType.SUSPEND_BUNDLE, m, err);
    }

    if (r == RefUpdate.Result.NEW) {
    } else if (r == RefUpdate.Result.FAST_FORWARD) {
    } else if (r == RefUpdate.Result.FORCED) {
    } else if (r == RefUpdate.Result.NO_CHANGE) {
    } else {
      final String m = "Failure creating " + name + ": " + r.name();
      throw new UnpackException(CodeType.SUSPEND_BUNDLE, m);
    }
  }

  private TransportBundleStream openBundle() {
    final URIish uri = makeURI(in);
    return new TransportBundleStream(db, uri, new BundleStream());
  }

  private static URIish makeURI(final NextReceivedBundleResponse in) {
    URIish u = new URIish();
    u = u.setScheme("codereview-bundle");
    u = u.setPath(in.getBundleKey());
    return u;
  }

  private class BundleStream extends InputStream {
    private int segmentId = 1;
    private final int totalSegments = in.getNSegments();
    private InputStream stream = in.getBundleData().newInput();

    @Override
    public int read() throws IOException {
      for (;;) {
        if (stream == null) {
          return -1;
        }

        final int r = stream.read();
        if (r < 0) {
          openNextStream();
        } else {
          return r;
        }
      }
    }

    @Override
    public int read(final byte[] b, final int off, final int len)
        throws IOException {
      for (;;) {
        if (stream == null) {
          return -1;
        }

        final int r = stream.read(b, off, len);
        if (r < 0) {
          openNextStream();
        } else {
          return r;
        }
      }
    }

    private void openNextStream() throws IOException {
      if (segmentId >= totalSegments) {
        stream = null;
        return;
      }

      segmentId++;
      final BundleSegmentRequest.Builder req;

      req = BundleSegmentRequest.newBuilder();
      req.setBundleKey(in.getBundleKey());
      req.setSegmentId(segmentId);

      final SimpleController ctrl = new SimpleController();
      server.getBundleStoreService().bundleSegment(ctrl, req.build(),
          new RpcCallback<BundleSegmentResponse>() {
            public void run(final BundleSegmentResponse rsp) {
              final BundleSegmentResponse.CodeType sc = rsp.getStatusCode();
              if (sc == BundleSegmentResponse.CodeType.DATA) {
                stream = rsp.getBundleData().newInput();
              } else {
                ctrl.setFailed(sc.name());
              }
            }
          });
      if (ctrl.failed()) {
        throw new IOException("Bundle" + in.getBundleKey() + " segment "
            + segmentId + " unavailable: " + ctrl.errorText());
      }
    }
  }
}
