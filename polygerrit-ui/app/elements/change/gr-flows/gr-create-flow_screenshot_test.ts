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
import {FlowActionInfo} from '../../../api/rest-api';
import {GrButton} from '../../shared/gr-button/gr-button';
import {queryAndAssert} from '../../../utils/common-util';
import {flowsModelToken} from '../../../models/flows/flows-model';
import {resolve} from '../../../models/dependency';

suite('gr-create-flow screenshot tests', () => {
  let element: GrCreateFlow;

  setup(async () => {
    element = await fixture<GrCreateFlow>(html`<gr-create-flow></gr-create-flow>`);
    element.changeNum = 123 as any;
    element.hostUrl = 'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321';
    
    // Stub flows model fetching actions if necessary, or just set it
    element['flowActions'] = [
      {name: 'review'},
      {name: 'submit'},
      {name: 'abandon'},
    ] as FlowActionInfo[];
    
    element['stages'] = [
      {
        condition: 'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is status:open',
        action: 'review',
        parameterStr: 'Code-Review+2',
      }
    ];
    
    element['currentCondition'] = 'status:merged';
    element['currentAction'] = 'submit';
    element['currentParameter'] = '';
    
    await element.updateComplete;

    // Open dialog
    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    await element.updateComplete;
  });

  test('dialog screenshot', async () => {
    // Take screenshot of the dialog itself, it has `#createModal`.
    await visualDiff(document.body, 'gr-create-flow-dialog');
    await visualDiffDarkTheme(document.body, 'gr-create-flow-dialog');
  });
});
