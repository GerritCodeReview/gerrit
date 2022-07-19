/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-shell-command';
import {GrShellCommand} from './gr-shell-command';
import {GrCopyClipboard} from '../gr-copy-clipboard/gr-copy-clipboard';
import {queryAndAssert} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-shell-command');

suite('gr-shell-command tests', () => {
  let element: GrShellCommand;

  setup(async () => {
    element = basicFixture.instantiate();
    element.command = `git fetch http://gerrit@localhost:8080/a/test-project
        refs/changes/05/5/1 && git checkout FETCH_HEAD`;
    await flush();
  });

  test('render', async () => {
    element.label = 'label1';
    await element.updateComplete;

    expect(element).shadowDom.to.equal(/* HTML */ `
      <label> label1 </label>
      <div class="commandContainer">
        <gr-copy-clipboard buttontitle="" hastooltip=""> </gr-copy-clipboard>
      </div>
    `);
  });

  test('focusOnCopy', async () => {
    const focusStub = sinon.stub(
      queryAndAssert<GrCopyClipboard>(element, 'gr-copy-clipboard')!,
      'focusOnCopy'
    );
    await element.focusOnCopy();
    assert.isTrue(focusStub.called);
  });
});
