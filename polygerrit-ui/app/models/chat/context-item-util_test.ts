/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {
  contextItemEquals,
  parseLink,
  searchForBugsInCommitMessage,
  searchForContextLinks,
} from './context-item-util';
import {ContextItem, ContextItemType} from '../../api/ai-code-review';
import sinon from 'sinon';

suite('context-item-util tests', () => {
  const fileContextItemType: ContextItemType = {
    id: 'file',
    name: 'File',
    icon: 'file_copy',
    regex: /file:\/\/([^\s]+)/,
    placeholder: 'file://...',
    parse: (input: string) => {
      const match = input.match(/file:\/\/(.*)/);
      if (!match) return undefined;
      return {
        type_id: 'file',
        link: input,
        title: match[1],
        identifier: match[1],
      };
    },
  };

  const anotherContextItemType: ContextItemType = {
    id: 'another',
    name: 'Another',
    icon: 'link',
    regex: /another:\/\/([^\s]+)/,
    placeholder: 'another://...',
    parse: (input: string) => {
      const match = input.match(/another:\/\/(.*)/);
      if (!match) return undefined;
      return {
        type_id: 'another',
        link: input,
        title: match[1],
        identifier: match[1],
      };
    },
  };

  const contextItemTypes = [fileContextItemType, anotherContextItemType];

  suite('searchForContextLinks', () => {
    test('finds single link', () => {
      const text = 'Here is a file: file:///a/b/c.txt';
      const result = searchForContextLinks(text, contextItemTypes);
      assert.lengthOf(result, 1);
      assert.equal(result[0].type_id, 'file');
      assert.equal(result[0].identifier, '/a/b/c.txt');
    });

    test('finds multiple links of different types', () => {
      const text = 'File: file:///a/b/c.txt and another: another:///x/y/z.ts';
      const result = searchForContextLinks(text, contextItemTypes);
      assert.lengthOf(result, 2);
      assert.equal(result[0].type_id, 'file');
      assert.equal(result[0].identifier, '/a/b/c.txt');
      assert.equal(result[1].type_id, 'another');
      assert.equal(result[1].identifier, '/x/y/z.ts');
    });

    test('deduplicates identical links', () => {
      const text = 'File: file:///a/b/c.txt and again file:///a/b/c.txt';
      const result = searchForContextLinks(text, contextItemTypes);
      assert.lengthOf(result, 1);
    });

    test('returns empty array when no links found', () => {
      const text = 'No links here.';
      const result = searchForContextLinks(text, contextItemTypes);
      assert.lengthOf(result, 0);
    });
  });

  suite('parseLink', () => {
    test('parses a valid link', () => {
      const url = 'file:///a/b/c.txt';
      const result = parseLink(url, contextItemTypes);
      assert.isOk(result);
      assert.equal(result.type_id, 'file');
      assert.equal(result.identifier, '/a/b/c.txt');
    });

    test('returns undefined for an invalid link', () => {
      const url = 'http://example.com';
      const result = parseLink(url, contextItemTypes);
      assert.isUndefined(result);
    });

    test('removes whitespace before parsing', () => {
      const url = ' file:///a/b/c.txt ';
      const result = parseLink(url, contextItemTypes);
      assert.isOk(result);
      assert.equal(result.type_id, 'file');
      assert.equal(result.identifier, '/a/b/c.txt');
    });
  });

  suite('contextItemEquals', () => {
    test('returns true for equal items', () => {
      const item1: ContextItem = {
        type_id: 'file',
        link: 'file:///a/b/c.txt',
        title: 'c.txt',
        identifier: '/a/b/c.txt',
      };
      const item2: ContextItem = {
        type_id: 'file',
        link: 'file:///a/b/c.txt',
        title: 'c.txt',
        identifier: '/a/b/c.txt',
      };
      assert.isTrue(contextItemEquals(item1, item2));
    });

    test('returns false for items with different type_id', () => {
      const item1: ContextItem = {
        type_id: 'file',
        link: 'file:///a/b/c.txt',
        title: 'c.txt',
        identifier: '/a/b/c.txt',
      };
      const item2: ContextItem = {
        type_id: 'another',
        link: 'another:///a/b/c.txt',
        title: 'c.txt',
        identifier: '/a/b/c.txt',
      };
      assert.isFalse(contextItemEquals(item1, item2));
    });

    test('returns false for items with different identifier', () => {
      const item1: ContextItem = {
        type_id: 'file',
        link: 'file:///a/b/c.txt',
        title: 'c.txt',
        identifier: '/a/b/c.txt',
      };
      const item2: ContextItem = {
        type_id: 'file',
        link: 'file:///x/y/z.txt',
        title: 'z.txt',
        identifier: '/x/y/z.txt',
      };
      assert.isFalse(contextItemEquals(item1, item2));
    });
  });

  suite('searchForBugsInCommitMessage', () => {
    let buganizerContextItemTypes: ContextItemType[];
    let parseSpy: sinon.SinonSpy;

    setup(() => {
      parseSpy = sinon.spy((url: string) => {
        const match = url.match(/b\/(\d+)/);
        if (match) {
          return {
            type_id: 'buganizer',
            title: `b/${match[1]}`,
            link: `http://b/${match[1]}`,
            identifier: match[1],
          };
        }
        return undefined;
      });

      buganizerContextItemTypes = [
        {
          id: 'buganizer',
          name: 'Buganizer',
          icon: 'bug_report',
          placeholder: 'b/...',
          regex:
            /(?:^|\s)(?:https:\/\/(?:b|buganizer)\.corp\.google\.com\/issues\/|b\/)([1-9]\d*)(?:\/.*)?/,
          parse: parseSpy,
        },
      ];
    });

    test('finds bug in "bug: 12345678" line', () => {
      const message = 'Fixes a bug.\n\nbug: 12345678';
      const result = searchForBugsInCommitMessage(
        message,
        buganizerContextItemTypes
      );
      assert.equal(result.length, 1);
      assert.equal(result[0].identifier, '12345678');
      assert.isTrue(parseSpy.calledWith('b/12345678'));
    });

    test('finds bug in "fixes: 12345678" line', () => {
      const message = 'Fixes a bug.\n\nfixes = 12345678';
      const result = searchForBugsInCommitMessage(
        message,
        buganizerContextItemTypes
      );
      assert.equal(result.length, 1);
      assert.equal(result[0].identifier, '12345678');
    });

    test('finds multiple bugs', () => {
      const message = 'Fixes a bug.\n\nbug: 12345678, 87654321';
      const result = searchForBugsInCommitMessage(
        message,
        buganizerContextItemTypes
      );
      assert.equal(result.length, 2);
      assert.deepEqual(
        result.map(r => r.identifier),
        ['12345678', '87654321']
      );
    });

    test('does not find bug if no keyword', () => {
      const message = 'This is a commit message.\n\n12345678';
      const result = searchForBugsInCommitMessage(
        message,
        buganizerContextItemTypes
      );
      assert.equal(result.length, 0);
    });

    test('does not find bug if keyword but no number', () => {
      const message = 'This is a commit message.\n\nbug: ';
      const result = searchForBugsInCommitMessage(
        message,
        buganizerContextItemTypes
      );
      assert.equal(result.length, 0);
    });

    test('ignores b/ links', () => {
      const message = 'Fixes a bug.\n\nbug: b/12345678';
      const result = searchForBugsInCommitMessage(
        message,
        buganizerContextItemTypes
      );
      assert.equal(result.length, 0);
    });

    test('ignores urls', () => {
      const message = 'Fixes a bug.\n\nbug: http://b/12345678';
      const result = searchForBugsInCommitMessage(
        message,
        buganizerContextItemTypes
      );
      assert.equal(result.length, 0);
    });

    test('ignores short numbers', () => {
      const message = 'Fixes a bug and 1 more feature on the count of 123.';
      const result = searchForBugsInCommitMessage(
        message,
        buganizerContextItemTypes
      );
      assert.equal(result.length, 0);
    });

    test('does not find bug in "debugging"', () => {
      const message = 'Enable debugging for feature X 12345678';
      const result = searchForBugsInCommitMessage(
        message,
        buganizerContextItemTypes
      );
      assert.equal(result.length, 0);
    });
  });
});
