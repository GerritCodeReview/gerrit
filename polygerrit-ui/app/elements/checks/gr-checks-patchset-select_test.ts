/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './gr-checks-patchset-select';
import {GrChecksPatchsetSelect} from './gr-checks-patchset-select';
import {html} from 'lit';
import {assert, fixture} from '@open-wc/testing';
import {checksModelToken} from '../../models/checks/checks-model';
import {setAllcheckRuns} from '../../test/test-data-generators';
import {resolve} from '../../models/dependency';
import {assertIsDefined, queryAndAssert} from '../../utils/common-util';
import {GrDropdownList} from '../shared/gr-dropdown-list/gr-dropdown-list';

suite('gr-checks-patchset-select test', () => {
  let element: GrChecksPatchsetSelect;

  setup(async () => {
    element = await fixture<GrChecksPatchsetSelect>(
      html`<gr-checks-patchset-select></gr-checks-patchset-select>`
    );
    setAllcheckRuns(resolve(element, checksModelToken)());
    await element.updateComplete;
  });

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-dropdown-list class="patchsetSelect" value="0"> </gr-dropdown-list>
        <gr-dropdown-list class="attemptSelect" value="latest">
        </gr-dropdown-list>
      `
    );
  });

  test('renders compact', async () => {
    element.compact = true;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-dropdown-list class="patchsetSelect" value="0"> </gr-dropdown-list>
      `
    );
  });

  test('attempt dropdown items', async () => {
    const attemptDropdown = queryAndAssert<GrDropdownList>(
      element,
      'gr-dropdown-list.attemptSelect'
    );
    assertIsDefined(attemptDropdown.items);
    assert.equal(attemptDropdown.items.length, 42);
    assert.deepEqual(attemptDropdown.items[0], {
      text: 'Latest Attempt',
      value: 'latest',
    });
    assert.deepEqual(attemptDropdown.items[1], {
      text: 'All Attempts',
      value: 'all',
    });
    assert.deepEqual(attemptDropdown.items[2], {
      text: 'Attempt 0',
      value: 0,
    });
    assert.deepEqual(attemptDropdown.items[41], {
      text: 'Attempt 40',
      value: 40,
    });
  });
});
