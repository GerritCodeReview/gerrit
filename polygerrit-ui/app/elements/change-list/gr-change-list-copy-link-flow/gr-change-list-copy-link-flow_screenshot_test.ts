/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-list-copy-link-flow';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrChangeListCopyLinkFlow} from './gr-change-list-copy-link-flow';
import {visualDiffDarkTheme, waitUntil} from '../../../test/test-utils';
import {createChange} from '../../../test/test-data-generators';
import {
  BulkActionsModel,
  bulkActionsModelToken,
} from '../../../models/bulk-actions/bulk-actions-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import {waitUntilObserved} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {queryAndAssert} from '../../../utils/common-util';
import {GrCopyLinks} from '../../change/gr-copy-links/gr-copy-links';

const change1: ChangeInfo = {
  ...createChange(),
  _number: 1 as NumericChangeId,
  subject: 'Avoid O(P^2) patchset scan for ChangeKind in RevisionJson',
};
const change2: ChangeInfo = {
  ...createChange(),
  _number: 2 as NumericChangeId,
  subject: 'Defer submission index query in RevertSubmission#getDescription',
};
const change3: ChangeInfo = {
  ...createChange(),
  _number: 3 as NumericChangeId,
  subject: 'Reuse RevWalk in BaseCommitUtil and DiffOperationsImpl',
};
const change4: ChangeInfo = {
  ...createChange(),
  _number: 4 as NumericChangeId,
  subject: 'Honor SKIP_DIFFSTAT when formatting revision FileInfo maps',
};
const change5: ChangeInfo = {
  ...createChange(),
  _number: 5 as NumericChangeId,
  subject: 'Reuse PermissionBackend.ForChange across labels in LabelsJson',
};
const changes = [change1, change2, change3, change4, change5];

suite('gr-change-list-copy-link-flow screenshot tests', () => {
  let element: GrChangeListCopyLinkFlow;
  let model: BulkActionsModel;
  let container: HTMLElement;

  setup(async () => {
    model = new BulkActionsModel(getAppContext().restApiService);
    model.sync(changes);

    container = await fixture(html`
      <div
        style="width: 700px; padding: 200px 50px 400px 50px; display: flex; justify-content: flex-end;"
      >
        ${wrapInProvider(
          html`<gr-change-list-copy-link-flow></gr-change-list-copy-link-flow>`,
          bulkActionsModelToken,
          model
        )}
      </div>
    `);

    element = container.querySelector('gr-change-list-copy-link-flow')!;

    for (const change of changes) {
      model.addSelectedChangeNum(change._number);
    }
    await waitUntilObserved(model.selectedChanges$, s => s.length === 5);
    await element.updateComplete;

    const copyLinkButton = queryAndAssert<GrButton>(element, '#copyLinkButton');
    copyLinkButton.click();
    await element.updateComplete;
    const copyLinks = queryAndAssert<GrCopyLinks>(element, 'gr-copy-links');
    await waitUntil(() => copyLinks.isDropdownOpen);
    await element.updateComplete;
  });

  test('copy link dropdown open', async () => {
    await visualDiff(container, 'gr-change-list-copy-link-flow-open');
    await visualDiffDarkTheme(container, 'gr-change-list-copy-link-flow-open');
  });
});
