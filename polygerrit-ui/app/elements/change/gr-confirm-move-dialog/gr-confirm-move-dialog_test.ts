/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-confirm-move-dialog';
import {GrConfirmMoveDialog} from './gr-confirm-move-dialog';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {BranchName, GitRef, RepoName} from '../../../types/common';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';

suite('gr-confirm-move-dialog tests', () => {
  let element: GrConfirmMoveDialog;

  setup(async () => {
    stubRestApi('getRepoBranches').callsFake((input: string) => {
      if (input.startsWith('test')) {
        return Promise.resolve([
          {
            ref: 'refs/heads/test-branch' as GitRef,
            revision: '67ebf73496383c6777035e374d2d664009e2aa5c',
            can_delete: true,
          },
        ]);
      } else {
        return Promise.resolve([]);
      }
    });
    element = await fixture(
      html`<gr-confirm-move-dialog
        .project=${'test-repo' as RepoName}
      ></gr-confirm-move-dialog>`
    );
  });

  test('render', async () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-dialog confirm-label="Move Change" role="dialog">
        <div class="header" slot="header">Move Change to Another Branch</div>
        <div class="main" slot="main">
          <p class="warning">
            Warning: moving a change will not change its parents.
          </p>
          <label for="branchInput"> Move change to branch </label>
          <gr-autocomplete id="branchInput" placeholder="Destination branch">
          </gr-autocomplete>
          <label for="messageInput"> Move Change Message </label>
          <iron-autogrow-textarea
            aria-disabled="false"
            id="messageInput"
            class="message"
            autocomplete="on"
          ></iron-autogrow-textarea>
        </div>
      </gr-dialog>
    `);
  });

  test('with updated commit message', async () => {
    element.branch = 'master' as BranchName;
    const myNewMessage = 'updated commit message';
    element.message = myNewMessage;
    await element.updateComplete;

    assert.equal(element.message, myNewMessage);
  });

  test('suggestions empty', async () => {
    const autoComplete = queryAndAssert<GrAutocomplete>(
      element,
      'gr-autocomplete'
    );
    const branches = await autoComplete.query!('nonexistent');
    assert.equal(branches.length, 0);
  });

  test('suggestions non-empty', async () => {
    const autoComplete = queryAndAssert<GrAutocomplete>(
      element,
      'gr-autocomplete'
    );
    const branches = await autoComplete.query!('test-branch');
    assert.equal(branches.length, 1);
    assert.equal(branches[0].name, 'test-branch');
  });

  test('suggestions input empty string', async () => {
    const autoComplete = queryAndAssert<GrAutocomplete>(
      element,
      'gr-autocomplete'
    );
    const branches = await autoComplete.query!('');
    assert.equal(branches.length, 0);
  });
});
