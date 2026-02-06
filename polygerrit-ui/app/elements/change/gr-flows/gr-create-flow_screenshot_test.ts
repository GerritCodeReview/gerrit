/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-flow';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrCreateFlow} from './gr-create-flow';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {NumericChangeId} from '../../../api/rest-api';
import {GrButton} from '../../shared/gr-button/gr-button';
import {queryAndAssert} from '../../../utils/common-util';

suite('gr-create-flow screenshot tests', () => {
  let element: GrCreateFlow;

  setup(async () => {
    element = await fixture<GrCreateFlow>(
      html`<gr-create-flow></gr-create-flow>`
    );
    element.changeNum = 123 as NumericChangeId;
    element.hostUrl =
      'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321';

    // Stub flows model fetching actions if necessary, or just set it
    element.flowActions = [
      {name: 'review'},
      {name: 'submit'},
      {name: 'abandon'},
    ];

    element.stages = [
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is status:open',
        action: 'review',
        parameterStr: 'Code-Review+2',
      },
    ];

    element.currentCondition = 'status:merged';
    element.currentAction = 'submit';
    element.currentParameter = '';

    await element.updateComplete;

    // Open dialog
    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    createButton.hidden = true;
    await element.updateComplete;
  });

  test('dialog screenshot', async () => {
    // Target the dialog element directly to avoid capturing empty space or the 'Create Flow' button.
    const dialog = queryAndAssert(element, '#createModal');

    await visualDiff(dialog, 'gr-create-flow-dialog');
    await visualDiffDarkTheme(dialog, 'gr-create-flow-dialog');
  });
});
