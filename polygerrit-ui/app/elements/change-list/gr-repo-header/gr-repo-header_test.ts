/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './gr-repo-header';
import {GrRepoHeader} from './gr-repo-header';
import {stubRestApi} from '../../../test/test-utils';
import {RepoName, UrlEncodedRepoName} from '../../../types/common';

suite('gr-repo-header tests', () => {
  let element: GrRepoHeader;

  setup(async () => {
    element = await fixture(
      html`<gr-repo-header .repo=${'test' as RepoName}></gr-repo-header>`
    );
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="info">
          <h1 class="heading-1">test</h1>
          <hr />
          <div>
            <span> Detail: </span>
            <a href="/admin/repos/test"> Repo settings </a>
          </div>
          <div>
            <span class="browse"> Browse: </span>
          </div>
        </div>
      `
    );
  });

  test('repoUrl reset once repo changed', async () => {
    element.repo = undefined;
    await element.updateComplete;
    assert.equal(element._repoUrl, undefined);

    element.repo = 'test' as RepoName;
    await element.updateComplete;

    assert.equal(element._repoUrl, '/admin/repos/test');
  });

  test('webLinks set', async () => {
    const repoRes = {
      id: 'test' as UrlEncodedRepoName,
      web_links: [
        {
          name: 'gitiles',
          url: 'https://gerrit.test/g',
        },
      ],
    };
    stubRestApi('getRepo').returns(Promise.resolve(repoRes));
    element.repo = undefined;
    await element.updateComplete;

    assert.deepEqual(element._webLinks, []);

    element.repo = 'test' as RepoName;
    await element.updateComplete;

    assert.deepEqual(element._webLinks, repoRes.web_links);
  });
});
