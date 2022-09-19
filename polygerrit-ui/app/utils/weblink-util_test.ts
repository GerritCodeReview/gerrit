/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {WebLinkInfo, RepoName, CommitId} from '../api/rest-api';
import '../test/common-test-setup';
import {createServerInfo, createGerritInfo} from '../test/test-data-generators';
import {
  firstCodeBrowserWeblink,
  getBrowseCommitWeblink,
  getChangeWeblinks,
  WeblinkType,
} from './weblink-util';

suite('weblink util tests', () => {
  test('firstCodeBrowserWeblink', () => {
    assert.deepEqual(
      firstCodeBrowserWeblink([
        {name: 'gitweb'},
        {name: 'gitiles'},
        {name: 'browse'},
        {name: 'test'},
      ]),
      {name: 'gitiles'}
    );

    assert.deepEqual(
      firstCodeBrowserWeblink([{name: 'gitweb'}, {name: 'test'}]),
      {name: 'gitweb'}
    );
  });

  test('getBrowseCommitWeblink', () => {
    const browserLink = {name: 'browser', url: 'browser/url'};
    const link = {name: 'test', url: 'test/url'};
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
    const mapLinksToConfig = (weblinks: WebLinkInfo[]) => {
      return {
        type: 'change' as WeblinkType.CHANGE,
        repo: 'test' as RepoName,
        commit: '111' as CommitId,
        options: {weblinks},
      };
    };

    assert.deepEqual(
      getChangeWeblinks(mapLinksToConfig([link, browserLink]))[0],
      {name: 'test', url: 'test/url'}
    );

    assert.deepEqual(getChangeWeblinks(mapLinksToConfig([link]))[0], {
      name: 'test',
      url: 'test/url',
    });

    link.url = `https://${link.url}`;
    assert.deepEqual(getChangeWeblinks(mapLinksToConfig([link]))[0], {
      name: 'test',
      url: 'https://test/url',
    });
  });
});
