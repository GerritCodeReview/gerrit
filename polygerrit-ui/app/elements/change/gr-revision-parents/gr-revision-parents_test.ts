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

  test('render', async () => {
    element.baseRevision = {
      ...createRevision(1),
      parents_data: [
        {
          branch_name: 'refs/heads/master',
          commit_id: '78e52ce873b1c08396422f51ad6aacf77ed95541',
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
          commit_id: '78e52ce873b1c08396422f51ad6aacf77ed95541',
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
      /* HTML*/ `
        <div class="container">
          <h3 class="heading-3">
            Parent Information
          </h3>
          <div class="section">
            <h4 class="heading-4">
              Left Revision
            </h4>
            <div>
              Branch: refs/heads/master
            </div>
            <div>
              Commit ID: 78e52ce873b1c08396422f51ad6aacf77ed95541
            </div>
            <div>
              Is Merged: false
            </div>
            <div>
              Change ID: Idc69e6d7bba0ce0a9a0bdcd22adb506c0b76e628
            </div>
            <div>
              Change Number: 1500
            </div>
            <div>
              Patchset Number: 1
            </div>
            <div>
              Change Status: NEW
            </div>
          </div>
          <div class="section">
            <h4 class="heading-4">
              Right Revision
            </h4>
            <div>
              Branch: refs/heads/master
            </div>
            <div>
              Commit ID: 78e52ce873b1c08396422f51ad6aacf77ed95541
            </div>
            <div>
              Is Merged: false
            </div>
            <div>
              Change ID: Idc69e6d7bba0ce0a9a0bdcd22adb506c0b76e628
            </div>
            <div>
              Change Number: 1500
            </div>
            <div>
              Patchset Number: 2
            </div>
            <div>
              Change Status: NEW
            </div>
          </div>
        </div>
    `
    );
  });
});
