// Copyright (C) 2009 The Android Open Source Project
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

grammar Query;
options {
  language = Java;
  output = AST;
}

tokens {
  AND;
  OR;
  NOT;
  DEFAULT_FIELD;
}

@header {
package com.google.gerrit.server.query;
}
@members {
  static class QueryParseInternalException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    QueryParseInternalException(final String msg) {
      super(msg);
    }
  }

  public static Tree parse(final String str)
    throws QueryParseException {
    try {
      final QueryParser p = new QueryParser(
        new TokenRewriteStream(
          new QueryLexer(
            new ANTLRStringStream(str)
          )
        )
      );
      return (Tree)p.query().getTree();
    } catch (QueryParseInternalException e) {
      throw new QueryParseException(e.getMessage());
    } catch (RecognitionException e) {
      throw new QueryParseException(e.getMessage());
    }
  }

  static boolean isSingleWord(final String value) {
    try {
      final QueryLexer lexer = new QueryLexer(new ANTLRStringStream(value));
      lexer.mSINGLE_WORD();
      return lexer.nextToken().getType() == QueryParser.EOF;
    } catch (QueryParseInternalException e) {
      return false;
    } catch (RecognitionException e) {
      return false;
    }
  }

  @Override
  public void displayRecognitionError(String[] tokenNames,
                                      RecognitionException e) {
      String hdr = getErrorHeader(e);
      String msg = getErrorMessage(e, tokenNames);
      throw new QueryParseInternalException(hdr + " " + msg);
  }
}

@lexer::header {
package com.google.gerrit.server.query;
}
@lexer::members {
  @Override
  public void displayRecognitionError(String[] tokenNames,
                                      RecognitionException e) {
      String hdr = getErrorHeader(e);
      String msg = getErrorMessage(e, tokenNames);
      throw new QueryParser.QueryParseInternalException(hdr + " " + msg);
  }
}

query
  : conditionOr
  ;

conditionOr
  : (conditionAnd OR)
    => conditionAnd OR^ conditionAnd (OR! conditionAnd)*
  | conditionAnd
  ;

conditionAnd
  : (conditionNot AND)
    => i+=conditionNot (i+=conditionAnd2)*
    -> ^(AND $i+)
  | (conditionNot conditionNot)
    => i+=conditionNot (i+=conditionAnd2)*
    -> ^(AND $i+)
  | conditionNot
  ;
conditionAnd2
  : AND! conditionNot
  | conditionNot
  ;

conditionNot
  : '-' conditionBase -> ^(NOT conditionBase)
  | NOT^ conditionBase
  | VARIABLE_ASSIGN^ conditionOr ')'!
  | conditionBase
  ;
conditionBase
  : '('! conditionOr ')'!
  | (FIELD_NAME ':') => FIELD_NAME^ ':'! fieldValue
  | fieldValue -> ^(DEFAULT_FIELD fieldValue)
  ;

fieldValue
  : n=FIELD_NAME   -> SINGLE_WORD[n]
  | SINGLE_WORD
  | EXACT_PHRASE
  ;

AND: 'AND' ;
OR:  'OR'  ;
NOT: 'NOT' ;

WS
  :  ( ' ' | '\r' | '\t' | '\n' ) { $channel=HIDDEN; }
  ;

FIELD_NAME
  : ('a'..'z' | '_')+
  ;

VARIABLE_ASSIGN
  : ('A'..'Z') ('A'..'Z' | 'a'..'Z')* '=' '(' {
      String s = $text;
      setText(s.substring(0, s.length() - 2));
    }
  ;

EXACT_PHRASE
  : '"' ( ~('"') )* '"' {
      String s = $text;
      setText(s.substring(1, s.length() - 1));
    }
  ;

SINGLE_WORD
  : ~( '-' | NON_WORD ) ( ~( NON_WORD ) )*
  ;
fragment NON_WORD
  :  ( '\u0000'..' '
     | '!'
     | '"'
     | '#'
     | '$'
     | '%'
     | '&'
     | '\''
     | '(' | ')'
     // '*'  permit
     // '+'  permit
     // ','  permit
     // '-'  permit
     // '.'  permit
     // '/'  permit
     | ':'
     | ';'
     // '<' permit
     // '=' permit
     // '>' permit
     | '?'
     | '[' | ']'
     | '{' | '}'
     | '~'
     )
  ;
