// Copyright (C) 2016 The Android Open Source Project
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

import com.googlecode.javaewah.EWAHCompressedBitmap;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

/**
 * Decide if two commits are similar or not.
 *
 * <p>This extracts the commit messages and the diff texts and calculates string
 * similarities. Given two string similarities, it puts them into a classifier
 * built with support vector machine and decides if two commits are similar.
 *
 * <p>The string similarity is calculated based on rolling hash. It takes the
 * WINDOW_SIZE substrings by shifting one character and calculate the hashes.
 * This process converts one String into a set of integers (fingerprint). The
 * similarity is the percentage of common hashes in the fingerprints.
 */
public class SimilarityDecisionFunction {
  public static SimilarityDecisionFunction INSTANCE =
      new SimilarityDecisionFunction();

  // These parameters here and in "similarity.svm" are trained by some samples
  // in *.googlesource.com. We extracted the followings as the training data:
  //
  // (1) For each change in Gerrit, match all patch-sets. These commits are
  // supposed to express the same change and should be classified as similar.
  // (2) For each change in Gerrit, pick up the latest patch-set and match them
  // all. These commits are from different changes and should be classified as
  // not-similar.
  //
  // We've got 37687 data points. From this data, we randomly sampled 2500 data
  // points and used them for training. The rest of them are used for checking
  // the accuracy for the classification. This is the statistics of the
  // classifier.
  //
  // Confusion Matrix and Statistics
  //           Reference
  // Prediction match unmatch
  //    match    1604      39
  //    unmatch    10   33501
  //                Accuracy : 0.9986
  //                  95% CI : (0.9982, 0.999)
  //     No Information Rate : 0.9541
  //     P-Value [Acc > NIR] : < 2.2e-16
  //                   Kappa : 0.9842
  //  Mcnemar's Test P-Value : 6.334e-05
  //             Sensitivity : 0.99380
  //             Specificity : 0.99884
  //          Pos Pred Value : 0.97626
  //          Neg Pred Value : 0.99970
  //              Prevalence : 0.04591
  //          Detection Rate : 0.04563
  //    Detection Prevalence : 0.04674
  //       Balanced Accuracy : 0.99632
  //        'Positive' Class : match
  //
  // TODO(masayasuzuki): Sample more data to increase the accuracy (though it's
  // already over 99%...)
  private static final double SIMILAR_LABEL = 1.0;
  private static final int COMMIT_MESSAGE_FEATURE_INDEX = 1;
  private static final double COMMIT_MESSAGE_FEATURE_MEAN = 0.0827783676212095;
  private static final double COMMIT_MESSAGE_FEATURE_SCALE = 0.21108231263655;
  private static final int DIFF_FEATURE_INDEX = 1;
  private static final double DIFF_FEATURE_MEAN = 0.0729221930514771;
  private static final double DIFF_FEATURE_SCALE = 0.176656753169063;

  private final svm_model model;

  SimilarityDecisionFunction() {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new BufferedInputStream(
                    SimilarityDecisionFunction.class.getResourceAsStream(
                        "similarity.svm"))))) {
      model = svm.svm_load_model(reader);
    } catch (IOException e) {
      throw new RuntimeException("cannot read the similarity model", e);
    }
  }

  public boolean isSimilar(Features f1, Features f2) {
    // Shortcut. If the timestamps are the same, consider two commits are
    // similar.
    if (f1.commitTimestamp == f2.commitTimestamp) {
      return true;
    }
    double commitMessageSimilarity =
        similarity(f1.commitMessage, f2.commitMessage);
    double diffSimilarity = similarity(f1.diff, f2.diff);

    return isSimilar(commitMessageSimilarity, diffSimilarity);
  }

  boolean isSimilar(double commitMessageSimilarity, double diffSimilarity) {
    // Normalize features.
    svm_node node1 = new svm_node();
    node1.index = COMMIT_MESSAGE_FEATURE_INDEX;
    node1.value =
        (commitMessageSimilarity - COMMIT_MESSAGE_FEATURE_MEAN)
        / COMMIT_MESSAGE_FEATURE_SCALE;

    svm_node node2 = new svm_node();
    node2.index = DIFF_FEATURE_INDEX;
    node2.value = (diffSimilarity - DIFF_FEATURE_MEAN) / DIFF_FEATURE_SCALE;

    // The result is
    // 1.0) SIMILAR
    // 2.0) NOT_SIMILAR
    double result = svm.svm_predict(model, new svm_node[] {node1, node2});
    return Math.abs(result - SIMILAR_LABEL) < 0.01;
  }

  private static double similarity(
      EWAHCompressedBitmap b1, EWAHCompressedBitmap b2) {
    int matches = b1.andCardinality(b2);
    int nonMatches = b1.andNotCardinality(b2) + b2.andNotCardinality(b1);
    return ((double) matches) / (matches + nonMatches);
  }

  public static final class Features {
    final int commitTimestamp;
    final EWAHCompressedBitmap commitMessage;
    final EWAHCompressedBitmap diff;

    Features(RevWalk walk, RevCommit commit, Repository repo)
        throws IOException {
      this.commitTimestamp = commit.getCommitTime();
      try (StringFingerprinter fp = new StringFingerprinter()) {
        fp.write(commit.getFullMessage().getBytes(StandardCharsets.UTF_8));
        this.commitMessage = fp.getBitmap();
      }
      if (commit.getParentCount() == 1) {
        this.diff = diff(repo, walk.parseCommit(commit.getParent(0)), commit);
      } else {
        this.diff = EWAHCompressedBitmap.bitmapOf();
      }
    }

    private static EWAHCompressedBitmap diff(
        Repository repo, RevCommit c1, RevCommit c2) throws IOException {
      CanonicalTreeParser t1 = new CanonicalTreeParser();
      t1.reset(repo.newObjectReader(), c1.getTree());
      CanonicalTreeParser t2 = new CanonicalTreeParser();
      t2.reset(repo.newObjectReader(), c2.getTree());

      try (StringFingerprinter fp = new StringFingerprinter();
          DiffFormatter diffFormatter = new DiffFormatter(fp)) {
        diffFormatter.setRepository(repo);
        diffFormatter.format(diffFormatter.scan(t1, t2));
        return fp.getBitmap();
      }
    }
  }

  static class StringFingerprinter extends OutputStream {
    // TODO(masayasuzuki): Use EWAHCompressedBitmap.WORD_IN_BITS after updating
    // javaewah.
    private static final int EWAH_MAX_VALUE = Integer.MAX_VALUE - 64;

    private final EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();
    private final RollingHasher rollingHasher = new RollingHasher();

    EWAHCompressedBitmap getBitmap() {
      bitmap.trim();
      return bitmap;
    }

    @Override
    public void write(int b) {
      long h = rollingHasher.add((byte) (b & 0xFF));
      if (h < 0) {
        h = -h;
      }
      bitmap.set((int) (h % EWAH_MAX_VALUE));
    }
  }

  private static class RollingHasher {
    private static final int WINDOW_SIZE = 8; // bytes
    private static final long RABIN_CARP_A = 1996936387L; // Random prime.
    private static final long MAX_A;

    static {
      long maxA = 1;
      for (int i = 0; i < WINDOW_SIZE - 1; i++) {
        maxA *= RABIN_CARP_A;
      }
      MAX_A = maxA;
    }

    private final byte[] history = new byte[WINDOW_SIZE];
    private int historyIndex;
    private long hash;

    RollingHasher() {}

    long add(byte b) {
      hash -= history[historyIndex] * MAX_A;
      hash = (hash * RABIN_CARP_A) + b;
      history[historyIndex] = b;
      historyIndex = (historyIndex + 1) % WINDOW_SIZE;
      return hash;
    }
  }
}
