tree grammar Simplify;

options {
  tokenVocab=Query;
  ASTLabelType=CommonTree;
  output=AST;
  filter=true;
}

@header {
package com.google.gerrit.server.query;
}

// Combine DEFAULT_FIELD sibling nodes when they appear in an AND.
// Some search backends implement DEFAULT_FIELD by expanding it into an OR of
// all combinations of fields that the backend is willing to try by default.
// For certain terms or phrases, this risks a combinatorial explosion that can
// exceed the query size limit of the backend.
// More details: https://bugs.chromium.org/p/gerrit/issues/detail?id=4904
topdown
  : ^(AND (.)+)
    {
      Tree tree = AND1;
      List<Tree> defaultFields = new ArrayList<>();
      int child = 0;
      while (child < tree.getChildCount()) {
        Tree operand = tree.getChild(child);
        if (operand.getType() != DEFAULT_FIELD) {
          child++;
          continue;
        }
        defaultFields.add(operand);
        tree.deleteChild(child);
      }
      if (!defaultFields.isEmpty()) {
        Tree first = defaultFields.get(0);
        for (int i = 1; i < defaultFields.size(); i++) {
          first.addChild(defaultFields.get(i).getChild(0));
        }
        tree.addChild(first);
      }
    }
  ;
