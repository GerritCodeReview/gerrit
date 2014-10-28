package com.google.gerrit.server.patch;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NoDiffPatchListLoader extends PatchListLoader {
  static final Logger log = LoggerFactory.getLogger(NoDiffPatchListLoader.class);

  @Inject
  NoDiffPatchListLoader(GitRepositoryManager mgr, PatchListCache plc, Config cfg) {
    super(mgr, plc, cfg);
  }

  @Override
  public PatchList load(final PatchListKey key) throws IOException,
      PatchListNotAvailableException {
    final Repository repo = repoManager.openRepository(key.projectKey);
    try {
      return readPatchList(key, repo);
    } finally {
      repo.close();
    }
  }

  private PatchList readPatchList(final PatchListKey key, final Repository repo)
      throws IOException, PatchListNotAvailableException {
    final RawTextComparator cmp = comparatorFor(key.getWhitespace());
    final ObjectReader reader = repo.newObjectReader();
    try {
      final RevWalk rw = new RevWalk(reader);
      final RevCommit b = rw.parseCommit(key.getNewId());
      final RevObject a = aFor(key, repo, rw, b);

      if (a == null) {
        // TODO(sop) Remove this case.
        // This is a merge commit, compared to its ancestor.
        //
        final PatchListEntry[] entries = new PatchListEntry[1];
        entries[0] = newCommitMessage(cmp, reader, null, b);
        return new PatchList(a, b, true, entries);
      }

      final boolean againstParent =
          b.getParentCount() > 0 && b.getParent(0) == a;

      RevCommit aCommit = a instanceof RevCommit ? (RevCommit) a : null;
      RevTree aTree = rw.parseTree(a);
      RevTree bTree = b.getTree();

      DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
      df.setRepository(repo);
      df.setDiffComparator(cmp);
      df.setDetectRenames(true);
      List<DiffEntry> diffEntries = df.scan(aTree, bTree);

      Set<String> paths = key.getOldId() != null
          ? FluentIterable.from(patchListCache.get(
                  new PatchListKey(key.projectKey, null, key.getNewId(),
                  key.getWhitespace())).getPatches())
              .transform(new Function<PatchListEntry, String>() {
                @Override
                public String apply(PatchListEntry entry) {
                  return entry.getNewName();
                }
              })
          .toSet()
          : null;
      int cnt = diffEntries.size();
      List<PatchListEntry> entries = new ArrayList<>();
      entries.add(newCommitMessage(cmp, reader, //
          againstParent ? null : aCommit, b));
      for (int i = 0; i < cnt; i++) {
        DiffEntry diffEntry = diffEntries.get(i);
        if (paths == null || paths.contains(diffEntry.getNewPath())
            || paths.contains(diffEntry.getOldPath())) {
          entries.add(new PatchListEntry(diffEntry.getChangeType(),
              diffEntry.getOldPath(), diffEntry.getNewPath()));
        }
      }
      return new PatchList(a, b, againstParent,
          entries.toArray(new PatchListEntry[entries.size()]));
    } finally {
      reader.release();
    }
  }
}
