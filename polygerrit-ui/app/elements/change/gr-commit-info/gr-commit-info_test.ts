/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-commit-info';
import {GrCommitInfo} from './gr-commit-info';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  createChange,
  createCommit,
  createServerInfo,
} from '../../../test/test-data-generators';
import {CommitId, RepoName} from '../../../types/common';
import {GrRouter} from '../../core/gr-router/gr-router';
import {fixture, html, assert} from '@open-wc/testing';
import {waitEventLoop} from '../../../test/test-utils';

suite('gr-commit-info tests', () => {
  let element: GrCommitInfo;

  setup(async () => {
    element = await fixture(html`<gr-commit-info></gr-commit-info>`);
  });

  test('render', async () => {
    element.change = createChange();
    element.commitInfo = createCommit();
    element.serverConfig = createServerInfo();
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container">
          <a href="/q/" rel="noopener" target="_blank"> </a>
          <gr-copy-clipboard hastooltip="" hideinput=""> </gr-copy-clipboard>
        </div>
      `
    );
  });

  test('weblinks use GerritNav interface', async () => {
    const weblinksStub = sinon
      .stub(GerritNav, '_generateWeblinks')
      .returns([{name: 'stubb', url: '#s'}]);
    element.change = createChange();
    element.commitInfo = createCommit();
    element.serverConfig = createServerInfo();
    await waitEventLoop();
    assert.isTrue(weblinksStub.called);
  });

  test('no web link when unavailable', () => {
    element.commitInfo = createCommit();
    element.serverConfig = createServerInfo();
    element.change = {...createChange(), labels: {}, project: '' as RepoName};

    assert.isNotOk(element._showWebLink);
  });

  test('use web link when available', () => {
    const router = new GrRouter();
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router.generateWeblinks.bind(router));

    element.change = {...createChange(), labels: {}, project: '' as RepoName};
    element.commitInfo = {
      ...createCommit(),
      commit: 'commitsha' as CommitId,
      web_links: [{name: 'gitweb', url: 'link-url'}],
    };
    element.serverConfig = createServerInfo();

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'link-url');
  });

  test('does not relativize web links that begin with scheme', () => {
    const router = new GrRouter();
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router.generateWeblinks.bind(router));

    element.change = {...createChange(), labels: {}, project: '' as RepoName};
    element.commitInfo = {
      ...createCommit(),
      commit: 'commitsha' as CommitId,
      web_links: [{name: 'gitweb', url: 'https://link-url'}],
    };
    element.serverConfig = createServerInfo();

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'https://link-url');
  });

  test('ignore web links that are neither gitweb nor gitiles', () => {
    const router = new GrRouter();
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router.generateWeblinks.bind(router));

    element.change = {...createChange(), project: 'project-name' as RepoName};
    element.commitInfo = {
      ...createCommit(),
      commit: 'commit-sha' as CommitId,
      web_links: [
        {
          name: 'ignore',
          url: 'ignore',
        },
        {
          name: 'gitiles',
          url: 'https://link-url',
        },
      ],
    };
    element.serverConfig = createServerInfo();

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'https://link-url');

    // Remove gitiles link.
    element.commitInfo = {
      ...createCommit(),
      commit: 'commit-sha' as CommitId,
      web_links: [
        {
          name: 'ignore',
          url: 'ignore',
        },
      ],
    };
    assert.isNotOk(element._showWebLink);
    assert.isNotOk(element._webLink);
  });
});
