/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html} from '@open-wc/testing-helpers';
import {IronDropdownElement} from '@polymer/iron-dropdown';
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
  queryAndAssert,
  stubRestApi,
  waitUntilCalled,
  waitUntilObserved,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId, TopicName} from '../../../types/common';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrButton} from '../../shared/gr-button/gr-button';
import './gr-change-list-topic-flow';
import type {GrChangeListTopicFlow} from './gr-change-list-topic-flow';

const changes: ChangeInfo[] = [
  {
    ...createChange(),
    _number: 1 as NumericChangeId,
    subject: 'Subject 1',
  },
  {
    ...createChange(),
    _number: 2 as NumericChangeId,
    subject: 'Subject 2',
  },
];

suite('gr-change-list-topic-flow tests', () => {
  let element: GrChangeListTopicFlow;
  let model: BulkActionsModel;

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChanges$, selected =>
      selected.some(other => other._number === change._number)
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
          html`<gr-change-list-topic-flow></gr-change-list-topic-flow>`,
          bulkActionsModelToken,
          model
        )
      )
    ).querySelector('gr-change-list-topic-flow')!;
    await selectChange(changes[0]);
    await selectChange(changes[1]);
    await waitUntilObserved(model.selectedChanges$, s => s.length === 2);
    await element.updateComplete;
  });

  test('skips dropdown render when closed', async () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-button
        id="start-flow"
        flatten=""
        aria-disabled="false"
        role="button"
        tabindex="0"
        >Topic</gr-button
      >
      <iron-dropdown
        aria-disabled="false"
        aria-hidden="true"
        style="outline: none; display: none;"
        vertical-align="auto"
        horizontal-align="auto"
      >
      </iron-dropdown>
    `);
  });

  test('dropdown hidden before flow button clicked', async () => {
    const dropdown = queryAndAssert<IronDropdownElement>(
      element,
      'iron-dropdown'
    );
    assert.isFalse(dropdown.opened);
  });

  test('flow button click shows dropdown', async () => {
    const button = queryAndAssert<GrButton>(element, 'gr-button#start-flow');

    button.click();
    await element.updateComplete;

    const dropdown = queryAndAssert<IronDropdownElement>(
      element,
      'iron-dropdown'
    );
    assert.isTrue(dropdown.opened);
  });

  suite('dropdown flow', () => {
    let setChangeTopicPromises: MockPromise<string>[];
    let setChangeTopicStub: sinon.SinonStub;
    // let dropdown: IronDropdownElement;

    async function resolvePromises() {
      setChangeTopicPromises[0].resolve('foo');
      setChangeTopicPromises[1].resolve('foo');
      await element.updateComplete;
    }

    setup(async () => {
      setChangeTopicPromises = [];
      setChangeTopicStub = stubRestApi('setChangeTopic');
      for (let i = 0; i < changes.length; i++) {
        const promise = mockPromise<string>();
        setChangeTopicPromises.push(promise);
        setChangeTopicStub
          .withArgs(changes[i]._number, sinon.match.any)
          .returns(promise);
      }

      queryAndAssert<GrButton>(element, 'gr-button#start-flow').click();
      await element.updateComplete;
      await flush();
    });

    test('renders dialog when opened', async () => {
      expect(element).shadowDom.to.equal(/* HTML */ `
        <gr-button
          id="start-flow"
          flatten=""
          aria-disabled="false"
          role="button"
          tabindex="0"
          >Topic</gr-button
        >
        <iron-dropdown
          aria-disabled="false"
          style="outline: none; position: fixed; box-sizing: border-box; left: 8px; top: 32px; max-width: 400px; max-height: 44px; z-index: 103;"
          vertical-align="auto"
          horizontal-align="auto"
        >
          <div slot="dropdown-content">
            <gr-autocomplete
              placeholder="Type topic name to create or filter topics"
            ></gr-autocomplete>
            <div class="buttons">
              <gr-button
                id="create-new-topic-button"
                flatten=""
                aria-disabled="true"
                disabled=""
                role="button"
                tabindex="-1"
                >Create new topic</gr-button
              >
              <gr-button
                id="apply-topic-button"
                flatten=""
                aria-disabled="false"
                role="button"
                tabindex="0"
                >Apply</gr-button
              >
            </div>
          </div>
        </iron-dropdown>
      `);
    });

    test('create new topic', async () => {
      stubRestApi('getChangesWithSimilarTopic').resolves([]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.text = 'foo';
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '#create-new-topic-button').click();
      await resolvePromises();
      await element.updateComplete;

      assert.isTrue(setChangeTopicStub.calledTwice);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changes[0]._number,
        'foo',
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changes[1]._number,
        'foo',
      ]);
    });

    test('apply topic', async () => {
      const getTopicsStub = stubRestApi('getChangesWithSimilarTopic').resolves([
        {...createChange(), topic: 'foo' as TopicName},
      ]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );

      autocomplete.focus();
      autocomplete.text = 'foo';
      await waitUntilCalled(getTopicsStub, 'getTopicsStub');

      queryAndAssert<GrButton>(element, '#apply-topic-button').click();
      await resolvePromises();

      assert.isTrue(setChangeTopicStub.calledTwice);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changes[0]._number,
        'foo',
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changes[1]._number,
        'foo',
      ]);
    });
  });
});
