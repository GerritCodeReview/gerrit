// Copyright (C) 2010 The Android Open Source Project
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

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * This class provides functions and operations for dealing with repositories
 * that use sub-trees.
 * <p>
 * This class provides
 * <ul>
 * <li>
 * Utility functions for find sub-trees that have been merged in to a commit or
 * somewhere in that commit's history.
 * <li>
 * Splitting out changes to sub-trees into their own commits that live in the
 * sub-tree's ancestory.
 * <li>
 * After splitting out sub-tree commits, rewriting main-line commits to bring
 * the sub-tree commits.
 * </ul>
 */
public class SubtreeSplitCommand {

  private static final FooterKey SUBTREE_FOOTER_KEY = new FooterKey("Sub-Tree");
  private static final String SUBTREE_CONFIG = ".gitsubtree";

  private static final String SUBTREE_PROP = "subtree";
  private static final String SUBTREE_PATH_PROP = "path";
  private static final String SUBTREE_URL_PROP = "url";

  @SuppressWarnings("serial")
  public static class SubtreeFooterException extends IOException {

    public SubtreeFooterException(String msg) {
      super(msg);
    }
  }

  public static final RevCommit NO_SUBTREE = new RevCommit(ObjectId.zeroId()) {};

  static abstract class SplitContext {

    Map<ObjectId, RevCommit> mNewParents = new HashMap<ObjectId, RevCommit>();

    protected String mId;

    protected SplitContext(String aId) {
      mId = aId;
    }

    protected abstract String getPath(Config conf);

    protected String getId() {
      return mId;
    }

    @Override
    public String toString() {
      return getId();
    }

  }

  /**
   * Assumes the subtree always exists at the same path. This is useful for
   * splitting out a new subtree project from an existing project.
   */
  public static class PathBasedSplitContext extends SplitContext {

    protected String mPath;

    public PathBasedSplitContext(String aId, String aPath) {
      super(aId);
      mPath = aPath;
    }

    @Override
    protected String getPath(Config conf) {
      return mPath;
    }

  }

  /**
   * Reads .gitsubtree config file on every commit to get the configuration of a
   * named subtree.
   */
  private static class NameBasedSplitContext extends SplitContext {

    private NameBasedSplitContext(String aName) {
      super(aName);
    }

    @Override
    protected String getPath(Config conf) {
      return conf != null ? conf
          .getString(SUBTREE_PROP, mId, SUBTREE_PATH_PROP) : null;
    }

  }

  private Set<SplitContext> mPathBasedSplitters = new HashSet<SplitContext>();
  private Repository mRepo;

  protected SubtreeSplitCommand(Repository aRepo) {
    mRepo = aRepo;
  }

  /**
   * Split out sub-trees and rewrite the commit to use the new splits.
   */
  public Map<ObjectId, RevCommit> call(RevWalk walk, RevCommit aStart, Set<RevCommit> toRewrite) throws IOException {

    Set<SplitContext> splitters = getSplitContexts(aStart);
    RevFilter oldFilter = walk.getRevFilter();
    TreeWalk treeWalk = null;
    ObjectInserter inserter = null;
    try {
      treeWalk = new TreeWalk(mRepo);
      inserter = mRepo.newObjectInserter();
      ArrayList<RevCommit> mainlineList = splitSubtrees(walk, splitters, inserter, treeWalk, aStart);
      return rewriteMainlineCommits(walk, splitters, inserter, mainlineList, toRewrite);
    } finally {
      walk.reset();
      walk.setRevFilter(oldFilter);
      if (treeWalk != null) {
        treeWalk.release();
      }
      if (inserter != null) {
        inserter.release();
      }
    }
  }

  protected ArrayList<RevCommit> splitSubtrees(
      RevWalk walk,
      Set<SplitContext> splitters,
      ObjectInserter inserter,
      TreeWalk treeWalk,
      RevCommit startCommit) throws MissingObjectException, IncorrectObjectTypeException, IOException {

    // Set up the walker
    walk.reset();
    walk.markStart(startCommit);
    SubtreeFilter subtreeFilter = new SubtreeFilter(mRepo);
    subtreeFilter.setSplitters(splitters);
    walk.setRevFilter(subtreeFilter);
    walk.sort(RevSort.TOPO);
    walk.sort(RevSort.REVERSE, true);

    ArrayList<RevCommit> mainlineList = new ArrayList<RevCommit>();

    RevCommit curCommit = null;
    while ((curCommit = walk.next()) != null) {

      mainlineList.add(curCommit);
      RevCommit[] parents = curCommit.getParents();
      Config conf = loadSubtreeConfig(mRepo, curCommit);
      RevTree curTree = curCommit.getTree();

      for (SplitContext context : splitters) {

        if (context.mNewParents.get(curCommit) != null) {
          // Technically this may be possible if someone has merged in a
          // commit as both a main line commit and a subtree.  For now this
          // is not allowed.
          throw new IOException("Tree walked out of order");
        }

        // Find the path that the subtree should be at.
        String path = context.getPath(conf);
        if (path == null) {
          // There is no subtree spec for this splitter on this commit.
          // This can happen when there is no entry for the named subtree
          // in the config file.
          context.mNewParents.put(curCommit, NO_SUBTREE);
          continue;
        }

        // Find the tree object at the path
        ObjectId tree = findTree(treeWalk, curTree, path);
        if (tree == null) {
          // This commit doesn't have the subtree
          // TODO: should this be an error case?  The subtree is specified in
          // the config file and there is nothing at the specified path.
          context.mNewParents.put(curCommit, NO_SUBTREE);
          continue;
        }

        CommitBuilder cb = new CommitBuilder();

        HashSet<RevCommit> commitParents = new HashSet<RevCommit>();
        RevCommit identicalParent = null;
        for (ObjectId parent : parents) {

          // Get the mapped subtree commit for the parent.
          RevCommit newParent = context.mNewParents.get(parent);
          if (newParent == NO_SUBTREE) {
            continue;
          }

          // If this tree object matches a parent, then just use the parent.
          // TODO: technically, we should only use the parent commit if the
          // tree matches *AND* there are no other commit objects. However,
          // this can create a bunch of commits with no changes and shouldn't
          // really happen too often.
          if (newParent.getTree().equals(tree)) {
            identicalParent = newParent;
          }

          if (!commitParents.contains(newParent)) {
            commitParents.add(newParent);
            cb.addParentId(newParent);
          }

        }

        RevCommit newRev = null;
        if (identicalParent != null) {
          // There was an identical parent, so just use it.
          newRev = identicalParent;
        } else {
          // Create a new commit for the split subtree.
          cb.setAuthor(curCommit.getAuthorIdent());
          cb.setCommitter(curCommit.getCommitterIdent());
          cb.setEncoding(curCommit.getEncoding());
          cb.setTreeId(tree);
          String msg = compileMsgLines(stripSubtreeFooters(curCommit.getFullMessage()));
          cb.setMessage(msg);
          newRev = walk.parseCommit(inserter.insert(cb));
          // Store off the tree object of the new split
        }
        context.mNewParents.put(curCommit, newRev);

      }

    }

    return mainlineList;
  }

  protected Map<ObjectId, RevCommit> rewriteMainlineCommits(
      RevWalk walk,
      Set<SplitContext> splitters,
      ObjectInserter inserter,
      List<RevCommit> mainlineList,
      Set<RevCommit> toRewrite) throws MissingObjectException, IncorrectObjectTypeException, IOException {

    // Keep track of the mappings between mainline commits and their rewritten
    // commits.
    Map<ObjectId, RevCommit> mainlineMap = new HashMap<ObjectId, RevCommit>();

    for (RevCommit curCommit : mainlineList) {
      // Figure out if this parent should be "rewritten"
      boolean rewriteCommit = toRewrite.contains(curCommit);
      if (toRewrite.contains(curCommit)) {
        rewriteCommit = true;
      } else {
        for (RevCommit parent : curCommit.getParents()) {
          if (toRewrite.contains(parent)) {
            rewriteCommit = true;
          }
        }
      }

      if (rewriteCommit) {
        ObjectId newCommitId = rewriteMainlineCommit(walk, splitters, inserter, mainlineMap, curCommit);
        RevCommit newCommit = walk.parseCommit(newCommitId);
        mainlineMap.put(curCommit, newCommit);
      }
    }

    return mainlineMap;

  }

  protected ObjectId findTree(TreeWalk tw, ObjectId tree, String path) throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {

    // Previous implementation was this:
    //
    // TreeWalk tw = TreeWalk.forPath(walk.getObjectReader(), path, curTree);
    // tw.setRecursive(false);
    // ObjectId tree = (tw != null ? tw.getObjectId(0) : null);
    //
    // However, this didn't seem to consistently return the correct
    // tree object. So, we now manually walk across the tree looking for
    // the correct path.

    StringTokenizer st = new StringTokenizer(path, "/\\");
    tw.reset();
    tw.addTree(tree);

    String element = st.nextToken();
    while (true) {

      if (!tw.next()) {
        return null;
      }

      if (tw.getNameString().equals(element)) {
        if (!st.hasMoreElements()) {
          return tw.getObjectId(0);
        } else {
          try {
            tw.enterSubtree();
            element = st.nextToken();
          } catch (IncorrectObjectTypeException e) {
            // Wasn't a dir
            return null;
          }
        }
      }
    }
  }

  /**
   * Rewrite a commit to use new sub-tree splits.
   */
  protected ObjectId rewriteMainlineCommit(
      RevWalk walk,
      Set<SplitContext> splitters,
      ObjectInserter inserter,
      Map<ObjectId, RevCommit> mainlineMap,
      ObjectId commitId) throws MissingObjectException, IncorrectObjectTypeException, IOException {

    RevCommit commit = walk.parseCommit(commitId);

    CommitBuilder cb = new CommitBuilder();

    // Use author, commiter, and encoding as is from existing commit.
    cb.setAuthor(commit.getAuthorIdent());
    cb.setCommitter(commit.getCommitterIdent());
    cb.setEncoding(commit.getEncoding());

    List<RevCommit> subtreeParents = new ArrayList<RevCommit>();

    // Look for valid subtree parent commits.
    for (SplitContext context : splitters) {

      ObjectId subtreeParentCandidate = context.mNewParents.get(commit);

      if (subtreeParentCandidate == null) {
        continue;
      }

      // Already listed as a parent
      if (subtreeParents.contains(subtreeParentCandidate)) {
        continue;
      }

      RevCommit subtreeParentCandidateRc =
          walk.parseCommit(subtreeParentCandidate);

      // See if this subtree parent is already reachable.
      // NOTE: we need to use the rewritten mainline commits here, so iterate
      // through each parent and use a rewritten version if available.
      boolean reachable = false;
      for (RevCommit parent : commit.getParents()) {
        RevCommit mappedParent = mainlineMap.get(parent);
        RevCommit commitToTest = mappedParent != null ? mappedParent : parent;
        if (isSubtreeMergedInto(walk, subtreeParentCandidateRc, commitToTest)) {
          reachable = true;
          break;
        }
      }

      if (reachable) {
        continue;
      }

      // This is a valid new parent
      subtreeParents.add(subtreeParentCandidateRc);
    }

    List<RevCommit> newParents = new ArrayList<RevCommit>();

    // Add main-line parents back in.
    for (RevCommit parentCandidate : commit.getParents()) {

      if (newParents.contains(parentCandidate)) {
        continue;
      }

      boolean parentAlreadyReachable = false;
      for (RevCommit newParent : subtreeParents) {
        for (RevCommit newParentParent : newParent.getParents()) {
          if (newParentParent.equals(parentCandidate)) {
            parentAlreadyReachable = true;
            break;
          }
        }
        if (parentAlreadyReachable) {
          break;
        }
      }
      if (parentAlreadyReachable) {
        continue;
      }

      RevCommit mappedParent = mainlineMap.get(parentCandidate);
      if (mappedParent != null) {
        cb.addParentId(mappedParent);
      } else {
        cb.addParentId(parentCandidate);
      }
    }

    // Added subtree parents after mainline parents to try and preserve parent
    // ordering. The initial parent is really the important one.
    for (RevCommit subtreeParent : subtreeParents) {
      // TODO: filter to make sure it's not already added?
      cb.addParentId(subtreeParent);
    }

    // Update the subtree config
    cb.setTreeId(updateSubtreeConfig(walk.getObjectReader(), inserter, splitters, commit));

    // Add subtree footers to the message
    cb.setMessage(updateSubtreeFooters(splitters, cb, commit));

    return inserter.insert(cb);

  }

  /**
   * This is similar to RevWalk.isMergedInto, but the walker doesn't go past
   * sub tree parents.
   */
  protected boolean isSubtreeMergedInto(RevWalk walk, RevCommit base, RevCommit tip)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {

    walk.reset();
    RevFilter oldFilter = walk.getRevFilter();
    walk.setRevFilter(new SubtreeFilter(mRepo, true));
    walk.sort(RevSort.TOPO);
    walk.markStart(tip);

    try {
      for (RevCommit c = walk.next(); c != null; c = walk.next()) {
        if (base.equals(c)) {
          return true;
        }
      }
      return false;
    } finally {
      walk.reset();
      walk.setRevFilter(oldFilter);
    }
  }

  /**
   * Update a .gitsubtree config file to match the current state of the split
   * contexts.
   */
  protected ObjectId updateSubtreeConfig(ObjectReader objectReader, ObjectInserter inserter, Set<SplitContext> splitters, RevCommit commit) throws UnmergedPathException, IOException {

    // load existing config
    Config config = loadSubtreeConfig(mRepo, commit);

    // update config for each split context
    for (SplitContext context : splitters) {
      config.setString(SUBTREE_PROP, context.getId(), SUBTREE_PATH_PROP,
          context.getPath(config));
      String url =
          config.getString(SUBTREE_PROP, context.getId(), SUBTREE_URL_PROP);
      if (url != null) {
        config.setString(SUBTREE_PROP, context.getId(), SUBTREE_URL_PROP, url);
      }
    }

    // create the config blob
    final ObjectId configId =
        inserter.insert(Constants.OBJ_BLOB,
            Constants.encode(config.toText()));

    // Load in the existing tree
    DirCache dirCache = DirCache.newInCore();
    DirCacheBuilder builder = dirCache.builder();
    builder.addTree(new byte[0], 0, objectReader, commit.getTree());
    builder.finish();

    // Add the updated .gitsubtree file
    DirCacheEditor editor = dirCache.editor();
    editor.add(new PathEdit(SUBTREE_CONFIG) {
      @Override
      public void apply(DirCacheEntry ent) {
        ent.setObjectId(configId);
        ent.setFileMode(FileMode.REGULAR_FILE);
      }
    });
    editor.finish();

    // Done modifying the tree
    return editor.getDirCache().writeTree(inserter);
  }

  private ArrayList<String> getCommitMsgLines(String msg) throws IOException {
    ArrayList<String> lines = new ArrayList<String>();

    StringReader sr = new StringReader(msg);
    BufferedReader in = new BufferedReader(sr);
    for (String line = in.readLine(); line != null; line = in.readLine()) {
      if (lines.isEmpty() && line.length() == 0) {
        // skip blank lines at the beginning of msg
        continue;
      }
      lines.add(line);
    }

    // Remove blank lines at the end of the message
    int linesLen = lines.size();
    while (linesLen > 0 && lines.get(linesLen - 1).length() == 0) {
      lines.remove(linesLen - 1);
      linesLen--;
    }

    return lines;
  }

  private int findLastParagraph(ArrayList<String> lines) {
    int linesLen = lines.size();
    for (int i = linesLen - 1; i >= 0; i--) {
      if (lines.get(i).length() == 0) {
        return i;
      }
    }
    return -1;
  }

  private String compileMsgLines(ArrayList<String> lines) {
    StringBuilder messageBuilder = new StringBuilder();
    for (String line : lines) {
      messageBuilder.append(line).append('\n');
    }
    return messageBuilder.toString();
  }

  private boolean hasFooters(ArrayList<String> lines) {
    int lastParagraph = findLastParagraph(lines);
    if (lastParagraph <= 0) {
      return false;
    }

    int len = lines.size();
    for (int i = lastParagraph + 1; i < len; i++) {
      String line = lines.get(i);
      int colonIdx = line.trim().indexOf(':');
      if (colonIdx > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Parses a commit message and removes any "Sub-Tree:" footer lines.
   * @throws IOException
   */
  protected ArrayList<String> stripSubtreeFooters(String msg) throws IOException {

    ArrayList<String> lines = getCommitMsgLines(msg);
    int lastParagraph = findLastParagraph(lines);
    int linesLen = lines.size();

    // Remove subtree lines
    String prefix = SUBTREE_FOOTER_KEY.getName() + ":";
    if (lastParagraph > 0) {
      for (int i = lastParagraph; i < linesLen; i++) {
        String line = lines.get(i);
        if (line.startsWith(prefix)) {
          lines.remove(i);
          i--;
          linesLen--;
        }
      }

      // Removing trailing space if necessary.
      if (linesLen - 1 == lastParagraph) {
        lines.remove(linesLen - 1);
        linesLen--;
      }
    }

    return lines;

  }

  /**
   * Update "Sub-Tree:" footer lines.
   *
   * Remove existing footers and add new ones as appropriate for the specified
   * split contexts.
   * @throws IOException
   *
   */
  protected String updateSubtreeFooters(Set<SplitContext> splitters, CommitBuilder cb, RevCommit commit) throws IOException {

    String rawMsg = commit.getFullMessage();
    ArrayList<String> msgLines = stripSubtreeFooters(rawMsg);

    for (SplitContext splitter : splitters) {

      boolean addFooter = false;

      // See if the subtree is listed as a parent.
      ObjectId splitRev = splitter.mNewParents.get(commit);
      for (ObjectId parent : cb.getParentIds()) {
        if (parent.equals(splitRev)) {
          addFooter = true;
          break;
        }
      }

      if (addFooter) {
        if (!hasFooters(msgLines)) {
          msgLines.add("");
        }
        msgLines.add(SUBTREE_FOOTER_KEY.getName() + ": " + splitRev.name() + " " + splitter.getId());
      }

    }

    String msg = compileMsgLines(msgLines);
    if (msg.trim().equals(rawMsg.trim())) {
      return rawMsg;
    } else {
      return compileMsgLines(msgLines);
    }

  }

  /**
   * Parse the .gitsubtree config file for a commit.
   * ".gitsubtree files are in this format":
   *
   * <pre>
   * {@code
   * [subtree "<subtree-id>"]
   *     path = <path-in-tree>
   *     url = <upstream-url>
   * }
   * </pre>
   *
   * For example:
   *
   * <pre>
   * {@code
   * [subtree "gerrit"]
   *     path = gerrit
   * }
   * </pre>
   *
   * @param curCommit
   * @return
   */
  public static Config loadSubtreeConfig(Repository aRepo, RevCommit curCommit)
      throws IOException {
    try {
      return new BlobBasedConfig(null, aRepo, curCommit, SUBTREE_CONFIG);
    } catch (FileNotFoundException e) {
      // Couldn't find the file, so no config
    } catch (IOException e) {
      throw new IOException("Unable to load " + SUBTREE_CONFIG
          + " config file for commit " + curCommit.name());
    } catch (ConfigInvalidException e) {
      // TODO: throw stronger typed message?
      throw new IOException("Invalid " + SUBTREE_CONFIG
          + " config file for commit " + curCommit.name());
    }
    return new Config();
  }

  protected Set<SplitContext> getSplitContexts(RevCommit aCommit) throws IOException {

    Config conf = loadSubtreeConfig(mRepo, aCommit);
    Set<SplitContext> contexts = new HashSet<SplitContext>();
    for (String name : conf.getSubsections(SUBTREE_PROP)) {
      contexts.add(new NameBasedSplitContext(name));
    }
    if (mPathBasedSplitters != null) {
      contexts.addAll(mPathBasedSplitters);
    }
    return contexts;
  }

  /**
   * Parse the Sub-Tree footers.
   *
   * The Sub-Tree footer lines should be in this format:
   * <p>
   * <code><pre>{@code
   * Sub-Tree: <sha1> <subtree-id>
   * }</pre></code>
   * <p/>
   * <p>
   * The sha1 is the sha1 of the commit that owns the subtree. The subtree-id
   * refers to an entry in the .gitsubtree file. For example:
   * </p>
   * <p>
   * <code><pre>{@code
   * Sub-Tree: 8dc6ca89e881048fc72d80ee214beab46a123675 gerrit
   * }</pre></code>
   * </p>
   *
   */
  public static HashMap<String, ObjectId> parseSubtreeFooters(Repository repo, RevCommit curCommit, HashMap<String, ObjectId> subTrees) throws IOException {
    if (subTrees == null) {
      subTrees = new HashMap<String, ObjectId>();
    }

    Config config = loadSubtreeConfig(repo, curCommit);
    RevCommit[] parents = curCommit.getParents();

    for (String line : curCommit.getFooterLines(SUBTREE_FOOTER_KEY)) {
      int len = line.length();
      int idx = 0;

      // skip whitespace
      while (idx < len && Character.isWhitespace(line.charAt(idx))) {
        idx++;
      }
      int shaStart = idx;
      while (idx < len && !Character.isWhitespace(line.charAt(idx))) {
        idx++;
      }

      // parse the SHA1
      ObjectId commitId = null;
      String sha1 = line.substring(shaStart, idx);
      try {
        commitId = ObjectId.fromString(sha1);
      } catch (IllegalArgumentException iae) {
        throw new SubtreeFooterException("Can't parse ObjectId: " + sha1 + " in footer of " + curCommit.name());
      }

      // Make sure the sub tree is actually a parent of this commit
      boolean found = false;
      for (RevCommit parent : parents) {
        if (parent.equals(commitId)) {
          found = true;
          break;
        }
      }
      if (!found) {
        throw new SubtreeFooterException("Sub-Tree \"" + commitId.name() + "\" is not a parent of \"" + curCommit.name() + "\"");
      }

      // skip whitespace
      while (idx < len && Character.isWhitespace(line.charAt(idx))) {
        idx++;
      }

      String subtreeId = line.substring(idx).trim();
      if (subtreeId.isEmpty()) {
        throw new SubtreeFooterException("Sub-Tree id not specified \"" + line + "\" in footer of " + curCommit.name());
      }

      if (config == null || !config.getSubsections("subtree").contains(subtreeId)) {
        throw new SubtreeFooterException("Sub-Tree \"" + subtreeId + "\" does not exist in .gitsubtree file for commit " + curCommit.name());
      }

      subTrees.put(subtreeId, commitId);
    }

    return subTrees;
  }

  public void setSplitPaths(PathBasedSplitContext... pathContexts) {
    mPathBasedSplitters = new HashSet<SplitContext>();
    for (PathBasedSplitContext pathContext : pathContexts) {
      mPathBasedSplitters.add(pathContext);
    }
  }

}
