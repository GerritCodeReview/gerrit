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
  firstDifference,
  lastDifference,
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

  test('firstDifference', () => {
    // basic difference
    assert.equal(firstDifference('01234567', '0123zz67'), 4);

    // no difference
    assert.equal(firstDifference('01234567', '01234567'), -1);

    // extra characters
    assert.equal(firstDifference('01234', '0123'), 4);
    assert.equal(firstDifference('0123', '01234'), 4);

    // empty string cases
    assert.equal(firstDifference('', ''), -1);
    assert.equal(firstDifference('01234567', ''), 0);
    assert.equal(firstDifference('', '01234567'), 0);
  });

  test('lastDifference', () => {
    // basic difference
    assert.equal(lastDifference('01234567', '0123zz67'), 5);

    // no difference
    assert.equal(lastDifference('01234567', '01234567'), -1);

    // extra characters
    assert.equal(lastDifference('01234', '0234'), 1);
    assert.equal(lastDifference('01234', '0123'), 4);
    assert.equal(lastDifference('0123', '01234'), 3);
    assert.equal(lastDifference('0123', '123'), 0);

    // empty string cases
    assert.equal(lastDifference('', ''), -1);
    assert.equal(lastDifference('01234567', ''), 7);
    assert.equal(lastDifference('', '01234567'), 0);
  });

  test('trimMatching', () => {
    // match on end
    assert.equal(trimMatching('foo bbb', 'aaa bar bbb'), 'foo');

    // match at start
    assert.equal(trimMatching('aaa foo', 'aaa bar bbb'), 'foo');

    // match at start and end
    assert.equal(trimMatching('aaa foo bbb', 'aaa bar bbb'), 'foo');

    // no match
    assert.equal(trimMatching('foo', 'bar'), 'foo');

    // internal match is ignored
    assert.equal(trimMatching('aaa 123 bbb', '2'), 'aaa 123 bbb');
  });
});
