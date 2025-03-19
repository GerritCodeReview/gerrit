/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
import {
  pluralize,
  ordinal,
  listForSentence,
  diffFilePaths,
  escapeAndWrapSearchOperatorValue,
  wrapLine,
  formatCommitMessage,
} from './string-util';

suite('string-util tests', () => {
  test('pluralize', () => {
    const noun = 'comment';
    assert.equal(pluralize(0, noun), '');
    assert.equal(pluralize(1, noun), '1 comment');
    assert.equal(pluralize(2, noun), '2 comments');
  });

  test('ordinal', () => {
    assert.equal(ordinal(0), '0th');
    assert.equal(ordinal(1), '1st');
    assert.equal(ordinal(2), '2nd');
    assert.equal(ordinal(3), '3rd');
    assert.equal(ordinal(4), '4th');
    assert.equal(ordinal(10), '10th');
    assert.equal(ordinal(11), '11th');
    assert.equal(ordinal(12), '12th');
    assert.equal(ordinal(13), '13th');
    assert.equal(ordinal(44413), '44413th');
    assert.equal(ordinal(44451), '44451st');
  });

  test('listForSentence', () => {
    assert.equal(listForSentence(['Foo', 'Bar', 'Baz']), 'Foo, Bar, and Baz');
    assert.equal(listForSentence(['Foo', 'Bar']), 'Foo and Bar');
    assert.equal(listForSentence(['Foo']), 'Foo');
    assert.equal(listForSentence([]), '');
  });

  test('diffFilePaths', () => {
    const path = 'some/new/path/to/foo.js';

    // no other path
    assert.deepStrictEqual(diffFilePaths(path, undefined), {
      matchingFolders: '',
      newFolders: 'some/new/path/to/',
      fileName: 'foo.js',
    });
    // no new folders
    assert.deepStrictEqual(diffFilePaths(path, 'some/new/path/to/bar.js'), {
      matchingFolders: 'some/new/path/to/',
      newFolders: '',
      fileName: 'foo.js',
    });
    // folder partially matches
    assert.deepStrictEqual(diffFilePaths(path, 'some/ne/foo.js'), {
      matchingFolders: 'some/',
      newFolders: 'new/path/to/',
      fileName: 'foo.js',
    });
    // no matching folders
    assert.deepStrictEqual(
      diffFilePaths(path, 'another/path/entirely/foo.js'),
      {
        matchingFolders: '',
        newFolders: 'some/new/path/to/',
        fileName: 'foo.js',
      }
    );
    // some folders match
    assert.deepStrictEqual(diffFilePaths(path, 'some/other/path/to/bar.js'), {
      matchingFolders: 'some/',
      newFolders: 'new/path/to/',
      fileName: 'foo.js',
    });
    // no folders
    assert.deepStrictEqual(diffFilePaths('COMMIT_MSG', 'some/other/foo.js'), {
      matchingFolders: '',
      newFolders: '',
      fileName: 'COMMIT_MSG',
    });
  });

  test('escapeAndWrapSearchOperatorValue', () => {
    assert.equal(
      escapeAndWrapSearchOperatorValue('"value of \\: \\"something"'),
      '"\\"value of \\\\: \\\\\\"something\\""'
    );
  });

  test('formatCommitMessage', () => {
    assert.deepEqual(formatCommitMessage(''), '');
    assert.deepEqual(formatCommitMessage(' '), '');
    assert.deepEqual(formatCommitMessage('a'), 'A\n');
    assert.deepEqual(formatCommitMessage('a '), 'A\n');
    assert.deepEqual(formatCommitMessage(' \n'), '');
    assert.deepEqual(formatCommitMessage('A\n\nb'), 'A\n\nb\n');
    assert.deepEqual(formatCommitMessage('A\n\nb\n\nc'), 'A\n\nb\n\nc\n');
    assert.deepEqual(formatCommitMessage('A\n\n\n\n\nb'), 'A\n\nb\n');
    assert.deepEqual(formatCommitMessage('Remove dot.'), 'Remove dot\n');
    assert.deepEqual(formatCommitMessage('Remove dot .'), 'Remove dot\n');
    assert.deepEqual(
      formatCommitMessage(
        'This is a subject longer than 72 chars. But not that much longer. Just a bit.'
      ),
      'This is a subject longer than 72 chars. But not that much longer. Just a\n\nbit.\n'
    );
    assert.deepEqual(
      formatCommitMessage(
        'This is a subject\n\nasdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf\n\nasdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf'
      ),
      'This is a subject\n\nasdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf\nasdf asdf\n\nasdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf\nasdf asdf\n'
    );
    assert.deepEqual(
      formatCommitMessage(
        'This is a subject\n\nasdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf\n\nasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf'
      ),
      'This is a subject\n\nasdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf asdf\nasdf asdf\n\nasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf\n'
    );
  });

  test('wrapLine', () => {
    assert.deepEqual(wrapLine('', 10), []);
    assert.deepEqual(wrapLine(' ', 10), []);
    assert.deepEqual(wrapLine('  ', 10), []);
    assert.deepEqual(wrapLine('a', 10), ['a']);
    assert.deepEqual(wrapLine('asdf', 10), ['asdf']);
    assert.deepEqual(wrapLine('asdfasdfasdf', 10), ['asdfasdfasdf']);
    assert.deepEqual(wrapLine('asdf asdf asdf', 10), ['asdf asdf', 'asdf']);
    assert.deepEqual(wrapLine('a   b', 10), ['a   b']);
    assert.deepEqual(wrapLine('a   b          c', 10), ['a   b', 'c']);
    assert.deepEqual(wrapLine('asdf asdf asdf asdf asdf asdf asdf', 10), [
      'asdf asdf',
      'asdf asdf',
      'asdf asdf',
      'asdf',
    ]);
  });
});
