/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {BranchName, RepoName, TopicName} from '../../api/rest-api';
import '../../test/common-test-setup';
import {createSearchUrl, SearchUrlOptions} from './search';

suite('search view state tests', () => {
  test('createSearchUrl', () => {
    let options: SearchUrlOptions = {
      owner: 'a%b',
      project: 'c%d' as RepoName,
      branch: 'e%f' as BranchName,
      topic: 'g%h' as TopicName,
      statuses: ['op%en'],
    };
    assert.equal(
      createSearchUrl(options),
      '/q/owner:a%2525b+project:c%2525d+branch:e%2525f+' +
        'topic:g%2525h+status:op%2525en'
    );

    window.CANONICAL_PATH = '/base';
    assert.equal(createSearchUrl(options).substring(0, 5), '/base');
    window.CANONICAL_PATH = undefined;

    options.offset = 100;
    assert.equal(
      createSearchUrl(options),
      '/q/owner:a%2525b+project:c%2525d+branch:e%2525f+' +
        'topic:g%2525h+status:op%2525en,100'
    );
    delete options.offset;

    // The presence of the query param overrides other options.
    options.query = 'foo$bar';
    assert.equal(createSearchUrl(options), '/q/foo%2524bar');

    options.offset = 100;
    assert.equal(createSearchUrl(options), '/q/foo%2524bar,100');

    options = {statuses: ['a', 'b', 'c']};
    assert.equal(
      createSearchUrl(options),
      '/q/(status:a OR status:b OR status:c)'
    );

    options = {topic: 'test' as TopicName};
    assert.equal(createSearchUrl(options), '/q/topic:test');

    options = {topic: 'test test' as TopicName};
    assert.equal(createSearchUrl(options), '/q/topic:"test+test"');

    options = {topic: 'test:test' as TopicName};
    assert.equal(createSearchUrl(options), '/q/topic:"test:test"');
  });
});
