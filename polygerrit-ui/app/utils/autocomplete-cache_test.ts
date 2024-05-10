/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AutocompleteCache} from './autocomplete-cache';
import {assert} from '@open-wc/testing';

suite('AutocompleteCache', () => {
  let cache: AutocompleteCache;

  setup(() => {
    cache = new AutocompleteCache();
  });

  test('should get and set values', () => {
    cache.set('foo', 'bar');
    assert.equal(cache.get('foo'), 'bar');
  });

  test('should return undefined for empty content string', () => {
    cache.set('foo', 'bar');
    assert.equal(cache.get(''), undefined);
  });

  test('should return a value, if completion content+hint start with content', () => {
    cache.set('foo', 'bar');
    assert.equal(cache.get('foo'), 'bar');
    assert.equal(cache.get('foob'), 'ar');
    assert.equal(cache.get('fooba'), 'r');
    assert.equal(cache.get('foobar'), undefined);
  });

  test('should not return a value, if content is shorter than completion content', () => {
    cache.set('foo', 'bar');
    assert.equal(cache.get('f'), undefined);
    assert.equal(cache.get('fo'), undefined);
  });

  test('should not get values that are not set', () => {
    assert.equal(cache.get('foo'), undefined);
  });

  test('should not return an empty completion, if content equals completion content+hint', () => {
    cache.set('foo', 'bar');
    assert.equal(cache.get('foobar'), undefined);
  });

  test('skips over the first entry, but returns the second entry', () => {
    cache.set('foobar', 'bang');
    cache.set('foo', 'bar');
    assert.equal(cache.get('foobar'), 'bang');
  });

  test('replaces entries', () => {
    cache.set('foo', 'bar');
    cache.set('foo', 'baz');
    assert.equal(cache.get('foo'), 'baz');
  });

  test('prefers newer entries, but also returns older entries', () => {
    cache.set('foo', 'bar');
    assert.equal(cache.get('foob'), 'ar');
    cache.set('foob', 'arg');
    assert.equal(cache.get('foob'), 'arg');
    assert.equal(cache.get('foo'), 'bar');
  });

  test('capacity', () => {
    cache = new AutocompleteCache(1);
    cache.set('foo', 'bar');
    cache.set('boom', 'bang');
    assert.equal(cache.get('foo'), undefined);
  });
});
