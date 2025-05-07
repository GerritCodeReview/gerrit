/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-shell-command';
import {GrShellCommand} from './gr-shell-command';
import {GrCopyClipboard} from '../gr-copy-clipboard/gr-copy-clipboard';
import {queryAndAssert} from '../../../test/test-utils';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-shell-command tests', () => {
  let element: GrShellCommand;

  setup(async () => {
    element = await fixture(html`<gr-shell-command></gr-shell-command>`);
    element.command = `git fetch http://gerrit@localhost:8080/a/test-project
        refs/changes/05/5/1 && git checkout FETCH_HEAD`;
    await element.updateComplete;
  });

  test('render', async () => {
    element.label = 'label1';
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <label> label1 </label>
        <div class="commandContainer">
          <gr-copy-clipboard buttontitle="" hastooltip=""> </gr-copy-clipboard>
        </div>
      `
    );
  });

  test('focusOnCopy', async () => {
    const focusStub = sinon.stub(
      queryAndAssert<GrCopyClipboard>(element, 'gr-copy-clipboard'),
      'focusOnCopy'
    );
    await element.focusOnCopy();
    assert.isTrue(focusStub.called);
  });
});
