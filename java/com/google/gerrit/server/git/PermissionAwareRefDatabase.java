package com.google.gerrit.server.git;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Wrapper around {@link RefDatabase} that filters all refs using {@link
 * com.google.gerrit.server.permissions.PermissionBackend}.
 */
public class PermissionAwareRefDatabase extends RefDatabase {

  private final PermissionBackend.ForProject forProject;
  private final Repository delegateRepository;

  @Inject
  @VisibleForTesting
  public PermissionAwareRefDatabase(
      Repository delegateRepository, PermissionBackend.ForProject forProject) {
    this.forProject = forProject;
    this.delegateRepository = delegateRepository;
  }

  @Override
  public void create() throws IOException {
    delegateRepository.getRefDatabase().create();
  }

  @Override
  public void close() {
    delegateRepository.getRefDatabase().close();
  }

  @Override
  public boolean isNameConflicting(String name) throws IOException {
    // Not applying any filtering here since we might give a wrong answer and we are not
    // leaking any data just declaring the existence
    return delegateRepository.getRefDatabase().isNameConflicting(name);
  }

  @Override
  public RefUpdate newUpdate(String name, boolean detach) throws IOException {
    final Ref toUpdate = delegateRepository.getRefDatabase().getRef(name);
    if (notVisible(toUpdate)) {
      return new AlwaysFailUpdate(delegateRepository, name);
    }

    return delegateRepository.getRefDatabase().newUpdate(name, detach);
  }

  @Override
  public RefRename newRename(String fromName, String toName) throws IOException {
    final Ref renameFrom = delegateRepository.getRefDatabase().getRef(fromName);
    // Does this make sense?
    final Ref renameTo = delegateRepository.getRefDatabase().getRef(toName);
    if (notVisible(renameFrom) || notVisible(renameTo)) {
      return new AlwaysFailRename(delegateRepository, fromName, toName);
    }

    return delegateRepository.getRefDatabase().newRename(fromName, toName);
  }

  @Override
  public Ref getRef(String name) throws IOException {
    final Ref ref = delegateRepository.getRefDatabase().getRef(name);
    if (ref == null) {
      return null;
    }

    return filterRef(ref);
  }

  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    Map<String, Ref> refs = delegateRepository.getRefDatabase().getRefs(prefix);
    if (refs.isEmpty()) {
      return refs;
    }

    return filterRefs(refs);
  }

  @Override
  public List<Ref> getAdditionalRefs() throws IOException {
    return filterRefs(delegateRepository.getRefDatabase().getAdditionalRefs());
  }

  @Override
  public Ref peel(Ref ref) throws IOException {
    return delegateRepository.getRefDatabase().peel(ref);
  }

  @Override
  public boolean performsAtomicTransactions() {
    return delegateRepository.getRefDatabase().performsAtomicTransactions();
  }

  @Override
  public void refresh() {
    delegateRepository.getRefDatabase().refresh();
  }

  private boolean notVisible(final Ref renameFrom) throws IOException {
    return renameFrom != null && filterRef(renameFrom) == null;
  }

  private Ref filterRef(Ref ref) throws IOException {
    Map<String, Ref> result = filterRefs(ImmutableMap.of(ref.getName(), ref));
    if (result.isEmpty()) {
      return null;
    }
    return result.get(ref.getName());
  }

  private List<Ref> filterRefs(List<Ref> refs) throws IOException {
    List<Ref> result = new ArrayList<>();
    result.addAll(
        filterRefs(refs.stream().collect(Collectors.toMap(Ref::getName, r -> r, (a, b) -> b)))
            .values());
    return result;
  }

  private Map<String, Ref> filterRefs(Map<String, Ref> refs) throws IOException {
    Map<String, Ref> result;
    try {
      result = forProject.filter(refs, delegateRepository, RefFilterOptions.defaults());
    } catch (PermissionBackendException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new IOException(e);
    }
    return result;
  }

  static class AlwaysFailUpdate extends RefUpdate {
    private final RefDatabase refdb;
    private final Repository repository;

    AlwaysFailUpdate(Repository repository, String name) {
      super(new ObjectIdRef.Unpeeled(Ref.Storage.NEW, name, null));
      this.refdb = repository.getRefDatabase();
      this.repository = repository;
      setCheckConflicting(false);
    }

    @Override
    public Result forceUpdate() throws IOException {
      return Result.REJECTED_OTHER_REASON;
    }

    @Override
    public Result update() throws IOException {
      return Result.REJECTED_OTHER_REASON;
    }

    @Override
    public Result update(RevWalk walk) throws IOException {
      return Result.REJECTED_OTHER_REASON;
    }

    @Override
    public Result delete() throws IOException {
      return Result.REJECTED_OTHER_REASON;
    }

    @Override
    public Result delete(RevWalk walk) throws IOException {
      return Result.REJECTED_OTHER_REASON;
    }

    /** {@inheritDoc} */
    @Override
    protected RefDatabase getRefDatabase() {
      return refdb;
    }

    /** {@inheritDoc} */
    @Override
    protected Repository getRepository() {
      return repository;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean tryLock(boolean deref) throws IOException {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    protected void unlock() {
      // No locks are held here.
    }

    /** {@inheritDoc} */
    @Override
    protected Result doUpdate(Result desiredResult) {
      return Result.REJECTED_OTHER_REASON;
    }

    /** {@inheritDoc} */
    @Override
    protected Result doDelete(Result desiredResult) {
      return Result.REJECTED_OTHER_REASON;
    }

    /** {@inheritDoc} */
    @Override
    protected Result doLink(String target) {
      return Result.REJECTED_OTHER_REASON;
    }
  }

  static class AlwaysFailRename extends RefRename {
    protected AlwaysFailRename(Repository repository, String from, String to) {
      super(new AlwaysFailUpdate(repository, from), new AlwaysFailUpdate(repository, to));
    }

    @Override
    protected Result doRename() throws IOException {
      return Result.REJECTED_OTHER_REASON;
    }
  }
}
