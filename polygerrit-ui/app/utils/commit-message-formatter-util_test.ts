/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
import {
  CommitMessage,
  detectFormattingErrorsInString,
  ErrorType,
  formatCommitMessageString,
  FormattingError,
  TEST_ONLY,
} from './commit-message-formatter-util';

const parseCommitMessageString = TEST_ONLY.parseCommitMessageString;

suite('commit-message-formatter-util tests', () => {
  suite('formatCommitMessageString', () => {
    test('subject exceeding 72 characters is not split', () => {
      const longSubject =
        'Fix(some-component): This is a very long subject that exceeds 72 characters';
      const message = longSubject + '\n\nThis is body.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        longSubject + '\n\nThis is body.\n\nChange-Id: abcdefg\n'
      );
    });

    test('words are always kept whole', () => {
      const message =
        'Fix the thing\n\nThis is a very long long long long line with a very-long-word-that-should-not-be-split.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is a very long long long long line with a\nvery-long-word-that-should-not-be-split.\n\nChange-Id: abcdefg\n'
      );
    });

    test('long strings without spaces are not split', () => {
      const message =
        'Fix the thing\n\nhttps://very-long-url-without-spaces-that-exceeds-72-characters.com/with/some/path/and/query?params=and&more=params\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nhttps://very-long-url-without-spaces-that-exceeds-72-characters.com/with/some/path/and/query?params=and&more=params\n\nChange-Id: abcdefg\n'
      );
    });

    test('trailing spaces are removed', () => {
      const message =
        'Fix the thing   \n\nThis is a line with trailing spaces.   \nAnd another one.  \n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is a line with trailing spaces. And another one.\n\nChange-Id: abcdefg\n'
      );
    });

    test('leading spaces are removed from subject and first body line', () => {
      const message =
        '   Fix the thing\n\n   This is the body.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is the body.\n\nChange-Id: abcdefg\n'
      );
    });

    test('empty lines at the beginning and end are removed', () => {
      const message =
        '\n\nFix the thing\n\nThis is the body.\n\n\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is the body.\n\nChange-Id: abcdefg\n'
      );
    });

    test('consecutive blank lines are combined', () => {
      const message =
        'Fix the thing\n\nThis is the body.\n\n\nAnd another paragraph.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is the body.\n\nAnd another paragraph.\n\nChange-Id: abcdefg\n'
      );
    });

    test('lines in the same paragraph are merged and split', () => {
      const message =
        'Fix the thing\n\nThis is a paragraph\nwith lines that should be\nmerged and then split\naccording to the line\nlength limit.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is a paragraph with lines that should be merged and then split\naccording to the line length limit.\n\nChange-Id: abcdefg\n'
      );
    });

    test('lines within footers are not split', () => {
      const message =
        'Fix the thing\n\nThis is the body.\n\nFixes: #123\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is the body.\n\nFixes: #123\nChange-Id: abcdefg\n'
      );
    });

    test('footers with equals sign (TAG=agy, CONV=...) are not split or merged', () => {
      const message =
        'Fix the thing\n\nThis is the body.\n\nTAG=agy\nCONV=fe186e29-8ffb-4bb8-a778-d48e3c804048\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is the body.\n\nTAG=agy\nCONV=fe186e29-8ffb-4bb8-a778-d48e3c804048\nChange-Id: abcdefg\n'
      );
    });

    test('footers with equals sign separated by a blank line', () => {
      const message =
        'Fix the thing\n\nThis is the body.\n\nTAG=agy\nCONV=fe186e29-8ffb-4bb8-a778-d48e3c804048\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is the body.\n\nTAG=agy\nCONV=fe186e29-8ffb-4bb8-a778-d48e3c804048\nChange-Id: abcdefg\n'
      );
    });

    test('footers with equals sign only are recognized as footers', () => {
      const message =
        'Fix the thing\n\nThis is the body.\n\nTAG=agy\nCONV=fe186e29-8ffb-4bb8-a778-d48e3c804048\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\nThis is the body.\n\nTAG=agy\nCONV=fe186e29-8ffb-4bb8-a778-d48e3c804048\n'
      );
    });

    test('chromium-style footers with equals sign are preserved', () => {
      const message =
        'Add new feature\n\nThis is a long description of the feature that should be wrapped across multiple lines if needed.\n\nBUG=chromium:12345\nTEST=browser_tests\nR=reviewer@chromium.org\n';
      assert.equal(
        formatCommitMessageString(message),
        'Add new feature\n\nThis is a long description of the feature that should be wrapped across\nmultiple lines if needed.\n\nBUG=chromium:12345\nTEST=browser_tests\nR=reviewer@chromium.org\n'
      );
    });

    test('indented lines are untouched', () => {
      const message =
        'Fix the thing\n\n    This is an indented line.\n        This is another indented line.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n    This is an indented line.\n        This is another indented line.\n\nChange-Id: abcdefg\n'
      );
    });

    test('quoted lines are untouched', () => {
      const message =
        'Fix the thing\n\n> This is a quoted line.\n> And another one.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n> This is a quoted line.\n> And another one.\n\nChange-Id: abcdefg\n'
      );
    });

    test('code blocks are untouched', () => {
      const message =
        'Fix the thing\n\n```\nThis is a code block.\n  It should not be formatted.\n```\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n```\nThis is a code block.\n  It should not be formatted.\n```\n\nChange-Id: abcdefg\n'
      );
    });

    test('bullet points are untouched', () => {
      const message =
        'Fix the thing\n\n - This is a bullet point.\n * This is another one.\n   + And one more.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n - This is a bullet point.\n * This is another one.\n   + And one more.\n\nChange-Id: abcdefg\n'
      );
    });

    test('body line starting with spaces is untouched', () => {
      const message =
        'Fix the thing\n\n   - This is a body line that starts with spaces and should be untouched\n   \n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n   - This is a body line that starts with spaces and should be untouched\n\nChange-Id: abcdefg\n'
      );
    });

    test('paragraph with list creating with leading space is untouched', () => {
      const message = 'Fix the thing\n\nList:\n  item1\n  item2\n\nBug: 123\n';
      assert.equal(formatCommitMessageString(message), message);
    });

    test('bullet points are not split', () => {
      const message =
        'Fix the thing\n\n- Uses a test buffer to store the result to avoid issue with rendering.\n' +
        '  This new buffer\n' +
        '- Test a new buffer when the input stream is reconfigured and\n' +
        '  call it.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n- Uses a test buffer to store the result to avoid issue with rendering.\n' +
          '  This new buffer\n' +
          '- Test a new buffer when the input stream is reconfigured and call it.\n\nChange-Id: abcdefg\n'
      );
    });

    test('long bullet points are wrapped with hanging indentation', () => {
      const message =
        'Fix the thing\n\n' +
        '- This is a very long bullet point that exceeds the 72 character limit and should be wrapped with a two-space hanging indent.\n' +
        '  * This is an indented bullet point that also exceeds the 72 character limit and should be wrapped with a four-space hanging indent.\n' +
        '    - This is a third-level nested bullet that exceeds the 72 character limit and should be wrapped with a six-space hanging indent.\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n' +
          '- This is a very long bullet point that exceeds the 72 character limit\n' +
          '  and should be wrapped with a two-space hanging indent.\n' +
          '  * This is an indented bullet point that also exceeds the 72 character\n' +
          '    limit and should be wrapped with a four-space hanging indent.\n' +
          '    - This is a third-level nested bullet that exceeds the 72 character\n' +
          '      limit and should be wrapped with a six-space hanging indent.\n\n' +
          'Change-Id: abcdefg\n'
      );
    });

    test('bullet points with unindented continuation lines are reflowed with hanging indent', () => {
      const message =
        'Fix the thing\n\n' +
        '- First bullet point that was manually wrapped onto the next\n' +
        'line without any leading spaces and continues even further.\n' +
        '- Second bullet point is short.\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n' +
          '- First bullet point that was manually wrapped onto the next line\n' +
          '  without any leading spaces and continues even further.\n' +
          '- Second bullet point is short.\n\n' +
          'Change-Id: abcdefg\n'
      );
    });

    test('unindented paragraph after bullet point ending with period or short bullet is not merged', () => {
      const message =
        'Fix the thing\n\n' +
        '- First bullet point is over sixty characters long and ends with period.\n' +
        'This is a separate paragraph immediately following a long bullet.\n\n' +
        '- Short bullet without period\n' +
        'This is a separate paragraph immediately following a short bullet.\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(formatCommitMessageString(message), message);
    });

    test('4-space indented code block after bullet point is untouched', () => {
      const message =
        'Fix the thing\n\n' +
        '- Update the helper function:\n' +
        '    const veryLongCodeLine = someFunctionWithVeryLongArgumentsThatExceeds72Characters();\n' +
        '    - diff line inside 4-space indented block that exceeds 72 characters in length\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n' +
          '- Update the helper function:\n' +
          '    const veryLongCodeLine = someFunctionWithVeryLongArgumentsThatExceeds72Characters();\n' +
          '    - diff line inside 4-space indented block that exceeds 72 characters in length\n\n' +
          'Change-Id: abcdefg\n'
      );
    });

    test('numbered lists are kept separate and wrapped with hanging indentation', () => {
      const message =
        'Fix the thing\n\n' +
        '1. First item\n' +
        '2. Second item that is very long and exceeds the 72 character limit so it should wrap with three spaces.\n' +
        '10. Tenth item that is also very long and exceeds the 72 character limit so it should wrap with four spaces.\n' +
        '1) Parenthesized item.\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n' +
          '1. First item\n' +
          '2. Second item that is very long and exceeds the 72 character limit so\n' +
          '   it should wrap with three spaces.\n' +
          '10. Tenth item that is also very long and exceeds the 72 character limit\n' +
          '    so it should wrap with four spaces.\n' +
          '1) Parenthesized item.\n\n' +
          'Change-Id: abcdefg\n'
      );
    });

    test('indented block after blank line preserves indentation and wraps long lines', () => {
      const message =
        'Fix the thing\n\n' +
        'List:\n\n' +
        '  item1: short line\n' +
        '  item2: very long indented line that exceeds the 72 character limit and should wrap preserving its indent\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n' +
          'List:\n\n' +
          '  item1: short line\n' +
          '  item2: very long indented line that exceeds the 72 character limit and\n' +
          '  should wrap preserving its indent\n\n' +
          'Change-Id: abcdefg\n'
      );
    });

    test('bullet list at end of message with colon in continuation is not treated as footer', () => {
      const message =
        'Fix the thing\n\n' +
        '- First bullet point that is quite long and exceeds the 72 character limit for body lines.\n' +
        '  Note: this continuation has a colon';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n' +
          '- First bullet point that is quite long and exceeds the 72 character\n' +
          '  limit for body lines. Note: this continuation has a colon'
      );
    });

    test('mid-paragraph wrapped asterisk or list marker is not treated as list item', () => {
      const message =
        'Fix the thing\n\n' +
        'for `item_byte_size`, `shape`, and `strides`. Consequently, `strides[0]\n' +
        '* item_byte_size` overflowed in 32-bit unsigned arithmetic and wrapped\n' +
        'around modulo 2^32 before widening to `size_t`.\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(formatCommitMessageString(message), message);
    });

    test('short single-line bullet points preserve internal column alignment', () => {
      const message =
        'Fix the thing\n\n' +
        'Speedups:\n' +
        '* checkstyle (300 files):          8.35s -> 4.48s  (1.86x)\n' +
        '* turbine (chrome_java header):    1.60s -> 1.07s  (1.49x)\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(formatCommitMessageString(message), message);
    });

    test('indented single-token footer example in body is not split', () => {
      const message =
        'Fix the thing\n\n' +
        'Add the following footer to your CL description:\n' +
        '  Include-Ci-Only-Tests: internal.client.clank:android-14-arm64-rel-tests|chrome_public_test_apk\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(formatCommitMessageString(message), message);
    });

    test('bullet points with preceding text are not reordered', () => {
      const message =
        'Add a content scrim view\n\n' +
        'Some description.\n\n' +
        'screenshots,\n' +
        '- https://example.com/1\n' +
        '- https://example.com/2\n' +
        '- https://example.com/3\n\n' +
        'Change-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Add a content scrim view\n\n' +
          'Some description.\n\n' +
          'screenshots,\n' +
          '- https://example.com/1\n' +
          '- https://example.com/2\n' +
          '- https://example.com/3\n\n' +
          'Change-Id: abcdefg\n'
      );
    });

    test('reproduction of bug: multiple paragraphs wrapping', () => {
      const message =
        'Generic subject that is long enough\n\n' +
        'This is the first paragraph of the body which is quite long and should be wrapped correctly by the formatter without any issues.\n\n' +
        'This is the second paragraph which triggered the bug because it looks like a footer due to a colon: happening after some words. This should be wrapped.';

      const expected =
        'Generic subject that is long enough\n\n' +
        'This is the first paragraph of the body which is quite long and should\n' +
        'be wrapped correctly by the formatter without any issues.\n\n' +
        'This is the second paragraph which triggered the bug because it looks\n' +
        'like a footer due to a colon: happening after some words. This should be\n' +
        'wrapped.';

      assert.equal(formatCommitMessageString(message), expected);
    });
  });

  suite('detectFormattingErrorsInString', () => {
    function assertError(
      errors: FormattingError[],
      type: ErrorType,
      line: number,
      message: string
    ) {
      assert.isTrue(
        errors.some(
          error =>
            error.type === type &&
            error.line === line &&
            error.message === message
        ),
        `Expected error ${type} on line ${line} with message "${message}, but` +
          JSON.stringify(errors)
      );
    }

    test('subject too long', () => {
      const longSubject =
        'Fix(some-component): This is a very long subject that exceeds 72 characters';
      const message = longSubject + '\n\nThis is body.\n\nChange-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.SUBJECT_TOO_LONG,
        1,
        'Subject exceeds 72 characters'
      );
    });

    test('subject too long produces only 1 error', () => {
      const longSubject =
        'Fix(some-component): This is a very long subject that exceeds 72 characters';
      const message = longSubject;
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.SUBJECT_TOO_LONG,
        1,
        'Subject exceeds 72 characters'
      );
      assert.equal(errors.length, 1);
    });

    test('subject has leading spaces', () => {
      const message =
        '  Fix the thing\n\nThis is the body.\n\nChange-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.LEADING_SPACES,
        1,
        'Subject should not start with spaces'
      );
    });

    test('subject has trailing spaces', () => {
      const message =
        'Fix the thing    \n\nThis is the body.\n\nChange-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.TRAILING_SPACES,
        1,
        'Subject should not end with spaces'
      );
    });

    test('line too long', () => {
      const message =
        'Fix the thing\n\nThis is a very long line that exceeds the maximum allowed length of 72 characters.\n\nChange-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.LINE_TOO_LONG,
        3,
        'Line exceeds 72 characters'
      );
    });

    test('line too long in footer', () => {
      const message =
        'Fix the thing\n\nThis is body.\n\nChange-Id: This is a very long line that exceeds the maximum allowed length of 72 characters.\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.LINE_TOO_LONG,
        5,
        'Line exceeds 72 characters'
      );
    });

    test('line with url not flagged as too long', () => {
      const message =
        'Fix the thing\n\nThis line has a http://very-long-url-without-spaces-that-exceeds-72-characters.com/with/some/path/and/query?params=and&more=params\n\nChange-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assert.isEmpty(errors);
    });

    test('trailing spaces', () => {
      const message =
        'Fix the thing\n\nThis is a line with trailing spaces.   \nAnd another one.  \n\nChange-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.TRAILING_SPACES,
        3,
        'Line should not end with spaces'
      );
      assertError(
        errors,
        ErrorType.TRAILING_SPACES,
        4,
        'Line should not end with spaces'
      );
    });

    test('trailing spaces in footer', () => {
      const message =
        'Fix the thing\n\nThis is body.\n\nChange-Id: abcdefg    \n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.TRAILING_SPACES,
        5,
        'Line should not end with spaces'
      );
    });

    test('extra blank lines', () => {
      const message =
        'Fix the thing\n\nThis is the body.\n\n\nAnd another paragraph.\n\nChange-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.EXTRA_BLANK_LINE,
        5,
        'Consecutive blank lines are not allowed'
      );
    });

    test('extra blank lines in footer', () => {
      const message =
        'Fix the thing\n\nThis is the body.\n\nTest: 123\n\n\nChange-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.EXTRA_BLANK_LINE,
        7,
        'Consecutive blank lines are not allowed'
      );
    });

    test('line in footer starts with spaces', () => {
      const message = 'Fix the thing\n\nThis is body.\n   Change-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.LEADING_SPACES,
        5,
        'Line should not start with spaces'
      );
    });

    test('comment lines', () => {
      const message =
        'Fix the thing\n\n# This is a comment line.\nThis is body.\n# Another comment line in body.\n\nChange-Id: abcdefg\n# Comment line in footer\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.COMMENT_LINE,
        3,
        "'#' at line start is a comment marker in Git. Line will be ignored"
      );
      assertError(
        errors,
        ErrorType.COMMENT_LINE,
        5,
        "'#' at line start is a comment marker in Git. Line will be ignored"
      );
    });

    test('bullet and numbered list lines exceeding 72 chars are flagged', () => {
      const message =
        'Fix the thing\n\n' +
        '- This is a very long bullet point that exceeds the maximum allowed length of 72 characters.\n' +
        '1. This is a very long numbered item that exceeds the maximum allowed length of 72 characters.\n\n' +
        'Change-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.LINE_TOO_LONG,
        3,
        'Line exceeds 72 characters'
      );
      assertError(
        errors,
        ErrorType.LINE_TOO_LONG,
        4,
        'Line exceeds 72 characters'
      );
    });

    test('4-space indented code block exceeding 72 chars is not flagged', () => {
      const message =
        'Fix the thing\n\n' +
        '    const veryLongCodeLine = someFunctionWithVeryLongArgumentsThatExceeds72Characters();\n' +
        '- Bullet item followed by 4-space code block:\n' +
        '    const anotherVeryLongCodeLine = someFunctionWithVeryLongArgumentsThatExceeds72Characters();\n\n' +
        'Change-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assert.isEmpty(errors);
    });

    test('indented bullet continuation exceeding 72 chars is flagged', () => {
      const message =
        'Fix the thing\n\n' +
        '  * Indented bullet item with a 4-space continuation line\n' +
        '    that is very long and exceeds the maximum allowed length of 72 characters.\n\n' +
        'Change-Id: abcdefg\n';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.LINE_TOO_LONG,
        4,
        'Line exceeds 72 characters'
      );
    });
  });

  suite('parseCommitMessageString', () => {
    function assertParseResult(
      message: string,
      expected: CommitMessage,
      messageDescription: string
    ) {
      const actual = parseCommitMessageString(message);
      assert.deepEqual(
        actual,
        expected,
        `Test Case Failed: ${messageDescription}\nInput Message:\n${message}`
      );
    }

    test('empty message', () => {
      assertParseResult(
        '',
        {subject: '', body: [], footer: [], hasTrailingBlankLine: false},
        'Empty message should parse to empty subject, body, and footer'
      );
    });

    test('single line subject', () => {
      assertParseResult(
        'Subject only',
        {
          subject: 'Subject only',
          body: [],
          footer: [],
          hasTrailingBlankLine: false,
        },
        'Single line subject should parse correctly'
      );
    });

    test('body and footer without blank line separator', () => {
      assertParseResult(
        'Subject\n\nBody line\n\nFooter line 1\nfooter: Footer line 2',
        {
          subject: 'Subject',
          body: ['Body line'],
          footer: ['Footer line 1', 'footer: Footer line 2'],
          hasTrailingBlankLine: false,
        },
        'body and footer without blank line separator'
      );
    });

    test('with trailing blank line', () => {
      assertParseResult(
        'Fix the thing\n\nThis is the body.\n\nFixes: #123\nChange-Id: abcdefg\n',
        {
          subject: 'Fix the thing',
          body: ['This is the body.'],
          footer: ['Fixes: #123', 'Change-Id: abcdefg'],
          hasTrailingBlankLine: true,
        },
        'with trailing blank line'
      );
    });

    test('without extra blank line after subject', () => {
      assertParseResult(
        'Fix the thing\nThis is the body.\n\nFixes: #123\nChange-Id: abcdefg\n',
        {
          subject: 'Fix the thing',
          body: ['This is the body.'],
          footer: ['Fixes: #123', 'Change-Id: abcdefg'],
          hasTrailingBlankLine: true,
        },
        'with trailing blank line'
      );
    });

    test('footers separated by a blank line', () => {
      assert.equal(
        formatCommitMessageString(
          'Fix the thing\n\nThis is the body.\n\nTest: 123\n\nChange-Id: abcdefg\n'
        ),
        'Fix the thing\n\nThis is the body.\n\nTest: 123\nChange-Id: abcdefg\n'
      );
    });

    test('footer without proper format is moved to body', () => {
      assertParseResult(
        'Subject\n\nBody line\n\nThis is not a proper footer line\nNeither is this one',
        {
          subject: 'Subject',
          body: [
            'Body line',
            '',
            'This is not a proper footer line',
            'Neither is this one',
          ],
          footer: [],
          hasTrailingBlankLine: false,
        },
        'footer without proper format should be moved to body'
      );
    });

    test('footer with at least one proper format line is kept as footer', () => {
      assertParseResult(
        'Subject\n\nBody line\n\nThis is not a proper footer line\nSigned-off-by: User <user@example.com>',
        {
          subject: 'Subject',
          body: ['Body line'],
          footer: [
            'This is not a proper footer line',
            'Signed-off-by: User <user@example.com>',
          ],
          hasTrailingBlankLine: false,
        },
        'footer with at least one proper format line should be kept as footer'
      );
    });

    test('footer with equals sign format line is kept as footer', () => {
      assertParseResult(
        'Subject\n\nBody line\n\nTAG=agy\nCONV=fe186e29-8ffb-4bb8-a778-d48e3c804048',
        {
          subject: 'Subject',
          body: ['Body line'],
          footer: ['TAG=agy', 'CONV=fe186e29-8ffb-4bb8-a778-d48e3c804048'],
          hasTrailingBlankLine: false,
        },
        'footer with equals sign format line should be kept as footer'
      );
    });

    test('indented list with key: value lines is kept in body', () => {
      assertParseResult(
        'Subject\n\nBody line\n\nList:\n  item1: val1\n  item2: val2',
        {
          subject: 'Subject',
          body: ['Body line', '', 'List:', '  item1: val1', '  item2: val2'],
          footer: [],
          hasTrailingBlankLine: false,
        },
        'indented list with key: value lines should be moved to body'
      );
      assertParseResult(
        'Subject\n\nList:\n\n  item1: val1\n  item2: val2',
        {
          subject: 'Subject',
          body: ['List:', '', '  item1: val1', '  item2: val2'],
          footer: [],
          hasTrailingBlankLine: false,
        },
        'indented list after blank line should be moved to body'
      );
      assertParseResult(
        'Subject\n\nExample:\n\n    timeout: 30s',
        {
          subject: 'Subject',
          body: ['Example:', '', '    timeout: 30s'],
          footer: [],
          hasTrailingBlankLine: false,
        },
        'single-line 4-space code block with key: value should be moved to body'
      );
    });
  });
});
