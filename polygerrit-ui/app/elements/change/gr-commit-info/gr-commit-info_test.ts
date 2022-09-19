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
import {fixture, html, assert} from '@open-wc/testing';

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

  test('no web link when unavailable', () => {
    element.commitInfo = createCommit();
    element.serverConfig = createServerInfo();
    element.change = {...createChange(), labels: {}, project: '' as RepoName};

    assert.isNotOk(element._showWebLink);
  });

  test('use web link when available', () => {
    sinon.stub(GerritNav, 'getPatchSetWeblink').callsFake(() => {
      return {name: 'test-name', url: 'test-url'};
    });

    element.change = {...createChange(), labels: {}, project: '' as RepoName};
    element.commitInfo = {
      ...createCommit(),
      commit: 'commitsha' as CommitId,
      web_links: [{name: 'gitweb', url: 'link-url'}],
    };
    element.serverConfig = createServerInfo();

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'test-url');
  });
});
