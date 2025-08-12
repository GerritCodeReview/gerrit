/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-included-in-dialog';
import {GrIncludedInDialog} from './gr-included-in-dialog';
import {BranchName, IncludedInInfo, TagName} from '../../../types/common';
import {queryAndAssert} from '../../../test/test-utils';
import {assert, fixture, html} from '@open-wc/testing';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';

suite('gr-included-in-dialog', () => {
  let element: GrIncludedInDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-included-in-dialog></gr-included-in-dialog>`
    );
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <header>
          <h1 class="heading-1" id="title">Included In:</h1>
          <span class="closeButtonContainer">
            <gr-button
              aria-disabled="false"
              id="closeButton"
              link=""
              role="button"
              tabindex="0"
            >
              Close
            </gr-button>
          </span>
          <md-outlined-text-field id="filterInput" placeholder="Filter">
          </md-outlined-text-field>
        </header>
        <div>Loading...</div>
      `
    );
  });

  test('computeGroups', () => {
    element.includedIn = {branches: [], tags: []} as IncludedInInfo;
    element.filterText = '';
    assert.deepEqual(element.computeGroups(), []);

    element.includedIn.branches.push(
      'master' as BranchName,
      'development' as BranchName,
      'stable-2.0' as BranchName
    );
    element.includedIn.tags.push(
      'v1.9' as TagName,
      'v2.0' as TagName,
      'v2.1' as TagName
    );
    assert.deepEqual(element.computeGroups(), [
      {title: 'Branches', items: ['master', 'development', 'stable-2.0']},
      {title: 'Tags', items: ['v1.9', 'v2.0', 'v2.1']},
    ]);

    element.includedIn.external = {};
    assert.deepEqual(element.computeGroups(), [
      {title: 'Branches', items: ['master', 'development', 'stable-2.0']},
      {title: 'Tags', items: ['v1.9', 'v2.0', 'v2.1']},
    ]);

    element.includedIn.external.foo = ['abc', 'def', 'ghi'];
    assert.deepEqual(element.computeGroups(), [
      {title: 'Branches', items: ['master', 'development', 'stable-2.0']},
      {title: 'Tags', items: ['v1.9', 'v2.0', 'v2.1']},
      {title: 'foo', items: ['abc', 'def', 'ghi']},
    ]);

    element.filterText = 'v2';
    assert.deepEqual(element.computeGroups(), [
      {title: 'Tags', items: ['v2.0', 'v2.1']},
    ]);

    // Filtering is case-insensitive.
    element.filterText = 'V2';
    assert.deepEqual(element.computeGroups(), [
      {title: 'Tags', items: ['v2.0', 'v2.1']},
    ]);
  });

  test('computeGroups with .bindValue', async () => {
    const filterInput = queryAndAssert<MdOutlinedTextField>(
      element,
      '#filterInput'
    );
    filterInput.value = 'stable-3.2';
    filterInput.dispatchEvent(
      new Event('input', {bubbles: true, composed: true})
    );
    element.includedIn = {branches: [], tags: []} as IncludedInInfo;
    element.includedIn.branches.push(
      'master' as BranchName,
      'stable-3.2' as BranchName
    );
    await element.updateComplete;
    assert.deepEqual(element.computeGroups(), [
      {title: 'Branches', items: ['stable-3.2']},
    ]);
  });
});
