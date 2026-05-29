/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-rule-editor';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrRuleEditor} from './gr-rule-editor';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {EditablePermissionRuleInfo} from '../gr-repo-access/gr-repo-access-interfaces';
import {PermissionAction} from '../../../constants/constants';
import {AccessPermissionId} from '../../../utils/access-util';

suite('gr-rule-editor screenshot tests', () => {
  let element: GrRuleEditor;

  setup(async () => {
    const rule: EditablePermissionRuleInfo = {
      action: PermissionAction.ALLOW,
      force: false,
      min: -1,
      max: 1,
    };

    element = await fixture<GrRuleEditor>(
      html`<gr-rule-editor></gr-rule-editor>`
    );
    element.permission = 'label-Code-Review' as AccessPermissionId;
    element.rule = {value: rule};
    element.label = {
      values: [
        {value: -1, text: '-1 Fails'},
        {value: 0, text: ' 0 No score'},
        {value: 1, text: '+1 Passes'},
      ],
    };
    element.hasRange = true;
    element.editing = true;
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-rule-editor');
    await visualDiffDarkTheme(element, 'gr-rule-editor');
  });
});
