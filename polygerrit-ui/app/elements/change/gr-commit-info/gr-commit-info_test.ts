/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-commit-info';
import {GrCommitInfo} from './gr-commit-info';
import {
  createCommit,
  createServerInfo,
} from '../../../test/test-data-generators';
import {CommitId} from '../../../types/common';
import {fixture, html, assert} from '@open-wc/testing';
import {queryAndAssert} from '../../../utils/common-util';

suite('gr-commit-info tests', () => {
  let element: GrCommitInfo;

  setup(async () => {
    element = await fixture(html`<gr-commit-info></gr-commit-info>`);
    element.serverConfig = createServerInfo();
  });

  test('render no weblink', async () => {
    element.commitInfo = createCommit();
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container">
          <a href="" rel="noopener" target="_blank"> </a>
          <gr-copy-clipboard hastooltip="" hideinput=""> </gr-copy-clipboard>
        </div>
      `
    );
  });

  test('web link from commit info', async () => {
    element.commitInfo = {
      ...createCommit(),
      commit: 'sha45678901234567890' as CommitId,
      web_links: [{name: 'gitweb', url: 'link-url'}],
    };
    await element.updateComplete;

    assert.dom.equal(
      queryAndAssert(element, 'a'),
      /* HTML */ '<a href="link-url" rel="noopener" target="_blank">sha4567</a>'
    );
  });

  test('web link fall back to search query', async () => {
    element.commitInfo = {
      ...createCommit(),
      commit: 'sha45678901234567890' as CommitId,
    };
    await element.updateComplete;

    assert.dom.equal(
      queryAndAssert(element, 'a'),
      /* HTML */ '<a href="/q/sha4567" rel="noopener" target="_blank">sha4567</a>'
    );
  });
});
