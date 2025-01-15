// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.common.UsedAt;
import java.io.File;
import java.io.IOException;
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
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;

/** Wrapper around {@link Repository} that delegates all calls to the wrapped {@link Repository}. */
@UsedAt(UsedAt.Project.PLUGIN_HIGH_AVAILABILITY)
@UsedAt(UsedAt.Project.PLUGIN_MULTI_SITE)
public class DelegateRepository extends Repository {

  protected final Repository delegate;

  protected DelegateRepository(Repository delegate) {
    super(toBuilder(delegate));
    this.delegate = delegate;
  }

  /** Returns the wrapped {@link Repository} instance. */
  public Repository delegate() {
    return delegate;
  }

  @Override
  public void create(boolean bare) throws IOException {
    delegate.create(bare);
  }

  @Override
  public String getIdentifier() {
    return delegate.getIdentifier();
  }

  @Override
  public ObjectDatabase getObjectDatabase() {
    return delegate.getObjectDatabase();
  }

  @Override
  public RefDatabase getRefDatabase() {
    return delegate.getRefDatabase();
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
  public void scanForRepoChanges() throws IOException {
    delegate.scanForRepoChanges();
  }

  @Override
  public void notifyIndexChanged(boolean internal) {
    delegate.notifyIndexChanged(internal);
  }

  @Override
  public ReflogReader getReflogReader(String refName) throws IOException {
    return delegate.getReflogReader(refName);
  }

  @SuppressWarnings("rawtypes")
  private static BaseRepositoryBuilder toBuilder(Repository repo) {
    if (!repo.isBare()) {
      throw new IllegalArgumentException(
          "non-bare repository is not supported: " + repo.getIdentifier());
    }

    return new BaseRepositoryBuilder<>().setFS(repo.getFS()).setGitDir(repo.getDirectory());
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
  public FS getFS() {
    return delegate.getFS();
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId, int typeHint)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    return delegate.open(objectId, typeHint);
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
  public String getFullBranch() throws IOException {
    return delegate.getFullBranch();
  }

  @Override
  public String getBranch() throws IOException {
    return delegate.getBranch();
  }

  @Override
  @Deprecated
  public Map<String, Ref> getAllRefs() {
    return delegate.getAllRefs();
  }

  @Override
  @Deprecated
  public Map<String, Ref> getTags() {
    return delegate.getTags();
  }

  @Override
  public DirCache lockDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return delegate.lockDirCache();
  }

  @Override
  public void autoGC(ProgressMonitor monitor) {
    delegate.autoGC(monitor);
  }

  @Override
  public Set<ObjectId> getAdditionalHaves() throws IOException {
    return delegate.getAdditionalHaves();
  }

  @Override
  public Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId() throws IOException {
    return delegate.getAllRefsByPeeledObjectId();
  }

  @Override
  public File getIndexFile() throws NoWorkTreeException {
    return delegate.getIndexFile();
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
  public String getRemoteName(String refName) {
    return delegate.getRemoteName(refName);
  }

  @Override
  public String getGitwebDescription() throws IOException {
    return delegate.getGitwebDescription();
  }

  @Override
  public Set<String> getRemoteNames() {
    return delegate.getRemoteNames();
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId) throws MissingObjectException, IOException {
    return delegate.open(objectId);
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
      throws AmbiguousObjectException,
          IncorrectObjectTypeException,
          RevisionSyntaxException,
          IOException {
    return delegate.resolve(revstr);
  }

  @Override
  public String simplify(String revstr) throws AmbiguousObjectException, IOException {
    return delegate.simplify(revstr);
  }

  @Override
  public RevCommit parseCommit(AnyObjectId id)
      throws IncorrectObjectTypeException, IOException, MissingObjectException {
    return delegate.parseCommit(id);
  }

  @Override
  public DirCache readDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return delegate.readDirCache();
  }

  @Override
  public String shortenRemoteBranchName(String refName) {
    return delegate.shortenRemoteBranchName(refName);
  }

  @Override
  public void setGitwebDescription(String description) throws IOException {
    delegate.setGitwebDescription(description);
  }

  @Override
  public String readMergeCommitMsg() throws IOException, NoWorkTreeException {
    return delegate.readMergeCommitMsg();
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

  /**
   * Converts between ref storage formats.
   *
   * @param format the format to convert to, either "reftable" or "refdir"
   * @param writeLogs whether to write reflogs
   * @param backup whether to make a backup of the old data
   * @throws IOException on I/O problems.
   */
  public void convertRefStorage(String format, boolean writeLogs, boolean backup)
      throws IOException {
    checkState(
        delegate instanceof FileRepository, "Repository is not an instance of FileRepository!");
    ((FileRepository) delegate).convertRefStorage(format, writeLogs, backup);
  }
}
