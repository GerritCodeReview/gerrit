/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
import {
  formatCommitMessageString,
  detectFormattingErrorsInString,
  ErrorType,
  FormattingError,
} from './commit-message-formatter-util';

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

    test('bullet points are not split', () => {
      const message =
        'Fix the thing\n\n- Uses a test buffer to store the result to avoid issue.\n' +
        '  This new buffer\n' +
        '- Test a new buffer\n' +
        '  call it.\n\nChange-Id: abcdefg\n';
      assert.equal(
        formatCommitMessageString(message),
        'Fix the thing\n\n- Uses a test buffer to store the result to avoid issue.\n' +
          '  This new buffer\n' +
          '- Test a new buffer\n' +
          '  call it.\n\nChange-Id: abcdefg\n'
      );
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
      const message =
        'Fix the thing\n\nThis is body.\n   Change-Id: abcdefg\n      ';
      const errors = detectFormattingErrorsInString(message);
      assertError(
        errors,
        ErrorType.LEADING_SPACES,
        4,
        'Line should not start with spaces'
      );
    });
  });
});
