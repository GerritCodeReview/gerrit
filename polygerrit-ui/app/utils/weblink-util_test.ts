/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
import {createServerInfo, createGerritInfo} from '../test/test-data-generators';
import {
  getCodeBrowserWeblink,
  getBrowseCommitWeblink,
  getChangeWeblinks,
  getRepoWeblink,
} from './weblink-util';

suite('weblink util tests', () => {
  test('getCodeBrowserWeblink', () => {
    assert.deepEqual(
      getCodeBrowserWeblink([
        {name: 'gitweb', url: 'http://www.test.com'},
        {name: 'gitiles', url: 'http://www.test.com'},
        {name: 'browse', url: 'http://www.test.com'},
        {name: 'test', url: 'http://www.test.com'},
      ]),
      {name: 'gitiles', url: 'http://www.test.com'}
    );

    assert.deepEqual(
      getCodeBrowserWeblink([
        {name: 'gitweb', url: 'http://www.test.com'},
        {name: 'test', url: 'http://www.test.com'},
      ]),
      {name: 'gitweb', url: 'http://www.test.com'}
    );
  });

  test('getBrowseCommitWeblink', () => {
    const browserLink = {name: 'browser', url: 'browser/url'};
    const link = {name: 'gitiles', url: 'test/url'};
    const weblinks = [browserLink, link];
    const config = {
      ...createServerInfo(),
      gerrit: {...createGerritInfo(), primary_weblink_name: browserLink.name},
    };

    assert.deepEqual(getBrowseCommitWeblink(weblinks, config), browserLink);
    assert.deepEqual(getBrowseCommitWeblink(weblinks), link);
  });

  test('getChangeWeblinks', () => {
    const link = {name: 'test', url: 'test/url'};
    const browserLink = {name: 'browser', url: 'browser/url'};

    assert.deepEqual(getChangeWeblinks([link, browserLink])[0], {
      name: 'test',
      url: 'test/url',
    });

    assert.deepEqual(getChangeWeblinks([link])[0], {
      name: 'test',
      url: 'test/url',
    });

    link.url = `https://${link.url}`;
    assert.deepEqual(getChangeWeblinks([link])[0], {
      name: 'test',
      url: 'https://test/url',
    });
  });

  test('getRepoWeblink', () => {
    const browserLink = {name: 'browser', url: 'browser/url'};
    const link = {name: 'gitiles', url: 'test/url'};
    const weblinks = [browserLink, link];
    const config = {
      ...createServerInfo(),
      gerrit: {...createGerritInfo(), primary_weblink_name: browserLink.name},
    };

    assert.deepEqual(getRepoWeblink(weblinks, config), browserLink);
    assert.deepEqual(getRepoWeblink(weblinks), link);
  });
});
