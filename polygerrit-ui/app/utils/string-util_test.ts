/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
import {
  capitalizeFirstLetter,
  charsOnly,
  diffFilePaths,
  escapeAndWrapSearchOperatorValue,
  hasMeInQuery,
  isCharacterLetter,
  isUpperCase,
  levenshteinDistance,
  listForSentence,
  ordinal,
  pluralize,
  translateMeInQuery,
  trimWithEllipsis,
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

  test('trimWithEllipsis', () => {
    assert.equal(trimWithEllipsis('asdf', 10), 'asdf');
    assert.equal(trimWithEllipsis('asdf', 4), 'asdf');
    assert.equal(trimWithEllipsis('asdf', 3), '...');
    assert.equal(trimWithEllipsis('asdf qwer', 5), 'as...');
    assert.equal(trimWithEllipsis('asdf qwer', 8), 'asdf ...');
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

  test('charsOnly', () => {
    assert.equal(charsOnly('Hello123'), 'Hello');
    assert.equal(charsOnly('123Hello'), 'Hello');
    assert.equal(charsOnly('Hello World!'), 'HelloWorld');
    assert.equal(charsOnly('!@#$%^&*()'), '');
    assert.equal(charsOnly(''), '');
  });

  test('isCharacterLetter', () => {
    assert.isTrue(isCharacterLetter('a'));
    assert.isTrue(isCharacterLetter('Z'));
    assert.isFalse(isCharacterLetter('1'));
    assert.isFalse(isCharacterLetter('!'));
    assert.isFalse(isCharacterLetter(''));
    assert.isFalse(isCharacterLetter('ab'));
  });

  test('isUpperCase', () => {
    assert.isTrue(isUpperCase('A'));
    assert.isTrue(isUpperCase('Z'));
    assert.isFalse(isUpperCase('a'));
    assert.isFalse(isUpperCase('z'));
    assert.isTrue(isUpperCase('AB'));
    assert.isTrue(isUpperCase('1'));
  });

  test('capitalizeFirstLetter', () => {
    assert.equal(capitalizeFirstLetter('hello'), 'Hello');
    assert.equal(capitalizeFirstLetter('Hello'), 'Hello');
    assert.equal(capitalizeFirstLetter('HELLO'), 'HELLO');
    assert.equal(capitalizeFirstLetter(''), '');
    assert.equal(capitalizeFirstLetter('123hello'), '123hello');
  });

  test('levenshteinDistance', () => {
    // kitten -> sitten -> sittin -> sitting
    assert.equal(levenshteinDistance('kitten', 'sitting'), 3);
    assert.equal(levenshteinDistance('', ''), 0);
    assert.equal(levenshteinDistance('', 'abc'), 3);
    assert.equal(levenshteinDistance('abc', ''), 3);
    assert.equal(levenshteinDistance('same', 'same'), 0);
  });

  test('hasMeInQuery', () => {
    assert.isTrue(hasMeInQuery('uploader:me'));
    assert.isTrue(hasMeInQuery('owner:me'));
    assert.isTrue(hasMeInQuery('owner: me'));
    assert.isTrue(hasMeInQuery('owner:"me"'));
    assert.isTrue(hasMeInQuery("owner:'me'"));
    assert.isTrue(hasMeInQuery('label:Code-Review+2,me'));
    assert.isTrue(hasMeInQuery('label:Code-Review>=1,me'));
    assert.isTrue(hasMeInQuery('draftby:me'));
    assert.isTrue(hasMeInQuery('attention:me'));
    assert.isTrue(hasMeInQuery('reviewedby:me'));

    // False cases
    assert.isFalse(hasMeInQuery('project:awesome-me'));
    assert.isFalse(hasMeInQuery('message:me'));
    assert.isFalse(hasMeInQuery('me'));
    assert.isFalse(hasMeInQuery('fixing me in comments'));
    assert.isFalse(hasMeInQuery('something,me,somethingElse'));
    assert.isFalse(hasMeInQuery('owner:me-awesome'));
    assert.isFalse(hasMeInQuery('owner:me@google.com'));
    assert.isFalse(hasMeInQuery('owner:foo@me.com'));
  });

  test('translateMeInQuery', () => {
    const email = 'user@example.com';
    assert.equal(translateMeInQuery('uploader:me', email), `uploader:${email}`);
    assert.equal(translateMeInQuery('owner:me', email), `owner:${email}`);
    assert.equal(translateMeInQuery('owner: me', email), `owner:${email}`);
    assert.equal(translateMeInQuery('owner:"me"', email), `owner:${email}`);
    assert.equal(translateMeInQuery("owner:'me'", email), `owner:${email}`);
    assert.equal(
      translateMeInQuery('label:Code-Review+2,me', email),
      `label:Code-Review+2,${email}`
    );
    assert.equal(
      translateMeInQuery('label:Code-Review=approve,me', email),
      `label:Code-Review=approve,${email}`
    );
    assert.equal(
      translateMeInQuery('label:Code-Review>=1,me', email),
      `label:Code-Review>=1,${email}`
    );
    assert.equal(
      translateMeInQuery('label:Code-Review<0,me', email),
      `label:Code-Review<0,${email}`
    );

    // Dollar sign in email address tests
    const dollarEmail = 'user$1@example.com';
    assert.equal(
      translateMeInQuery('uploader:me', dollarEmail),
      `uploader:${dollarEmail}`
    );
    assert.equal(
      translateMeInQuery('label:Code-Review+2,me', dollarEmail),
      `label:Code-Review+2,${dollarEmail}`
    );

    // Negative lookarounds (ensure we don't match me inside other words/emails)
    assert.equal(
      translateMeInQuery('owner:me-awesome', email),
      'owner:me-awesome'
    );
    assert.equal(
      translateMeInQuery('owner:awesome-me', email),
      'owner:awesome-me'
    );
    assert.equal(
      translateMeInQuery('owner:me@google.com', email),
      'owner:me@google.com'
    );
    assert.equal(
      translateMeInQuery('owner:foo@me.com', email),
      'owner:foo@me.com'
    );

    // New operators
    assert.equal(translateMeInQuery('draftby:me', email), `draftby:${email}`);
    assert.equal(
      translateMeInQuery('reviewedby:me', email),
      `reviewedby:${email}`
    );
    assert.equal(
      translateMeInQuery('attention:me', email),
      `attention:${email}`
    );

    // Ignore me elsewhere (e.g. project name or message content)
    assert.equal(
      translateMeInQuery('project:awesome-me', email),
      'project:awesome-me'
    );
    assert.equal(translateMeInQuery('message:me', email), 'message:me');
    assert.equal(translateMeInQuery('me', email), 'me');
    assert.equal(
      translateMeInQuery('fixing me in comments', email),
      'fixing me in comments'
    );
    assert.equal(
      translateMeInQuery('something,me,somethingElse', email),
      'something,me,somethingElse'
    );

    // Case insensitivity
    assert.equal(translateMeInQuery('OWNER:ME', email), `OWNER:${email}`);

    // If email is undefined
    assert.equal(translateMeInQuery('uploader:me', undefined), 'uploader:me');
  });
});
