/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-revision-parents';
import {assert, fixture, html} from '@open-wc/testing';
import {GrRevisionParents} from './gr-revision-parents';
import {createRevision} from '../../../test/test-data-generators';
import {
  ChangeId,
  ChangeStatus,
  CommitId,
  NumericChangeId,
  ParentInfo,
  PatchSetNumber,
} from '../../../api/rest-api';
import {queryAll} from '../../../utils/common-util';

const PARENT_DEFAULT: ParentInfo = {
  branch_name: 'refs/heads/master',
  commit_id: '78e52ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
  is_merged_in_target_branch: true,
};

const PARENT_REBASED: ParentInfo = {
  ...PARENT_DEFAULT,
  commit_id: '00002ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
};

const PARENT_OTHER_BRANCH: ParentInfo = {
  ...PARENT_DEFAULT,
  branch_name: 'refs/heads/otherbranch',
  commit_id: '11112ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
};

const PARENT_WEIRD: ParentInfo = {
  ...PARENT_DEFAULT,
  commit_id: '22222ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
  is_merged_in_target_branch: false,
};

const PARENT_CHANGE_123_1: ParentInfo = {
  ...PARENT_DEFAULT,
  commit_id: '12312ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
  is_merged_in_target_branch: false,
  change_id: 'Idc69e6d7bba0ce0a9a0bdcd22adb506c0b76e628' as ChangeId,
  change_number: 123 as NumericChangeId,
  patch_set_number: 1 as PatchSetNumber,
  change_status: ChangeStatus.NEW,
};

const PARENT_CHANGE_123_2: ParentInfo = {
  ...PARENT_CHANGE_123_1,
  commit_id: '12322ce873b1c08396422f51ad6aacf77ed95541' as CommitId,
  patch_set_number: 2 as PatchSetNumber,
};

suite('gr-revision-parents tests', () => {
  let element: GrRevisionParents;

  const setParents = async (
    parentLeft: ParentInfo,
    parentRight: ParentInfo
  ) => {
    element.baseRevision = {
      ...createRevision(1),
      parents_data: [parentLeft],
    };
    element.revision = {
      ...createRevision(2),
      parents_data: [parentRight],
    };
    await element.updateComplete;
  };

  setup(async () => {
    element = await fixture(html`<gr-revision-parents></gr-revision-parents>`);
    await element.updateComplete;
  });

  test('render empty', () => {
    assert.shadowDom.equal(element, '');
  });

  test('render details: PARENT_DEFAULT', async () => {
    element.showDetails = true;
    await setParents(PARENT_DEFAULT, PARENT_DEFAULT);
    assert.dom.equal(
      queryAll(element, '.section')[0],
      /* HTML */ `
        <div class="section">
          <h4 class="heading-4">Patchset 1</h4>
          <div>Target branch: master</div>
          <div>
            Base commit:
            <gr-commit-info> </gr-commit-info>
          </div>
        </div>
      `
    );
  });

  test('render details: PARENT_WEIRD', async () => {
    element.showDetails = true;
    await setParents(PARENT_WEIRD, PARENT_WEIRD);
    assert.dom.equal(
      queryAll(element, '.section')[0],
      `
        <div class="section">
          <h4 class="heading-4">Patchset 1</h4>
          <div>Target branch: master</div>
          <div>
            Base commit:
            <gr-commit-info> </gr-commit-info>
          </div>
          <div>
            <gr-icon icon="warning"> </gr-icon>
            <span>
              Warning: The base commit is not known (aka reachable) in the
                target branch.
            </span>
          </div>
        </div>
      `
    );
  });

  test('render details: PARENT_CHANGE_123_1', async () => {
    element.showDetails = true;
    await setParents(PARENT_CHANGE_123_1, PARENT_CHANGE_123_2);
    assert.dom.equal(
      queryAll(element, '.section')[0],
      /* HTML */ `
        <div class="section">
          <h4 class="heading-4">Patchset 1</h4>
          <div>Target branch: master</div>
          <div>
            Base commit:
            <gr-commit-info> </gr-commit-info>
          </div>
          <div>
            Base change:
            <a href="/c/123"> 123 </a>
            , patchset
            <a href="/c/123/1"> 1 </a>
          </div>
        </div>
      `
    );
  });

  test('render message PARENT_DEFAULT vs PARENT_DEFAULT', async () => {
    await setParents(PARENT_DEFAULT, PARENT_DEFAULT);
    assert.shadowDom.equal(element, '');
  });

  test('render message PARENT_DEFAULT vs PARENT_OTHER_BRANCH', async () => {
    await setParents(PARENT_DEFAULT, PARENT_OTHER_BRANCH);
    assert.shadowDom.equal(
      element,
      `<div class="messageContainer warning">
       <div class="icon"><gr-icon icon="warning"></gr-icon></div>
       <div class="text"><p>
       Patchset 1 and 2 are targeting different branches.<br/>
       The diff below may not be meaningful and may<br/>
       even be hiding relevant changes.
       <a href="/Documentation/user-review-ui.html#hazardous-rebases">Learn more</a>
       </p><p><gr-button aria-disabled="false" link="" role="button" tabindex="0">Show details</gr-button></p></div></div>`
    );
  });

  test('render message PARENT_DEFAULT vs PARENT_WEIRD', async () => {
    await setParents(PARENT_DEFAULT, PARENT_WEIRD);
    assert.shadowDom.equal(
      element,
      `<div class="messageContainer warning">
       <div class="icon"><gr-icon icon="warning"></gr-icon></div>
       <div class="text"><p>
       Patchset 2 is based on a commit that neither exists in its
            target branch, nor is it a commit of another active change.<br/>
            The diff below may not be meaningful and may<br/>
            even be hiding relevant changes.
            <a href="/Documentation/user-review-ui.html#hazardous-rebases">Learn more</a>
            </p><p><gr-button aria-disabled="false" link="" role="button" tabindex="0">Show details</gr-button></p></div></div>`
    );
  });

  test('render message PARENT_DEFAULT vs PARENT_REBASED', async () => {
    await setParents(PARENT_DEFAULT, PARENT_REBASED);
    assert.shadowDom.equal(
      element,
      `<div class="messageContainer info">
       <div class="icon"><gr-icon icon="info"></gr-icon></div>
       <div class="text"><p>
       The change was rebased from <gr-commit-info></gr-commit-info>
       onto <gr-commit-info></gr-commit-info>.
       </p></div></div>`
    );
  });

  test('render message PARENT_CHANGE_123_1 vs PARENT_CHANGE_123_2', async () => {
    await setParents(PARENT_CHANGE_123_1, PARENT_CHANGE_123_2);
    assert.shadowDom.equal(
      element,
      `<div class="messageContainer info">
       <div class="icon"><gr-icon icon="info"></gr-icon></div>
       <div class="text"><p>
       The change was rebased from patchset
       <a href="/c/123/1">1</a> onto
            patchset
       <a href="/c/123/2">2</a> of
            change
       <a href="/c/123">123</a>.
       </p></div></div>`
    );
  });

  test('render message PARENT_DEFAULT vs PARENT_CHANGE_123_1', async () => {
    await setParents(PARENT_DEFAULT, PARENT_CHANGE_123_1);
    assert.shadowDom.equal(
      element,
      `<div class="messageContainer warning">
       <div class="icon"><gr-icon icon="warning"></gr-icon></div>
       <div class="text"><p>
       Patchset 1 is based on commit
       <gr-commit-info></gr-commit-info>
       in the target branch
        (master).<br>
       Patchset 2 is based on patchset
       <a href="/c/123/1">1</a>
       of change
       <a href="/c/123">123</a>.<br>
       The diff below may not be meaningful and may<br/>
       even be hiding relevant changes.
       <a href="/Documentation/user-review-ui.html#hazardous-rebases">Learn more</a>
       </p><p><gr-button aria-disabled="false" link="" role="button" tabindex="0">Show details</gr-button></p></div></div>`
    );
  });
});
