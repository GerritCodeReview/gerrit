/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {assert, fixture, html} from '@open-wc/testing';
import {IronDropdownElement} from '@polymer/iron-dropdown';
import {SinonStubbedMember} from 'sinon';
import {
  BulkActionsModel,
  bulkActionsModelToken,
} from '../../../models/bulk-actions/bulk-actions-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import '../../../test/common-test-setup';
import {createChange} from '../../../test/test-data-generators';
import {
  MockPromise,
  mockPromise,
  query,
  queryAll,
  queryAndAssert,
  stubReporting,
  stubRestApi,
  waitEventLoop,
  waitUntil,
  waitUntilCalled,
  waitUntilObserved,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId, TopicName} from '../../../types/common';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrButton} from '../../shared/gr-button/gr-button';
import './gr-change-list-topic-flow';
import {GrChangeListTopicFlow} from './gr-change-list-topic-flow';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

suite('gr-change-list-topic-flow tests', () => {
  let element: GrChangeListTopicFlow;
  let model: BulkActionsModel;
  let reportingStub: SinonStubbedMember<ReportingService['reportInteraction']>;

  setup(() => {
    reportingStub = stubReporting('reportInteraction');
  });

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChanges$, selected =>
      selected.some(other => other._number === change._number)
    );
    await element.updateComplete;
  }

  async function deselectChange(change: ChangeInfo) {
    model.removeSelectedChangeNum(change._number);
    await waitUntilObserved(
      model.selectedChanges$,
      selected => !selected.some(other => other._number === change._number)
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
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-button
            id="start-flow"
            flatten=""
            down-arrow=""
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
        `
      );
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

    async function rejectPromises() {
      setChangeTopicPromises[0].reject(new Error('error'));
      setChangeTopicPromises[1].reject(new Error('error'));
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
          .withArgs(
            changesWithTopics[i]._number,
            sinon.match.any,
            sinon.match.any
          )
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
      await waitEventLoop();
    });

    test('renders existing-topics flow', () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-button
            id="start-flow"
            flatten=""
            down-arrow=""
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
                <button
                  role="listbox"
                  aria-label="topic1 selection"
                  class="chip"
                >
                  topic1
                </button>
                <button
                  role="listbox"
                  aria-label="topic2 selection"
                  class="chip"
                >
                  topic2
                </button>
              </div>
              <div class="footer">
                <div class="loadingOrError" role="progressbar"></div>
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

    test('apply all button is disabled if all changes have the same topic', async () => {
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-to-all-button').disabled
      );

      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      await element.updateComplete;

      assert.isFalse(
        queryAndAssert<GrButton>(element, '#apply-to-all-button').disabled
      );

      await deselectChange(changesWithTopics[1]);

      const allChanges = model.getState().allChanges;
      const change2 = {
        ...createChange(),
        _number: 2 as NumericChangeId,
        subject: 'Subject 2',
        topic: 'topic1' as TopicName, // same as changesWithTopics[0]
      };
      allChanges.set(2 as NumericChangeId, change2);
      model.setState({
        ...model.getState(),
        allChanges,
      });

      await selectChange(change2);

      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-to-all-button').disabled
      );
    });

    test('remove single topic', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      await element.updateComplete;
      queryAndAssert<GrButton>(element, '#remove-topics-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Removing topic...'
      );

      await resolvePromises();
      await element.updateComplete;

      // not called for second change which has a different topic
      assert.isTrue(setChangeTopicStub.calledOnce);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changesWithTopics[0]._number,
        '',
        throwingErrorCallback,
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: 'topic1 removed from changes',
        showDismiss: true,
      });
      assert.deepEqual(reportingStub.lastCall.args[1], {
        type: 'removing-topic',
        selectedChangeCount: 2,
      });
    });

    test('remove multiple topics', async () => {
      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      queryAll<HTMLButtonElement>(element, 'button.chip')[1].click();
      await element.updateComplete;
      queryAndAssert<GrButton>(element, '#remove-topics-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Removing topics...'
      );

      await resolvePromises();
      await element.updateComplete;

      // also called for second change which has a different topic
      assert.isTrue(setChangeTopicStub.calledTwice);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changesWithTopics[0]._number,
        '',
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changesWithTopics[1]._number,
        '',
        throwingErrorCallback,
      ]);
    });

    test('shows error when remove topic fails', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      await element.updateComplete;
      queryAndAssert<GrButton>(element, '#remove-topics-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Removing topic...'
      );

      // Rest api doesn't reject on error by default, but it does in topic flow,
      // because we specify a throwing callback.
      await rejectPromises();
      await element.updateComplete;

      await waitUntil(() => query(element, '.error') !== undefined);
      assert.equal(
        queryAndAssert(element, '.error').textContent,
        'Failed to remove topic'
      );
      assert.equal(
        queryAndAssert(element, 'gr-button#cancel-button').textContent,
        'Cancel'
      );
      assert.isUndefined(query(element, '.loadingText'));
    });

    test('can only apply a single topic', async () => {
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-to-all-button').disabled
      );

      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      await element.updateComplete;

      assert.isFalse(
        queryAndAssert<GrButton>(element, '#apply-to-all-button').disabled
      );

      queryAll<HTMLButtonElement>(element, 'button.chip')[1].click();
      await element.updateComplete;

      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-to-all-button').disabled
      );
    });

    test('applies topic to all changes', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
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
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changesWithTopics[1]._number,
        'topic1',
        throwingErrorCallback,
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: 'topic1 applied to all changes',
        showDismiss: true,
      });
      assert.deepEqual(reportingStub.lastCall.args[1], {
        type: 'apply-topic-to-all',
        selectedChangeCount: 2,
      });
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

    async function rejectPromises() {
      setChangeTopicPromises[0].reject(new Error('error'));
      setChangeTopicPromises[1].reject(new Error('error'));
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
          .withArgs(
            changesWithNoTopics[i]._number,
            sinon.match.any,
            sinon.match.any
          )
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
      await waitEventLoop();
    });

    test('renders no-existing-topics flow', () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-button
            id="start-flow"
            flatten=""
            down-arrow=""
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
                <div class="loadingOrError" role="progressbar"></div>
                <div class="buttons">
                  <gr-button
                    id="set-topic-button"
                    flatten=""
                    aria-disabled="true"
                    disabled=""
                    role="button"
                    tabindex="-1"
                    >Set Topic</gr-button
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
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      const getTopicsStub = stubRestApi('getChangesWithSimilarTopic').resolves(
        []
      );
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.debounceWait = 10;
      autocomplete.setFocus(true);
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getTopicsStub, 'getTopicsStub');
      assert.isFalse(
        queryAndAssert<GrButton>(element, '#set-topic-button').disabled
      );

      queryAndAssert<GrButton>(element, '#set-topic-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Setting topic...'
      );

      await resolvePromises();
      await element.updateComplete;

      assert.isTrue(setChangeTopicStub.calledTwice);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changesWithNoTopics[0]._number,
        'foo',
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changesWithNoTopics[1]._number,
        'foo',
        throwingErrorCallback,
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: '2 Changes added to foo',
        showDismiss: true,
      });
      assert.deepEqual(reportingStub.lastCall.args[1], {
        type: 'add-topic',
        selectedChangeCount: 2,
      });
    });

    test('shows error when create topic fails', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      const getTopicsStub = stubRestApi('getChangesWithSimilarTopic').resolves(
        []
      );
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.debounceWait = 10;
      autocomplete.setFocus(true);
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getTopicsStub, 'getTopicsStub');
      assert.isFalse(
        queryAndAssert<GrButton>(element, '#set-topic-button').disabled
      );
      queryAndAssert<GrButton>(element, '#set-topic-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Setting topic...'
      );

      // Rest api doesn't reject on error by default, but it does in topic flow,
      // because we specify a throwing callback.
      await rejectPromises();
      await element.updateComplete;
      await waitUntil(() => query(element, '.error') !== undefined);

      assert.equal(
        queryAndAssert(element, '.error').textContent,
        'Failed to set topic'
      );
      assert.equal(
        queryAndAssert(element, 'gr-button#cancel-button').textContent,
        'Cancel'
      );
      assert.isUndefined(query(element, '.loadingText'));
    });

    test('apply topic', async () => {
      const getTopicsStub = stubRestApi('getChangesWithSimilarTopic').resolves([
        {...createChange(), topic: 'foo' as TopicName},
      ]);
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.debounceWait = 10;
      autocomplete.setFocus(true);
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getTopicsStub, 'getTopicsStub');

      assert.isFalse(
        queryAndAssert<GrButton>(element, '#set-topic-button').disabled
      );
      queryAndAssert<GrButton>(element, '#set-topic-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Setting topic...'
      );

      await resolvePromises();

      assert.isTrue(setChangeTopicStub.calledTwice);
      assert.deepEqual(setChangeTopicStub.firstCall.args, [
        changesWithNoTopics[0]._number,
        'foo',
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeTopicStub.secondCall.args, [
        changesWithNoTopics[1]._number,
        'foo',
        throwingErrorCallback,
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: '2 Changes added to foo',
        showDismiss: true,
      });
      assert.deepEqual(reportingStub.lastCall.args[1], {
        type: 'add-topic',
        selectedChangeCount: 2,
      });
    });
  });
});
