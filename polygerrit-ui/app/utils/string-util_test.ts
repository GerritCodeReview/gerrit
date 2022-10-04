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
  getSharedPrefix,
  getSharedSuffix,
  trimMatching,
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

  test('getSharedPrefix', () => {
    // basic difference
    assert.equal(getSharedPrefix('01234567', '0123zz67'), '0123');

    // no difference
    assert.equal(getSharedPrefix('01234567', '01234567'), '01234567');

    // extra characters
    assert.equal(getSharedPrefix('01234', '0123'), '0123');
    assert.equal(getSharedPrefix('0123', '01234'), '0123');

    // empty string cases
    assert.equal(getSharedPrefix('', ''), '');
    assert.equal(getSharedPrefix('01234567', ''), '');
    assert.equal(getSharedPrefix('', '01234567'), '');
  });

  test('getSharedSuffix', () => {
    // basic difference
    assert.equal(getSharedSuffix('01234567', '0123zz67'), '67');

    // no difference
    assert.equal(getSharedSuffix('01234567', '01234567'), '01234567');

    // extra characters
    assert.equal(getSharedSuffix('01234', '0234'), '234');
    assert.equal(getSharedSuffix('01234', '0123'), '');
    assert.equal(getSharedSuffix('0123', '01234'), '');
    assert.equal(getSharedSuffix('0123', '123'), '123');

    // empty string cases
    assert.equal(getSharedSuffix('', ''), '');
    assert.equal(getSharedSuffix('01234567', ''), '');
    assert.equal(getSharedSuffix('', '01234567'), '');
  });

  test('trimMatching', () => {
    // match only on end
    assert.equal(trimMatching('foo bbb', 'aaa bar bbb'), 'foo');

    // match only at start
    assert.equal(trimMatching('aaa foo', 'aaa bar bbb'), 'foo');

    // match at both start and end
    assert.equal(trimMatching('aaa foo bbb', 'aaa bar bbb'), 'foo');

    // no match
    assert.equal(trimMatching('foo', 'bar'), 'foo');

    // internal match is ignored
    assert.equal(trimMatching('aaa 123 bbb', '123'), 'aaa 123 bbb');
  });
});
