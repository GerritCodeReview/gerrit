/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html} from '@open-wc/testing-helpers';
import {
  BulkActionsModel,
  bulkActionsModelToken,
} from '../../../models/bulk-actions/bulk-actions-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import '../../../test/common-test-setup-karma';
import {createChange} from '../../../test/test-data-generators';
import {
  MockPromise,
  mockPromise,
  queryAll,
  queryAndAssert,
  stubRestApi,
  waitUntilObserved,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import './gr-change-list-submit-flow';
import type {GrChangeListSubmitFlow} from './gr-change-list-submit-flow';

const changes: ChangeInfo[] = [
  {
    ...createChange(),
    _number: 1 as NumericChangeId,
    subject: 'Submittable Change 1',
    actions: {submit: {}},
  },
  {
    ...createChange(),
    _number: 2 as NumericChangeId,
    subject: 'Submittable Change 2',
    actions: {submit: {}},
  },
  {
    ...createChange(),
    _number: 3 as NumericChangeId,
    subject: 'Unsubmittable Change 1',
    actions: {},
  },
];

suite('gr-change-list-submit-flow tests', () => {
  let element: GrChangeListSubmitFlow;
  let model: BulkActionsModel;

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChangeNums$, selectedChangeNums =>
      selectedChangeNums.includes(change._number)
    );
    await element.updateComplete;
  }

  setup(async () => {
    stubRestApi('getDetailedChangesWithActions').resolves(changes);
    model = new BulkActionsModel(getAppContext().restApiService);
    model.sync(changes);

    element = (
      await fixture(
        wrapInProvider(
          html`<gr-change-list-submit-flow></gr-change-list-submit-flow>`,
          bulkActionsModelToken,
          model
        )
      )
    ).querySelector('gr-change-list-submit-flow')!;
    await selectChange(changes[0]);
    await selectChange(changes[1]);
    await element.updateComplete;
  });

  test('renders flow', async () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-button flatten="" aria-disabled="false" role="button" tabindex="0"
        >submit</gr-button
      >
      <gr-overlay
        with-backdrop=""
        tabindex="-1"
        aria-hidden="true"
        style="outline: none; display: none;"
      >
        <gr-dialog role="dialog">
          <div slot="header">Submit 2 changes</div>
          <div slot="main">
            <table>
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Submittable Change 1</td>
                  <td>NOT STARTED</td>
                </tr>
                <tr>
                  <td>Submittable Change 2</td>
                  <td>NOT STARTED</td>
                </tr>
              </tbody>
            </table>
          </div>
        </gr-dialog>
      </gr-overlay>
    `);
  });

  test('flow button enabled only when submittable changes are selected', async () => {
    const button = queryAndAssert<GrButton>(element, 'gr-button');
    assert.isFalse(button.disabled);

    // select an un-submittable change
    model.addSelectedChangeNum(changes[2]._number);
    await waitUntilObserved(model.selectedChangeNums$, s => s.length === 3);
    await element.updateComplete;

    assert.isTrue(button.disabled);
  });

  test('flow button disabled when no changes selected', async () => {
    model.clearSelectedChangeNums();
    await waitUntilObserved(model.selectedChangeNums$, s => s.length === 0);
    await element.updateComplete;

    const button = queryAndAssert<GrButton>(element, 'gr-button');
    assert.isTrue(button.disabled);
  });

  test('overlay hidden before flow button clicked', async () => {
    const overlay = queryAndAssert<GrOverlay>(element, 'gr-overlay');
    assert.isFalse(overlay.opened);
  });

  test('flow button click shows overlay', async () => {
    const button = queryAndAssert<GrButton>(element, 'gr-button');

    button.click();
    await element.updateComplete;

    const overlay = queryAndAssert<GrOverlay>(element, 'gr-overlay');
    assert.isTrue(overlay.opened);
  });

  suite('dialog', () => {
    let submitPromises: MockPromise<Response>[];
    let changeActionStub: sinon.SinonStub;
    let dialog: GrDialog;

    async function resolvePromises() {
      submitPromises[0].resolve(new Response());
      submitPromises[1].reject('not allowed');
      await element.updateComplete;
    }

    function getStatusLabels() {
      return Array.from(
        queryAll<HTMLTableCellElement>(element, 'tbody td:last-child')
      ).map(td => td.innerText);
    }

    setup(async () => {
      submitPromises = [];
      changeActionStub = stubRestApi('executeChangeAction');
      for (let i = 0; i < changes.length; i++) {
        const promise = mockPromise<Response>();
        submitPromises.push(promise);
        changeActionStub
          .withArgs(changes[i]._number, sinon.match.any, sinon.match.any)
          .returns(promise);
      }

      queryAndAssert<GrButton>(element, 'gr-button').click();
      await element.updateComplete;
      dialog = queryAndAssert<GrDialog>(element, 'gr-dialog');
      await dialog.updateComplete;
    });

    test('confirm button text updates', async () => {
      assert.equal(dialog.confirmLabel, 'Apply');

      dialog.confirmButton!.click();
      await element.updateComplete;

      assert.equal(dialog.confirmLabel, 'Running');

      await resolvePromises();
      await element.updateComplete;

      assert.equal(dialog.confirmLabel, 'Close');
    });

    test('status column updates', async () => {
      assert.sameOrderedMembers(getStatusLabels(), [
        'NOT STARTED',
        'NOT STARTED',
      ]);

      dialog.confirmButton!.click();
      await element.updateComplete;

      assert.sameOrderedMembers(getStatusLabels(), ['RUNNING', 'RUNNING']);

      await resolvePromises();
      await element.updateComplete;

      assert.sameOrderedMembers(getStatusLabels(), ['SUCCESSFUL', 'FAILED']);
    });
  });
});
