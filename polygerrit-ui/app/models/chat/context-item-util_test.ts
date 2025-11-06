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
  searchForContextLinks,
} from './context-item-util';
import {ContextItem, ContextItemType} from '../../api/ai-code-review';

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
});
