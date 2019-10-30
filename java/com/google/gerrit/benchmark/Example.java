package com.google.gerrit.benchmark;

import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(
    value = 2,
    jvmArgs = {"-Xms4G", "-Xmx4G"})
@Warmup(iterations = 1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
public class Example {
  private Repository repository;
  // <ChangeId, <PsNumber, ObjectId>>
  private TreeMap<Integer, TreeMap<Integer, ObjectId>> testData = new TreeMap<>();

  /**
   * Set up the benchmark.
   *
   * <p>Prerequisite: {@code git clone --mirror https://chromium.googlesource.com}
   */
  @Setup
  public void setup() throws Exception {
    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
    repositoryBuilder.setMustExist(true);
    repositoryBuilder.setGitDir(new File("/usr/local/google/home/hiesel/tmp/src.git"));
    repository = repositoryBuilder.build();

    Pattern changeIdPattern = Pattern.compile("refs/changes/(\\d+)/(\\d+)/(\\d+)");

    int NUM_CHANGES = 250;

    for (Ref ref : repository.getRefDatabase().getRefsByPrefix("refs/changes/")) {
      Matcher m = changeIdPattern.matcher(ref.getName());
      if (!m.matches()) {
        // meta ref
        continue;
      }
      int cId = Integer.valueOf(m.group(2));
      int psNum = Integer.valueOf(m.group(3));

      testData.putIfAbsent(cId, new TreeMap<>());
      testData.get(cId).put(psNum, repository.parseCommit(ref.getObjectId().copy()));

      if (testData.size() >= NUM_CHANGES) {
        System.out.println();
        System.out.println(
            "==> Created a test set of "
                + testData.size()
                + " changes with an average of "
                + testData.values().stream()
                    .map(e -> e.values().size())
                    .collect(Collectors.summarizingInt(Integer::intValue))
                    .getAverage());
        return;
      }
    }
  }

  @Benchmark
  public void singleRefWalkWithAllPatchSetsAsStart() throws IOException {
    for (Map.Entry<Integer, TreeMap<Integer, ObjectId>> change : testData.entrySet()) {
      ObjectId last = change.getValue().lastEntry().getValue();
      Set<ObjectId> old = new HashSet<>(change.getValue().values());
      old.remove(last);
      boolean isMerged = newPatchSetDependsOnPriorPatchSet(last, old);
      if (isMerged) {
        throw new RuntimeException("unexpected");
      }
    }
  }

  @Benchmark
  public void asingleRefWalkWithCurrentPatchSet() throws IOException {
    for (Map.Entry<Integer, TreeMap<Integer, ObjectId>> change : testData.entrySet()) {
      ObjectId last = change.getValue().lastEntry().getValue();
      Set<ObjectId> old = new HashSet<>(change.getValue().values());
      old.remove(last);
      boolean isMerged = newPatchSetDependsOnPriorPatchSetSingleWalk(last, old);
      if (isMerged) {
        throw new RuntimeException("unexpected");
      }
    }
  }

  @Benchmark
  public void currentCode() throws IOException {
    for (Map.Entry<Integer, TreeMap<Integer, ObjectId>> changes : testData.entrySet()) {
      RevWalk walk = new RevWalk(repository);
      Collection<ObjectId> patchSets = changes.getValue().values();
      RevCommit lastPatchSet = walk.parseCommit(Iterables.getLast(patchSets));
      for (ObjectId c : patchSets) {
        if (c.equals(lastPatchSet)) {
          continue;
        }
        boolean isMerged = walk.isMergedInto(walk.parseCommit(c), lastPatchSet);
        if (isMerged) {
          throw new RuntimeException("unexpected");
        }
      }
    }
  }

  /** Checks a change for self-dependency by performing a rev walk. */
  private boolean newPatchSetDependsOnPriorPatchSet(
      ObjectId newPatchSet, Set<ObjectId> oldPatchSets) throws IOException {
    RevWalk rw = new RevWalk(repository);
    rw.reset();
    rw.setRevFilter(RevFilter.MERGE_BASE);
    rw.setTreeFilter(TreeFilter.ALL);
    rw.markStart(rw.parseCommit(newPatchSet));
    for (ObjectId ps : oldPatchSets) {
      rw.markStart(rw.parseCommit(ps));
    }
    RevCommit mergeBase;
    while ((mergeBase = rw.next()) != null) {
      if (oldPatchSets.contains(mergeBase)) {
        System.out.println(mergeBase + " -----" + oldPatchSets);
        return true;
      }
    }
    return false;
  }

  /** Checks a change for self-dependency by performing a rev walk. */
  private boolean newPatchSetDependsOnPriorPatchSetSingleWalk(
      ObjectId newPatchSet, Set<ObjectId> oldPatchSets) throws IOException {
    RevWalk rw = new RevWalk(repository);
    rw.reset();
    rw.markStart(rw.parseCommit(newPatchSet));

    ObjectIdOwnerMap<RevCommit> old = new ObjectIdOwnerMap<>();
    for (ObjectId e : oldPatchSets) {
      old.add(rw.parseCommit(e));
    }
    for (RevCommit rc : rw) {
      if (rc != null && old.contains(rc)) {
        System.out.println(rc + " -----" + oldPatchSets);
        return true;
      }
    }
    return false;
  }
}
