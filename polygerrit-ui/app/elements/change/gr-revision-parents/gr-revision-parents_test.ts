/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-revision-parents';
import {fixture, html, assert} from '@open-wc/testing';
import {GrRevisionParents} from './gr-revision-parents';
import {createRevision} from '../../../test/test-data-generators';
import {
  ChangeId,
  ChangeStatus,
  CommitId,
  NumericChangeId,
  PatchSetNumber,
} from '../../../api/rest-api';

suite('gr-revision-parents tests', () => {
  let element: GrRevisionParents;

  setup(async () => {
    element = await fixture(html`<gr-revision-parents></gr-revision-parents>`);
    await element.updateComplete;
  });

  test('render empty', () => {
    assert.shadowDom.equal(element, '');
  });

  test('render details', async () => {
    element.showDetails = true;
    element.baseRevision = {
      ...createRevision(1),
      parents_data: [
        {
          branch_name: 'refs/heads/master',
          commit_id: '78e52ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
          is_merged_in_target_branch: false,
          change_id: 'Idc69e6d7bba0ce0a9a0bdcd22adb506c0b76e628' as ChangeId,
          change_number: 1500 as NumericChangeId,
          patch_set_number: 1 as PatchSetNumber,
          change_status: ChangeStatus.NEW,
        },
      ],
    };
    element.revision = {
      ...createRevision(2),
      parents_data: [
        {
          branch_name: 'refs/heads/master',
          commit_id: '78e52ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
          is_merged_in_target_branch: false,
          change_id: 'Idc69e6d7bba0ce0a9a0bdcd22adb506c0b76e628' as ChangeId,
          change_number: 1500 as NumericChangeId,
          patch_set_number: 2 as PatchSetNumber,
          change_status: ChangeStatus.NEW,
        },
      ],
    };
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container">
          <div class="flex">
            <div class="section">
              <h4 class="heading-4">Patchset 1</h4>
              <div>Target Branch: master</div>
              <div>
                Base Commit:
                <gr-commit-info> </gr-commit-info>
                <gr-commit-info> </gr-commit-info>
              </div>
              <div>
                <span> The base commit is a patchset of another change. </span>
              </div>
              <div>
                Change Number:
                <a href="/c/1500"> 1500 </a>
              </div>
              <div>
                Patchset Number:
                <a href="/c/1500/1"> 1 </a>
              </div>
            </div>
            <div class="section">
              <h4 class="heading-4">Patchset 2</h4>
              <div>Target Branch: master</div>
              <div>
                Base Commit:
                <gr-commit-info> </gr-commit-info>
                <gr-commit-info> </gr-commit-info>
              </div>
              <div>
                <span> The base commit is a patchset of another change. </span>
              </div>
              <div>
                Change Number:
                <a href="/c/1500"> 1500 </a>
              </div>
              <div>
                Patchset Number:
                <a href="/c/1500/2"> 2 </a>
              </div>
            </div>
          </div>
        </div>
      `
    );
  });

  test('render warning', async () => {
    element.baseRevision = {
      ...createRevision(1),
      parents_data: [
        {
          branch_name: 'refs/heads/master',
          commit_id: '78e52ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
          is_merged_in_target_branch: false,
          change_id: 'Idc69e6d7bba0ce0a9a0bdcd22adb506c0b76e628' as ChangeId,
          change_number: 1500 as NumericChangeId,
          patch_set_number: 1 as PatchSetNumber,
          change_status: ChangeStatus.NEW,
        },
      ],
    };
    element.revision = {
      ...createRevision(2),
      parents_data: [
        {
          branch_name: 'refs/heads/other',
          commit_id: '33352ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
          is_merged_in_target_branch: false,
          change_id: 'Idc69e6d7bba0ce0a9a0bdcd22adb506c0b76e628' as ChangeId,
          change_number: 1500 as NumericChangeId,
          patch_set_number: 2 as PatchSetNumber,
          change_status: ChangeStatus.NEW,
        },
      ],
    };
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      `
        <div class="messageContainer warning">
          <div class="icon">
            <gr-icon icon="warning"> </gr-icon>
          </div>
          <div class="text">
            <p>Patchset 1 and 2 are targeting different branches.</p>
            <p>
              The diff below may not be meaningful and may even be hiding relevant changes.
            </p>
            <p>
              <gr-button link=""> Show details </gr-button>
            </p>
          </div>
        </div>
      `
    );
  });
});
