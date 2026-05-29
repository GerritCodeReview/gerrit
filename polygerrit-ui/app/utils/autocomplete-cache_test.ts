/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {AutocompleteCache} from './autocomplete-cache';
import {assert} from '@open-wc/testing';

suite('AutocompleteCache', () => {
  let cache: AutocompleteCache;

  setup(() => {
    cache = new AutocompleteCache();
  });

  const cacheSet = (draftContent: string, commentCompletion: string) => {
    cache.set({draftContent, commentCompletion});
  };

  const assertCacheEqual = (
    draftContent: string,
    expectedCommentCompletion?: string
  ) => {
    assert.equal(
      cache.get(draftContent)?.commentCompletion,
      expectedCommentCompletion
    );
  };

  test('should get and set values', () => {
    cacheSet('foo', 'bar');
    assertCacheEqual('foo', 'bar');
  });

  test('should return undefined for empty content string', () => {
    cacheSet('foo', 'bar');
    assertCacheEqual('', undefined);
  });

  test('should return a value, if completion content+hint start with content', () => {
    cacheSet('foo', 'bar');
    assertCacheEqual('foo', 'bar');
    assertCacheEqual('foob', 'ar');
    assertCacheEqual('fooba', 'r');
    assertCacheEqual('foobar', undefined);
  });

  test('should not return a value, if content is shorter than completion content', () => {
    cacheSet('foo', 'bar');
    assertCacheEqual('f', undefined);
    assertCacheEqual('fo', undefined);
  });

  test('should not get values that are not set', () => {
    assertCacheEqual('foo', undefined);
  });

  test('should not return an empty completion, if content equals completion content+hint', () => {
    cacheSet('foo', 'bar');
    assertCacheEqual('foobar', undefined);
  });

  test('skips over the first entry, but returns the second entry', () => {
    cacheSet('foobar', 'bang');
    cacheSet('foo', 'bar');
    assertCacheEqual('foobar', 'bang');
  });

  test('replaces entries', () => {
    cacheSet('foo', 'bar');
    cacheSet('foo', 'baz');
    assertCacheEqual('foo', 'baz');
  });

  test('prefers newer entries, but also returns older entries', () => {
    cacheSet('foo', 'bar');
    assertCacheEqual('foob', 'ar');
    cacheSet('foob', 'arg');
    assertCacheEqual('foob', 'arg');
    assertCacheEqual('foo', 'bar');
  });

  test('capacity', () => {
    cache = new AutocompleteCache(1);
    cacheSet('foo', 'bar');
    cacheSet('boom', 'bang');
    assertCacheEqual('foo', undefined);
  });
});
