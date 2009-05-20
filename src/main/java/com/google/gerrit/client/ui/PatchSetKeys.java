package com.google.gerrit.client.ui;

import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;

import java.util.Arrays;
import java.util.List;

/**
 * This class is used to supply links to the previous and next patch file in a patch set.
 * It contains the list of all the patch keys contained in this patch set and a pointer
 * to the current file.
 */
public class PatchSetKeys {
  private int index;
  private List<Patch.Key> keys;
  private Patch.Key key;
  private Change.Id parentChange;

  /**
   * Create a PatchSetKeys based on a list of patches and the index that identifies that patch
   * in the list.
   */
  public PatchSetKeys(List<Patch.Key> keys, int index, Change.Id parentChange) {
    init(keys, keys.get(index), index, parentChange);
  }

  /**
   * Create a PatchSetKeys with just one Patch
   */
  public PatchSetKeys(Patch.Key key) {
    init(Arrays.asList(new Patch.Key[] { key }), key, 0, null);
  }

  private void init(List<Patch.Key> keys, Patch.Key key, int index, Change.Id parentChange) {
    this.key = key;
    this.keys = keys;
    this.index = index;
    this.parentChange = parentChange;
  }

  public Patch.Key getKey() {
    return key;
  }

  /**
   * @return a link to the previous file in this patch set, or null if we are looking at the first
   * file.
   */
  public DirectScreenLink getPreviousPatchLink(Patch.PatchType patchType) {
    if (index == 0) return createParentChangeLink();
    else return createLink(new PatchSetKeys(keys, index - 1, parentChange), patchType, "<<", "");
  }

  /**
   * @return a link to the next file in this patch set, or null if we are looking at the last
   * file.
   */
  public DirectScreenLink getNextPatchLink(Patch.PatchType patchType) {
    if (index >= keys.size() - 1) return createParentChangeLink();
    else return createLink(new PatchSetKeys(keys, index + 1, parentChange), patchType, "", ">>");
  }

  /**
   * @return a link to the the given patch.
   * @param patch The patch to link to
   * @param patchType The type of patch display
   * @param before A string to display at the beginning of the href text
   * @param after A string to display at the end of the href text
   */
  private PatchLink createLink(PatchSetKeys patch, Patch.PatchType patchType, 
      String before, String after) {
    String thisKey = patch.getKey().get();
    PatchLink result = null;
    switch (patchType) {
      case UNIFIED:
        result = new PatchLink.Unified(before + " " + thisKey + " " + after, patch);
        break;
      case BINARY:
        // TODO
        break;
      case N_WAY:
        result = new PatchLink.SideBySide(before + " " + thisKey + " " + after, patch);
        break;
    }

    return result;
  }
  
  private ChangeLink createParentChangeLink() {
    return parentChange != null ? new ChangeLink("Back to the patch", parentChange) : null;
  }
}
