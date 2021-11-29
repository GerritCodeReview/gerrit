// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RebaseTodoLine;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;

class CachedRefRepository extends DelegateRepository {
  @Singleton
  static class Factory implements LocalDiskRepositoryManager.RepoWrapperFactory {
    private final CachedRefDatabase.Factory refDbFactory;
    private final RefUpdateWithCacheUpdate.Factory updateFactory;
    private final RefRenameWithCacheUpdate.Factory renameFactory;

    @Inject
    Factory(
        CachedRefDatabase.Factory refDbFactory,
        RefUpdateWithCacheUpdate.Factory updateFactory,
        RefRenameWithCacheUpdate.Factory renameFactory) {
      this.refDbFactory = refDbFactory;
      this.updateFactory = updateFactory;
      this.renameFactory = renameFactory;
    }

    @Override
    public Repository create(Repository repo) {
      return new CachedRefRepository(refDbFactory, updateFactory, renameFactory, repo);
    }
  }

  private final CachedRefDatabase refDb;
  private final RefUpdateWithCacheUpdate.Factory updateFactory;
  private final RefRenameWithCacheUpdate.Factory renameFactory;

  CachedRefRepository(
      CachedRefDatabase.Factory refDbFactory,
      RefUpdateWithCacheUpdate.Factory updateFactory,
      RefRenameWithCacheUpdate.Factory renameFactory,
      Repository repo) {
    super(repo);
    this.updateFactory = updateFactory;
    this.renameFactory = renameFactory;
    this.refDb = refDbFactory.create(this);
  }

  @Override
  public RefDatabase getRefDatabase() {
    return refDb;
  }

  @Override
  public ListenerList getListenerList() {
    return getDelegate().getListenerList();
  }

  @Override
  public void fireEvent(RepositoryEvent<?> event) {
    getDelegate().fireEvent(event);
  }

  @Override
  public File getDirectory() {
    return getDelegate().getDirectory();
  }

  @Override
  public ObjectInserter newObjectInserter() {
    return getDelegate().newObjectInserter();
  }

  @Override
  public ObjectReader newObjectReader() {
    return getDelegate().newObjectReader();
  }

  @Override
  public FS getFS() {
    return getDelegate().getFS();
  }

  @Deprecated
  @Override
  public boolean hasObject(AnyObjectId objectId) {
    return getDelegate().hasObject(objectId);
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId) throws MissingObjectException, IOException {
    return getDelegate().open(objectId);
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId, int typeHint)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    return getDelegate().open(objectId, typeHint);
  }

  @Override
  public RefUpdate updateRef(String ref) throws IOException {
    return updateFactory.create(refDb, this, getDelegate().updateRef(ref));
  }

  @Override
  public RefUpdate updateRef(String ref, boolean detach) throws IOException {
    return updateFactory.create(refDb, this, getDelegate().updateRef(ref, detach));
  }

  @Override
  public RefRename renameRef(String fromRef, String toRef) throws IOException {
    return renameFactory.create(
        this, getDelegate().renameRef(fromRef, toRef), updateRef(fromRef), updateRef(toRef));
  }

  @Override
  public ObjectId resolve(String revstr)
      throws AmbiguousObjectException, IncorrectObjectTypeException, RevisionSyntaxException,
          IOException {
    return getDelegate().resolve(revstr);
  }

  @Override
  public String simplify(String revstr) throws AmbiguousObjectException, IOException {
    return getDelegate().simplify(revstr);
  }

  @Override
  public void incrementOpen() {
    getDelegate().incrementOpen();
  }

  @Override
  public void close() {
    getDelegate().close();
  }

  @Override
  public String getFullBranch() throws IOException {
    return getDelegate().getFullBranch();
  }

  @Override
  public String getBranch() throws IOException {
    return getDelegate().getBranch();
  }

  @Override
  public Set<ObjectId> getAdditionalHaves() {
    return getDelegate().getAdditionalHaves();
  }

  @Deprecated
  @Override
  public Map<String, Ref> getAllRefs() {
    return getDelegate().getAllRefs();
  }

  @Deprecated
  @Override
  public Map<String, Ref> getTags() {
    return getDelegate().getTags();
  }

  @Deprecated
  @Override
  public Ref peel(Ref ref) {
    return getDelegate().peel(ref);
  }

  @Override
  public Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId() {
    return getDelegate().getAllRefsByPeeledObjectId();
  }

  @Override
  public File getIndexFile() throws NoWorkTreeException {
    return getDelegate().getIndexFile();
  }

  @Override
  public RevCommit parseCommit(AnyObjectId id)
      throws IncorrectObjectTypeException, IOException, MissingObjectException {
    return getDelegate().parseCommit(id);
  }

  @Override
  public DirCache readDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return getDelegate().readDirCache();
  }

  @Override
  public DirCache lockDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return getDelegate().lockDirCache();
  }

  @Override
  public RepositoryState getRepositoryState() {
    return getDelegate().getRepositoryState();
  }

  @Override
  public boolean isBare() {
    return getDelegate().isBare();
  }

  @Override
  public File getWorkTree() throws NoWorkTreeException {
    return getDelegate().getWorkTree();
  }

  @Override
  public String shortenRemoteBranchName(String refName) {
    return getDelegate().shortenRemoteBranchName(refName);
  }

  @Override
  public String getRemoteName(String refName) {
    return getDelegate().getRemoteName(refName);
  }

  @Override
  public String getGitwebDescription() throws IOException {
    return getDelegate().getGitwebDescription();
  }

  @Override
  public void setGitwebDescription(String description) throws IOException {
    getDelegate().setGitwebDescription(description);
  }

  @Override
  public String readMergeCommitMsg() throws IOException, NoWorkTreeException {
    return getDelegate().readMergeCommitMsg();
  }

  @Override
  public void writeMergeCommitMsg(String msg) throws IOException {
    getDelegate().writeMergeCommitMsg(msg);
  }

  @Override
  public String readCommitEditMsg() throws IOException, NoWorkTreeException {
    return getDelegate().readCommitEditMsg();
  }

  @Override
  public void writeCommitEditMsg(String msg) throws IOException {
    getDelegate().writeCommitEditMsg(msg);
  }

  @Override
  public List<ObjectId> readMergeHeads() throws IOException, NoWorkTreeException {
    return getDelegate().readMergeHeads();
  }

  @Override
  public void writeMergeHeads(List<? extends ObjectId> heads) throws IOException {
    getDelegate().writeMergeHeads(heads);
  }

  @Override
  public ObjectId readCherryPickHead() throws IOException, NoWorkTreeException {
    return getDelegate().readCherryPickHead();
  }

  @Override
  public ObjectId readRevertHead() throws IOException, NoWorkTreeException {
    return getDelegate().readRevertHead();
  }

  @Override
  public void writeCherryPickHead(ObjectId head) throws IOException {
    getDelegate().writeCherryPickHead(head);
  }

  @Override
  public void writeRevertHead(ObjectId head) throws IOException {
    getDelegate().writeRevertHead(head);
  }

  @Override
  public void writeOrigHead(ObjectId head) throws IOException {
    getDelegate().writeOrigHead(head);
  }

  @Override
  public ObjectId readOrigHead() throws IOException, NoWorkTreeException {
    return getDelegate().readOrigHead();
  }

  @Override
  public String readSquashCommitMsg() throws IOException {
    return getDelegate().readSquashCommitMsg();
  }

  @Override
  public void writeSquashCommitMsg(String msg) throws IOException {
    getDelegate().writeSquashCommitMsg(msg);
  }

  @Override
  public List<RebaseTodoLine> readRebaseTodo(String path, boolean includeComments)
      throws IOException {
    return getDelegate().readRebaseTodo(path, includeComments);
  }

  @Override
  public void writeRebaseTodoFile(String path, List<RebaseTodoLine> steps, boolean append)
      throws IOException {
    getDelegate().writeRebaseTodoFile(path, steps, append);
  }

  @Override
  public Set<String> getRemoteNames() {
    return getDelegate().getRemoteNames();
  }

  @Override
  public void autoGC(ProgressMonitor monitor) {
    getDelegate().autoGC(monitor);
  }

  @Override
  public void create() throws IOException {
    getDelegate().create();
  }
}
