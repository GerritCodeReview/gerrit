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
  queryAll,
  queryAndAssert,
  stubRestApi,
  waitUntil,
  waitUntilCalled,
  waitUntilObserved,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId, TopicName} from '../../../types/common';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrButton} from '../../shared/gr-button/gr-button';
import './gr-change-list-topic-flow';
import type {GrChangeListTopicFlow} from './gr-change-list-topic-flow';

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

  suite('dropdown closed', () => {
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

    test('flow button click when open hides dropdown', async () => {
      queryAndAssert<GrButton>(element, 'gr-button#start-flow').click();
      await waitUntil(() =>
        Boolean(
          queryAndAssert<IronDropdownElement>(element, 'iron-dropdown').opened
        )
      );
      queryAndAssert<GrButton>(element, 'gr-button#start-flow').click();
      await waitUntil(
        () =>
          !queryAndAssert<IronDropdownElement>(element, 'iron-dropdown').opened
      );
    });
  });

  suite('changes in existing topics', () => {
    const changesWithTopics: ChangeInfo[] = [
      {
        ...createChange(),
        _number: 1 as NumericChangeId,
        subject: 'Subject 1',
        topic: 'topic1' as TopicName,
      },
      {
        ...createChange(),
        _number: 2 as NumericChangeId,
        subject: 'Subject 2',
        topic: 'topic2' as TopicName,
      },
    ];
    let setChangeTopicPromises: MockPromise<string>[];
    let setChangeTopicStub: sinon.SinonStub;

    async function resolvePromises() {
      setChangeTopicPromises[0].resolve('foo');
      setChangeTopicPromises[1].resolve('foo');
      await element.updateComplete;
    }

    setup(async () => {
      stubRestApi('getDetailedChangesWithActions').resolves(changesWithTopics);
      setChangeTopicPromises = [];
      setChangeTopicStub = stubRestApi('setChangeTopic');
      for (let i = 0; i < changesWithTopics.length; i++) {
        const promise = mockPromise<string>();
        setChangeTopicPromises.push(promise);
        setChangeTopicStub
          .withArgs(changesWithTopics[i]._number, sinon.match.any)
          .returns(promise);
      }
      model = new BulkActionsModel(getAppContext().restApiService);
      model.sync(changesWithTopics);

      element = (
        await fixture(
          wrapInProvider(
            html`<gr-change-list-topic-flow></gr-change-list-topic-flow>`,
            bulkActionsModelToken,
            model
          )
        )
      ).querySelector('gr-change-list-topic-flow')!;

      // select changes
      await selectChange(changesWithTopics[0]);
      await selectChange(changesWithTopics[1]);
      await waitUntilObserved(model.selectedChanges$, s => s.length === 2);
      await element.updateComplete;

      // open flow
      queryAndAssert<GrButton>(element, 'gr-button#start-flow').click();
      await element.updateComplete;
      await flush();
    });

    test('renders existing-topics flow', () => {
      expect(element).shadowDom.to.equal(
        /* HTML */ `
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
            vertical-align="auto"
            horizontal-align="auto"
          >
            <div slot="dropdown-content">
              <div class="chips">
                <span class="chip">topic1</span>
                <span class="chip">topic2</span>
              </div>
              <div class="footer">
                <div class="loadingOrError"></div>
                <div class="buttons">
                  <gr-button
                    id="apply-to-all-button"
                    flatten=""
                    aria-disabled="true"
                    disabled=""
                    role="button"
                    tabindex="-1"
                    >Apply to all</gr-button
                  >
                  <gr-button
                    id="remove-topics-button"
                    flatten=""
                    aria-disabled="true"
                    disabled=""
                    role="button"
                    tabindex="-1"
                    >Remove</gr-button
                  >
                </div>
              </div>
            </div>
          </iron-dropdown>
        `,
        {
          // iron-dropdown sizing seems to vary between local & CI
          ignoreAttributes: [{tags: ['iron-dropdown'], attributes: ['style']}],
        }
      );
    });

    test('remove single topic', async () => {
      queryAll<HTMLSpanElement>(element, 'span.chip')[0].click();
      await element.updateComplete;
      queryAndAssert<GrButton>(element, '#remove-topics-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Removing 1 topic...'
      );

      await resolvePromises();
      await element.updateComplete;

      // not called for second change which as a different topic
      assert.isTrue(setChangeTopicStub.calledOnce);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changesWithTopics[0]._number,
        '',
      ]);
    });

    test('remove multiple topics', async () => {
      queryAll<HTMLSpanElement>(element, 'span.chip')[0].click();
      queryAll<HTMLSpanElement>(element, 'span.chip')[1].click();
      await element.updateComplete;
      queryAndAssert<GrButton>(element, '#remove-topics-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Removing 2 topics...'
      );

      await resolvePromises();
      await element.updateComplete;

      // not called for second change which as a different topic
      assert.isTrue(setChangeTopicStub.calledTwice);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changesWithTopics[0]._number,
        '',
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changesWithTopics[1]._number,
        '',
      ]);
    });

    test('can only apply a single topic', async () => {
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-to-all-button').disabled
      );

      queryAll<HTMLSpanElement>(element, 'span.chip')[0].click();
      await element.updateComplete;

      assert.isFalse(
        queryAndAssert<GrButton>(element, '#apply-to-all-button').disabled
      );

      queryAll<HTMLSpanElement>(element, 'span.chip')[1].click();
      await element.updateComplete;

      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-to-all-button').disabled
      );
    });

    test('applies topic to all changes', async () => {
      queryAll<HTMLSpanElement>(element, 'span.chip')[0].click();
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '#apply-to-all-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Applying to all'
      );

      await resolvePromises();
      await element.updateComplete;

      assert.isTrue(setChangeTopicStub.calledTwice);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changesWithTopics[0]._number,
        'topic1',
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changesWithTopics[1]._number,
        'topic1',
      ]);
    });
  });

  suite('change have no existing topics', () => {
    const changesWithNoTopics: ChangeInfo[] = [
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
    let setChangeTopicPromises: MockPromise<string>[];
    let setChangeTopicStub: sinon.SinonStub;

    async function resolvePromises() {
      setChangeTopicPromises[0].resolve('foo');
      setChangeTopicPromises[1].resolve('foo');
      await element.updateComplete;
    }

    setup(async () => {
      stubRestApi('getDetailedChangesWithActions').resolves(
        changesWithNoTopics
      );
      setChangeTopicPromises = [];
      setChangeTopicStub = stubRestApi('setChangeTopic');
      for (let i = 0; i < changesWithNoTopics.length; i++) {
        const promise = mockPromise<string>();
        setChangeTopicPromises.push(promise);
        setChangeTopicStub
          .withArgs(changesWithNoTopics[i]._number, sinon.match.any)
          .returns(promise);
      }

      model = new BulkActionsModel(getAppContext().restApiService);
      model.sync(changesWithNoTopics);

      element = (
        await fixture(
          wrapInProvider(
            html`<gr-change-list-topic-flow></gr-change-list-topic-flow>`,
            bulkActionsModelToken,
            model
          )
        )
      ).querySelector('gr-change-list-topic-flow')!;

      // select changes
      await selectChange(changesWithNoTopics[0]);
      await selectChange(changesWithNoTopics[1]);
      await waitUntilObserved(model.selectedChanges$, s => s.length === 2);
      await element.updateComplete;

      // open flow
      queryAndAssert<GrButton>(element, 'gr-button#start-flow').click();
      await element.updateComplete;
      await flush();
    });

    test('renders no-existing-topics flow', () => {
      expect(element).shadowDom.to.equal(
        /* HTML */ `
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
            vertical-align="auto"
            horizontal-align="auto"
          >
            <div slot="dropdown-content">
              <gr-autocomplete
                placeholder="Type topic name to create or filter topics"
                show-blue-focus-border=""
              ></gr-autocomplete>
              <div class="footer">
                <div class="loadingOrError"></div>
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
                    aria-disabled="true"
                    disabled=""
                    role="button"
                    tabindex="-1"
                    >Apply</gr-button
                  >
                </div>
              </div>
            </div>
          </iron-dropdown>
        `,
        {
          // iron-dropdown sizing seems to vary between local & CI
          ignoreAttributes: [{tags: ['iron-dropdown'], attributes: ['style']}],
        }
      );
    });

    test('create new topic', async () => {
      const getTopicsStub = stubRestApi('getChangesWithSimilarTopic').resolves(
        []
      );
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.focus();
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getTopicsStub, 'getTopicsStub');
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-topic-button').disabled
      );

      queryAndAssert<GrButton>(element, '#create-new-topic-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Creating topic...'
      );

      await resolvePromises();
      await element.updateComplete;

      assert.isTrue(setChangeTopicStub.calledTwice);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changesWithNoTopics[0]._number,
        'foo',
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changesWithNoTopics[1]._number,
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
      await element.updateComplete;
      await waitUntilCalled(getTopicsStub, 'getTopicsStub');
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#create-new-topic-button').disabled
      );

      queryAndAssert<GrButton>(element, '#apply-topic-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Applying topic...'
      );

      await resolvePromises();

      assert.isTrue(setChangeTopicStub.calledTwice);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changesWithNoTopics[0]._number,
        'foo',
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changesWithNoTopics[1]._number,
        'foo',
      ]);
    });
  });
});
