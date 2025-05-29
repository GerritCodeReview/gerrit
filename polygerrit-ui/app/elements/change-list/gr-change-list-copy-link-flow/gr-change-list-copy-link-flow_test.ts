/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import {
  BulkActionsModel,
  bulkActionsModelToken,
} from '../../../models/bulk-actions/bulk-actions-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import '../../../test/common-test-setup';
import {createChange} from '../../../test/test-data-generators';
import {waitUntilObserved} from '../../../test/test-utils';
import './gr-change-list-copy-link-flow';
import {GrChangeListCopyLinkFlow} from './gr-change-list-copy-link-flow';
import {ChangeInfo, NumericChangeId} from '../../../api/rest-api';
import {prependOrigin} from '../../../utils/url-util';

const change1: ChangeInfo = {...createChange(), _number: 1 as NumericChangeId};
const change2: ChangeInfo = {...createChange(), _number: 2 as NumericChangeId};

suite('gr-change-list-copy-link-flow tests', () => {
  let element: GrChangeListCopyLinkFlow;
  let model: BulkActionsModel;

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChangeNums$, selectedChangeNums =>
      selectedChangeNums.includes(change._number)
    );
    await element.updateComplete;
  }

  setup(async () => {
    model = new BulkActionsModel(getAppContext().restApiService);
    model.sync([change1, change2]);
    element = (
      await fixture(
        wrapInProvider(
          html`<gr-change-list-copy-link-flow></gr-change-list-copy-link-flow>`,
          bulkActionsModelToken,
          model
        )
      )
    ).querySelector('gr-change-list-copy-link-flow')!;
    await element.updateComplete;
  });

  test('renders nothing when no changes selected', async () => {
    assert.shadowDom.equal(element, '');
  });

  test('renders copy link button when changes are selected', async () => {
    await selectChange(change1);
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-button
          id="copyLinkButton"
          link=""
          role="button"
          tabindex="0"
          aria-disabled="false"
        >
          Copy Link
        </gr-button>
        <gr-copy-links></gr-copy-links>
      `
    );
  });

  test('copy links are generated correctly', async () => {
    await selectChange(change1);
    await selectChange(change2);
    assert.deepEqual(element.getCopyLinks(), [
      {
        label: 'Change Query URL',
        shortcut: 'u',
        value: prependOrigin('/q/change:1+OR+change:2'),
      },
      {
        label: 'Markdown',
        shortcut: 'm',
        value: `[Subject 1](${prependOrigin(
          '/c/1'
        )})\n[Subject 2](${prependOrigin('/c/2')})`,
        multiline: true,
      },
    ]);
  });

  test('clicking copy link button opens dropdown', async () => {
    await selectChange(change1);
    const copyLinks = element.shadowRoot?.querySelector('gr-copy-links');
    assert.exists(copyLinks);
    const button = element.shadowRoot?.querySelector('gr-button');
    assert.exists(button);
    button?.click();
    await element.updateComplete;
    assert.isTrue((copyLinks as any).isDropdownOpen);
  });
});
