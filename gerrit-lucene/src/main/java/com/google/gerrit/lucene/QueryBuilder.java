package com.google.gerrit.lucene;

import static org.apache.lucene.search.BooleanClause.Occur.MUST;
import static org.apache.lucene.search.BooleanClause.Occur.MUST_NOT;
import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;

import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.NotPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

public class QueryBuilder {
  public static Query toQuery(Predicate<ChangeData> p) throws QueryParseException {
    if (p.getClass() == AndPredicate.class) {
      return booleanQuery(p, MUST);
    } else if (p.getClass() == OrPredicate.class) {
      return booleanQuery(p, SHOULD);
    } else if (p.getClass() == NotPredicate.class) {
      return booleanQuery(p, MUST_NOT);
    } else if (p instanceof IndexPredicate) {
      return fieldQuery((IndexPredicate<ChangeData>) p);
    } else {
      throw new QueryParseException("Cannot convert to index predicate: " + p);
    }
  }

  private static Query booleanQuery(Predicate<ChangeData> p, BooleanClause.Occur o)
      throws QueryParseException {
    BooleanQuery q = new BooleanQuery();
    for (int i = 0; i < p.getChildCount(); i++) {
      q.add(toQuery(p.getChild(i)), o);
    }
    return q;
  }

  private static Query fieldQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    if (p.getType() == FieldType.INTEGER) {
      return intQuery(p);
    } else if (p.getType() == FieldType.EXACT) {
      return exactQuery(p);
    } else {
      throw badFieldType(p.getType());
    }
  }

  public static Term intTerm(String name, int value) {
    BytesRef bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
    NumericUtils.intToPrefixCodedBytes(value, 0, bytes);
    return new Term(name, bytes);
  }

  private static Query intQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    int value;
    try {
      // Can't use IntPredicate because it and IndexPredicate are different
      // subclasses of OperatorPredicate.
      value = Integer.valueOf(p.getValue());
    } catch (IllegalArgumentException e) {
      throw new QueryParseException("not an integer: " + p.getValue());
    }
    return new TermQuery(intTerm(p.getOperator(), value));
  }

  private static Query exactQuery(IndexPredicate<ChangeData> p) {
    return new TermQuery(new Term(p.getOperator(), p.getValue()));
  }

  public static IllegalArgumentException badFieldType(FieldType<?> t) {
    return new IllegalArgumentException("unknown index field type " + t);
  }
}
