package com.google.gerrit.extensions.common;

public enum ProjectSubmitType {
  FAST_FORWARD_ONLY,
  MERGE_IF_NECESSARY,
  REBASE_IF_NECESSARY,
  MERGE_ALWAYS,
  CHERRY_PICK
}