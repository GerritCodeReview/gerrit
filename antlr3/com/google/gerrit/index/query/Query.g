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
package com.google.gerrit.index.query;
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

  public static boolean isSingleWord(final String value) {
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
package com.google.gerrit.index.query;
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
  | conditionBase
  ;
conditionBase
  : '('! conditionOr ')'!
  | (FIELD_NAME COLON) => FIELD_NAME^ COLON! fieldValue
  | fieldValue -> ^(DEFAULT_FIELD fieldValue)
  ;

fieldValue
  // Rewrite by invoking SINGLE_WORD fragment lexer rule, passing the field name as an argument.
  : n=FIELD_NAME -> SINGLE_WORD[n]

  // Allow field values to contain a colon. We can't do this at the lexer level, because we need to
  // emit a separate token for the field name. If we were to allow ':' in SINGLE_WORD, then
  // everything would just lex as DEFAULT_FIELD.
  //
  // Field values with a colon may be lexed either as <field>:<rest> or <word>:<rest>, depending on
  // whether the part before the colon looks like a field name.
  // TODO(dborowitz): Field values ending in colon still don't work.
  | (FIELD_NAME COLON) => n=FIELD_NAME COLON fieldValue -> SINGLE_WORD[n] COLON fieldValue
  | (SINGLE_WORD COLON) => SINGLE_WORD COLON fieldValue

  | SINGLE_WORD
  | EXACT_PHRASE
  ;

AND: 'AND' ;
OR:  'OR'  ;
NOT: 'NOT' ;

COLON: ':' ;

WS
  :  ( ' ' | '\r' | '\t' | '\n' ) { $channel=HIDDEN; }
  ;

fragment ALPHA_UNDERSCORE: ('a'..'z' | '_')+ ;

FIELD_NAME
  : ALPHA_UNDERSCORE ( '-' ALPHA_UNDERSCORE )*
  ;

EXACT_PHRASE
@init { final StringBuilder buf = new StringBuilder(); }
  : '"' ( ESCAPE[buf] | i = ~('\\'|'"') { buf.appendCodePoint(i); } )* '"' {
      setText(buf.toString());
    }
  | '{' ( ESCAPE[buf] | i = ~('\\'|'{'|'}') { buf.appendCodePoint(i); } )* '}' {
      setText(buf.toString());
    }
  ;

SINGLE_WORD
  : ~( '-' | NON_WORD ) ( ~( NON_WORD ) )*
  ;
fragment NON_WORD
  :  ( '\u0000'..' '
     | '!'
     | '"'
     // '#' permit
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
     | COLON
     | ';'
     // '<' permit
     // '=' permit
     // '>' permit
     | '?'
     | '[' | ']'
     | '{' | '}'
     // | '~' permit
     )
  ;

fragment ESCAPE[StringBuilder buf] :
    '\\'
    ( 't' { buf.append('\t'); }
    | 'n' { buf.append('\n'); }
    | 'r' { buf.append('\r'); }
    | i = ~('t'|'n'|'r') { buf.appendCodePoint(i); }
    );
