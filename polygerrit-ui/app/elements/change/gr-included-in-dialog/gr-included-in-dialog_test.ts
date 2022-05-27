/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-included-in-dialog';
import {GrIncludedInDialog} from './gr-included-in-dialog';
import {BranchName, IncludedInInfo, TagName} from '../../../types/common';
import {IronInputElement} from '@polymer/iron-input';
import {queryAndAssert} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-included-in-dialog');

suite('gr-included-in-dialog', () => {
  let element: GrIncludedInDialog;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
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
    queryAndAssert<IronInputElement>(element, '#filterInput')!.bindValue =
      'stable-3.2';
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
