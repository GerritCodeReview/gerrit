/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-confirm-submit-dialog';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-expect-error
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrConfirmSubmitDialog} from './gr-confirm-submit-dialog';
import {
  createChange,
  createParsedChange,
  createSubmittedTogetherInfo,
} from '../../../test/test-data-generators';
import {NumericChangeId} from '../../../types/common';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {setViewport} from '@web/test-runner-commands';

suite('gr-confirm-submit-dialog screenshot tests', () => {
  let element: GrConfirmSubmitDialog;
  let container: HTMLDivElement;

  setup(async () => {
    container = await fixture<HTMLDivElement>(
      html`<div style="width: 640px; display: inline-block">
        <gr-confirm-submit-dialog></gr-confirm-submit-dialog>
      </div>`
    );
    element = container.querySelector('gr-confirm-submit-dialog')!;
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      _number: 2241714 as NumericChangeId,
      subject: 'Child CL: Implement feature XYZ',
    };
    element.submittedTogether = {
      ...createSubmittedTogetherInfo(),
      changes: [
        {
          ...createChange(),
          _number: 2241713 as NumericChangeId,
          subject: 'Parent CL: Refactor base service',
          unresolved_comment_count: 2,
        },
        {
          ...createChange(),
          _number: 2241714 as NumericChangeId,
          subject: 'Child CL: Implement feature XYZ',
          unresolved_comment_count: 0,
        },
      ],
    };
    element.init();
    await element.updateComplete;
  });

  test('submit group with unresolved comments on parent change (light)', async () => {
    await setViewport({width: 1200, height: 800});
    await visualDiff(
      container,
      'gr-confirm-submit-dialog-submit-group-unresolved'
    );
  });

  test('submit group with unresolved comments on parent change (dark)', async () => {
    await setViewport({width: 1200, height: 800});
    await visualDiffDarkTheme(
      container,
      'gr-confirm-submit-dialog-submit-group-unresolved'
    );
  });
});
