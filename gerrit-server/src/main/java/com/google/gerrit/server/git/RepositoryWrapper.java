// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
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
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectDatabase;
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
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FS;

public class RepositoryWrapper extends Repository {
  private static BaseRepositoryBuilder<?, ?> builder(Repository r) {
    checkNotNull(r);
    BaseRepositoryBuilder<?, ?> builder =
        new BaseRepositoryBuilder<>().setFS(r.getFS()).setGitDir(r.getDirectory());

    if (!r.isBare()) {
      builder.setWorkTree(r.getWorkTree()).setIndexFile(r.getIndexFile());
    }
    return builder;
  }

  protected final Repository delegate;
  protected final RefDatabaseWrapper refdb;
  protected final ObjectDatabaseWrapper objdb;

  protected RepositoryWrapper(Repository delegate) {
    super(builder(delegate));
    this.delegate = delegate;
    this.refdb = wrapRefDatabase(delegate.getRefDatabase());
    this.objdb = wrapObjectDatabase(delegate.getObjectDatabase());
  }

  protected RefDatabaseWrapper wrapRefDatabase(RefDatabase delegate) {
    return new RefDatabaseWrapper(delegate);
  }

  protected ObjectDatabaseWrapper wrapObjectDatabase(ObjectDatabase delegate) {
    return new ObjectDatabaseWrapper(delegate);
  }

  @Override
  public ObjectDatabaseWrapper getObjectDatabase() {
    return objdb;
  }

  @Override
  public RefDatabaseWrapper getRefDatabase() {
    return refdb;
  }

  @Override
  public ListenerList getListenerList() {
    return delegate.getListenerList();
  }

  @Override
  public void fireEvent(RepositoryEvent<?> event) {
    delegate.fireEvent(event);
  }

  @Override
  public void create() throws IOException {
    delegate.create();
  }

  @Override
  public void create(boolean bare) throws IOException {
    delegate.create(bare);
  }

  @Override
  public File getDirectory() {
    return delegate.getDirectory();
  }

  @Override
  public ObjectInserter newObjectInserter() {
    return delegate.newObjectInserter();
  }

  @Override
  public ObjectReader newObjectReader() {
    return delegate.newObjectReader();
  }

  @Override
  public StoredConfig getConfig() {
    return delegate.getConfig();
  }

  @Override
  public AttributesNodeProvider createAttributesNodeProvider() {
    return delegate.createAttributesNodeProvider();
  }

  @Override
  public FS getFS() {
    return delegate.getFS();
  }

  @Override
  public boolean hasObject(AnyObjectId objectId) {
    return delegate.hasObject(objectId);
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId) throws MissingObjectException, IOException {
    return delegate.open(objectId);
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId, int typeHint)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    return delegate.open(objectId, typeHint);
  }

  @Override
  public RefUpdate updateRef(String ref) throws IOException {
    return delegate.updateRef(ref);
  }

  @Override
  public RefUpdate updateRef(String ref, boolean detach) throws IOException {
    return delegate.updateRef(ref, detach);
  }

  @Override
  public RefRename renameRef(String fromRef, String toRef) throws IOException {
    return delegate.renameRef(fromRef, toRef);
  }

  @Override
  public ObjectId resolve(String revstr)
      throws AmbiguousObjectException, IncorrectObjectTypeException, RevisionSyntaxException,
          IOException {
    return delegate.resolve(revstr);
  }

  @Override
  public String simplify(String revstr) throws AmbiguousObjectException, IOException {
    return delegate.simplify(revstr);
  }

  @Override
  public void incrementOpen() {
    delegate.incrementOpen();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(delegate).toString();
  }

  @Override
  public String getFullBranch() throws IOException {
    return delegate.getFullBranch();
  }

  @Override
  public String getBranch() throws IOException {
    return delegate.getBranch();
  }

  @Override
  public Set<ObjectId> getAdditionalHaves() {
    return delegate.getAdditionalHaves();
  }

  @SuppressWarnings("deprecation")
  @Override
  public Ref getRef(String name) throws IOException {
    return delegate.getRef(name);
  }

  @Override
  public Ref exactRef(String name) throws IOException {
    return delegate.exactRef(name);
  }

  @Override
  public Ref findRef(String name) throws IOException {
    return delegate.findRef(name);
  }

  @Override
  public Map<String, Ref> getAllRefs() {
    return delegate.getAllRefs();
  }

  @Override
  public Map<String, Ref> getTags() {
    return delegate.getTags();
  }

  @Override
  public Ref peel(Ref ref) {
    return delegate.peel(ref);
  }

  @Override
  public Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId() {
    return delegate.getAllRefsByPeeledObjectId();
  }

  @Override
  public File getIndexFile() throws NoWorkTreeException {
    return delegate.getIndexFile();
  }

  @Override
  public DirCache readDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return delegate.readDirCache();
  }

  @Override
  public DirCache lockDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return delegate.lockDirCache();
  }

  @Override
  public RepositoryState getRepositoryState() {
    return delegate.getRepositoryState();
  }

  @Override
  public boolean isBare() {
    return delegate.isBare();
  }

  @Override
  public File getWorkTree() throws NoWorkTreeException {
    return delegate.getWorkTree();
  }

  @Override
  public void scanForRepoChanges() throws IOException {
    delegate.scanForRepoChanges();
  }

  @Override
  public void notifyIndexChanged() {
    delegate.notifyIndexChanged();
  }

  @Override
  public String shortenRemoteBranchName(String refName) {
    return delegate.shortenRemoteBranchName(refName);
  }

  @Override
  public String getRemoteName(String refName) {
    return delegate.getRemoteName(refName);
  }

  @Override
  public String getGitwebDescription() throws IOException {
    return delegate.getGitwebDescription();
  }

  @Override
  public void setGitwebDescription(String description) throws IOException {
    delegate.setGitwebDescription(description);
  }

  @Override
  public ReflogReader getReflogReader(String refName) throws IOException {
    return delegate.getReflogReader(refName);
  }

  @Override
  public String readMergeCommitMsg() throws IOException, NoWorkTreeException {
    return delegate.readCommitEditMsg();
  }

  @Override
  public void writeMergeCommitMsg(String msg) throws IOException {
    delegate.writeMergeCommitMsg(msg);
  }

  @Override
  public String readCommitEditMsg() throws IOException, NoWorkTreeException {
    return delegate.readCommitEditMsg();
  }

  @Override
  public void writeCommitEditMsg(String msg) throws IOException {
    delegate.writeCommitEditMsg(msg);
  }

  @Override
  public List<ObjectId> readMergeHeads() throws IOException, NoWorkTreeException {
    return delegate.readMergeHeads();
  }

  @Override
  public void writeMergeHeads(List<? extends ObjectId> heads) throws IOException {
    delegate.writeMergeHeads(heads);
  }

  @Override
  public ObjectId readCherryPickHead() throws IOException, NoWorkTreeException {
    return delegate.readCherryPickHead();
  }

  @Override
  public ObjectId readRevertHead() throws IOException, NoWorkTreeException {
    return delegate.readRevertHead();
  }

  @Override
  public void writeCherryPickHead(ObjectId head) throws IOException {
    delegate.writeCherryPickHead(head);
  }

  @Override
  public void writeRevertHead(ObjectId head) throws IOException {
    delegate.writeRevertHead(head);
  }

  @Override
  public void writeOrigHead(ObjectId head) throws IOException {
    delegate.writeOrigHead(head);
  }

  @Override
  public ObjectId readOrigHead() throws IOException, NoWorkTreeException {
    return delegate.readOrigHead();
  }

  @Override
  public String readSquashCommitMsg() throws IOException {
    return delegate.readSquashCommitMsg();
  }

  @Override
  public void writeSquashCommitMsg(String msg) throws IOException {
    delegate.writeSquashCommitMsg(msg);
  }

  @Override
  public List<RebaseTodoLine> readRebaseTodo(String path, boolean includeComments)
      throws IOException {
    return delegate.readRebaseTodo(path, includeComments);
  }

  @Override
  public void writeRebaseTodoFile(String path, List<RebaseTodoLine> steps, boolean append)
      throws IOException {
    delegate.writeRebaseTodoFile(path, steps, append);
  }

  @Override
  public Set<String> getRemoteNames() {
    return delegate.getRemoteNames();
  }

  @Override
  public void autoGC(ProgressMonitor monitor) {
    delegate.autoGC(monitor);
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return o.getClass() == getClass() && delegate.equals(((RepositoryWrapper) o).delegate);
  }

  public static class RefDatabaseWrapper extends RefDatabase {
    private final RefDatabase delegate;

    protected RefDatabaseWrapper(RefDatabase delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public void create() throws IOException {
      delegate.create();
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public boolean isNameConflicting(String name) throws IOException {
      return delegate.isNameConflicting(name);
    }

    @Override
    public Collection<String> getConflictingNames(String name) throws IOException {
      return delegate.getConflictingNames(name);
    }

    @Override
    public RefUpdate newUpdate(String name, boolean detach) throws IOException {
      return delegate.newUpdate(name, detach);
    }

    @Override
    public RefRename newRename(String fromName, String toName) throws IOException {
      return delegate.newRename(fromName, toName);
    }

    @Override
    public BatchRefUpdate newBatchUpdate() {
      return delegate.newBatchUpdate();
    }

    @Override
    public boolean performsAtomicTransactions() {
      return delegate.performsAtomicTransactions();
    }

    @Override
    public Ref getRef(String name) throws IOException {
      return delegate.getRef(name);
    }

    @Override
    public Ref exactRef(String name) throws IOException {
      return delegate.exactRef(name);
    }

    @Override
    public Map<String, Ref> exactRef(String... refs) throws IOException {
      return delegate.exactRef(refs);
    }

    @Override
    public Ref firstExactRef(String... refs) throws IOException {
      return delegate.firstExactRef(refs);
    }

    @Override
    public Map<String, Ref> getRefs(String prefix) throws IOException {
      return delegate.getRefs(prefix);
    }

    @Override
    public List<Ref> getAdditionalRefs() throws IOException {
      return delegate.getAdditionalRefs();
    }

    @Override
    public Ref peel(Ref ref) throws IOException {
      return delegate.peel(ref);
    }

    @Override
    public void refresh() {
      delegate.refresh();
    }

    @Override
    public int hashCode() {
      return delegate.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      return getClass().equals(o.getClass()) && delegate.equals(((RefDatabaseWrapper) o).delegate);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).addValue(delegate).toString();
    }
  }

  public static class ObjectDatabaseWrapper extends ObjectDatabase {
    private final ObjectDatabase delegate;

    protected ObjectDatabaseWrapper(ObjectDatabase delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public boolean exists() {
      return delegate.exists();
    }

    @Override
    public void create() throws IOException {
      delegate.create();
    }

    @Override
    public ObjectInserter newInserter() {
      return delegate.newInserter();
    }

    @Override
    public ObjectReader newReader() {
      return delegate.newReader();
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public boolean has(AnyObjectId objectId) throws IOException {
      return delegate.has(objectId);
    }

    @Override
    public ObjectLoader open(AnyObjectId objectId) throws IOException {
      return delegate.open(objectId);
    }

    @Override
    public ObjectLoader open(AnyObjectId objectId, int typeHint)
        throws MissingObjectException, IncorrectObjectTypeException, IOException {
      return delegate.open(objectId, typeHint);
    }

    @Override
    public ObjectDatabase newCachedDatabase() {
      return delegate.newCachedDatabase();
    }

    @Override
    public int hashCode() {
      return delegate.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      return getClass() == o.getClass() && delegate.equals(((ObjectDatabaseWrapper) o).delegate);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).addValue(delegate).toString();
    }
  }
}
