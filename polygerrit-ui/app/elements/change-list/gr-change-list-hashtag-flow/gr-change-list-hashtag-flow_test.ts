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
import {ChangeInfo, Hashtag, NumericChangeId} from '../../../types/common';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrButton} from '../../shared/gr-button/gr-button';
import './gr-change-list-hashtag-flow';
import {GrChangeListHashtagFlow} from './gr-change-list-hashtag-flow';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

suite('gr-change-list-hashtag-flow tests', () => {
  let element: GrChangeListHashtagFlow;
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
            html`<gr-change-list-hashtag-flow></gr-change-list-hashtag-flow>`,
            bulkActionsModelToken,
            model
          )
        )
      ).querySelector('gr-change-list-hashtag-flow')!;
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
            >Hashtag</gr-button
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

  suite('hashtag flow', () => {
    const changes: ChangeInfo[] = [
      {
        ...createChange(),
        _number: 1 as NumericChangeId,
        subject: 'Subject 1',
        hashtags: ['hashtag1' as Hashtag, 'sharedHashtag' as Hashtag],
      },
      {
        ...createChange(),
        _number: 2 as NumericChangeId,
        subject: 'Subject 2',
        hashtags: ['hashtag2' as Hashtag, 'sharedHashtag' as Hashtag],
      },
      {
        ...createChange(),
        _number: 3 as NumericChangeId,
        subject: 'Subject 3',
        hashtags: ['sharedHashtag' as Hashtag],
      },
    ];
    let setChangeHashtagPromises: MockPromise<Hashtag[]>[];
    let setChangeHashtagStub: sinon.SinonStub;

    async function resolvePromises(newHashtags: Hashtag[]) {
      setChangeHashtagPromises[0].resolve([
        ...(changes[0].hashtags ?? []),
        ...newHashtags,
      ]);
      setChangeHashtagPromises[1].resolve([
        ...(changes[1].hashtags ?? []),
        ...newHashtags,
      ]);
      setChangeHashtagPromises[2].resolve([
        ...(changes[2].hashtags ?? []),
        ...newHashtags,
      ]);
      await element.updateComplete;
    }

    async function rejectPromises() {
      setChangeHashtagPromises[0].reject(new Error('error'));
      setChangeHashtagPromises[1].reject(new Error('error'));
      setChangeHashtagPromises[2].reject(new Error('error'));
      await element.updateComplete;
    }

    setup(async () => {
      stubRestApi('getDetailedChangesWithActions').resolves(changes);
      setChangeHashtagPromises = [];
      setChangeHashtagStub = stubRestApi('setChangeHashtag');
      for (let i = 0; i < changes.length; i++) {
        const promise = mockPromise<Hashtag[]>();
        setChangeHashtagPromises.push(promise);
        setChangeHashtagStub
          .withArgs(changes[i]._number, sinon.match.any, sinon.match.any)
          .returns(promise);
      }
      model = new BulkActionsModel(getAppContext().restApiService);
      model.sync(changes);

      element = (
        await fixture(
          wrapInProvider(
            html`<gr-change-list-hashtag-flow></gr-change-list-hashtag-flow>`,
            bulkActionsModelToken,
            model
          )
        )
      ).querySelector('gr-change-list-hashtag-flow')!;

      // select changes
      await selectChange(changes[0]);
      await selectChange(changes[1]);
      await selectChange(changes[2]);
      await waitUntilObserved(model.selectedChanges$, s => s.length === 3);
      await element.updateComplete;

      // open flow
      queryAndAssert<GrButton>(element, 'gr-button#start-flow').click();
      await element.updateComplete;
      await waitEventLoop();
    });

    test('renders hashtags flow', () => {
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
            >Hashtag</gr-button
          >
          <iron-dropdown
            aria-disabled="false"
            focused=""
            vertical-align="auto"
            horizontal-align="auto"
          >
            <div slot="dropdown-content">
              <div class="chips">
                <button
                  role="listbox"
                  aria-label="hashtag1 selection"
                  class="chip"
                >
                  hashtag1
                </button>
                <button
                  role="listbox"
                  aria-label="sharedHashtag selection"
                  class="chip"
                >
                  sharedHashtag
                </button>
                <button
                  role="listbox"
                  aria-label="hashtag2 selection"
                  class="chip"
                >
                  hashtag2
                </button>
              </div>
              <gr-autocomplete
                placeholder="Type hashtag name to create or filter hashtags"
                show-blue-focus-border=""
              ></gr-autocomplete>
              <div class="footer">
                <div class="loadingOrError" role="progressbar"></div>
                <div class="buttons">
                  <gr-button
                    id="add-hashtag-button"
                    flatten=""
                    aria-disabled="true"
                    disabled=""
                    role="button"
                    tabindex="-1"
                    >Add Hashtag</gr-button
                  >
                </div>
              </div>
            </div>
          </iron-dropdown>
        `,
        {
          // iron-dropdown sizing seems to vary between local & CI
          ignoreAttributes: [
            {tags: ['iron-dropdown'], attributes: ['style', 'focused']},
          ],
        }
      );
    });

    test('add hashtag from selected change', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      // selects "hashtag1"
      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '#add-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Adding hashtag...'
      );

      await resolvePromises(['hashtag1' as Hashtag]);
      await element.updateComplete;

      assert.isTrue(setChangeHashtagStub.calledThrice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changes[0]._number,
        {add: ['hashtag1']},
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changes[1]._number,
        {add: ['hashtag1']},
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeHashtagStub.thirdCall.args, [
        changes[2]._number,
        {add: ['hashtag1']},
        throwingErrorCallback,
      ]);
      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: '3 Changes added to hashtag1',
        showDismiss: true,
      });
      assert.deepEqual(reportingStub.lastCall.args[1], {
        type: 'add-hashtag',
        selectedChangeCount: 3,
        hashtagsApplied: 1,
      });
      assert.isTrue(
        queryAndAssert<IronDropdownElement>(element, 'iron-dropdown').opened
      );
    });

    test('add multiple hashtag from selected change', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      // selects "hashtag1"
      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      await element.updateComplete;

      // selects "hashtag2"
      queryAll<HTMLButtonElement>(element, 'button.chip')[2].click();
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '#add-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Adding hashtag...'
      );

      await resolvePromises(['hashtag1' as Hashtag, 'hashtag2' as Hashtag]);
      await element.updateComplete;

      assert.isTrue(setChangeHashtagStub.calledThrice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changes[0]._number,
        {add: ['hashtag1', 'hashtag2']},
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changes[1]._number,
        {add: ['hashtag1', 'hashtag2']},
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeHashtagStub.thirdCall.args, [
        changes[2]._number,
        {add: ['hashtag1', 'hashtag2']},
        throwingErrorCallback,
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: '2 hashtags added to changes',
        showDismiss: true,
      });
      assert.deepEqual(reportingStub.lastCall.args[1], {
        type: 'add-hashtag',
        selectedChangeCount: 3,
        hashtagsApplied: 2,
      });
    });

    test('add existing hashtag not on selected changes', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      const getHashtagsStub = stubRestApi(
        'getChangesWithSimilarHashtag'
      ).resolves([{...createChange(), hashtags: ['foo' as Hashtag]}]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.debounceWait = 10;

      autocomplete.setFocus(true);
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getHashtagsStub, 'getHashtagsStub');

      queryAndAssert<GrButton>(element, '#add-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Adding hashtag...'
      );

      await resolvePromises(['foo' as Hashtag]);

      assert.isTrue(setChangeHashtagStub.calledThrice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changes[0]._number,
        {add: ['foo']},
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changes[1]._number,
        {add: ['foo']},
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeHashtagStub.thirdCall.args, [
        changes[2]._number,
        {add: ['foo']},
        throwingErrorCallback,
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: '3 Changes added to foo',
        showDismiss: true,
      });
      assert.deepEqual(reportingStub.lastCall.args[1], {
        type: 'add-hashtag',
        selectedChangeCount: 3,
        hashtagsApplied: 1,
      });
      assert.isTrue(
        queryAndAssert<IronDropdownElement>(element, 'iron-dropdown').opened
      );
    });

    test('add new hashtag', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      const getHashtagsStub = stubRestApi(
        'getChangesWithSimilarHashtag'
      ).resolves([]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.debounceWait = 10;
      autocomplete.setFocus(true);
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getHashtagsStub, 'getHashtagsStub');
      assert.isFalse(
        queryAndAssert<GrButton>(element, '#add-hashtag-button').disabled
      );

      queryAndAssert<GrButton>(element, '#add-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Adding hashtag...'
      );

      await resolvePromises(['foo' as Hashtag]);
      await waitUntilObserved(model.selectedChanges$, selected =>
        selected.every(change => change.hashtags?.includes('foo' as Hashtag))
      );
      await element.updateComplete;

      assert.isTrue(setChangeHashtagStub.calledThrice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changes[0]._number,
        {add: ['foo']},
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changes[1]._number,
        {add: ['foo']},
        throwingErrorCallback,
      ]);
      assert.deepEqual(setChangeHashtagStub.thirdCall.args, [
        changes[2]._number,
        {add: ['foo']},
        throwingErrorCallback,
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: '3 Changes added to foo',
        showDismiss: true,
      });
      assert.deepEqual(reportingStub.lastCall.args[1], {
        type: 'add-hashtag',
        selectedChangeCount: 3,
        hashtagsApplied: 1,
      });
      assert.isTrue(
        queryAndAssert<IronDropdownElement>(element, 'iron-dropdown').opened
      );
      assert.equal(
        queryAll<HTMLButtonElement>(element, 'button.chip')[2].innerText,
        'foo'
      );
    });

    test('shows error when add hashtag fails', async () => {
      const getHashtagsStub = stubRestApi(
        'getChangesWithSimilarHashtag'
      ).resolves([]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.debounceWait = 10;
      autocomplete.setFocus(true);
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getHashtagsStub, 'getHashtagsStub');
      assert.isFalse(
        queryAndAssert<GrButton>(element, '#add-hashtag-button').disabled
      );

      queryAndAssert<GrButton>(element, '#add-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Adding hashtag...'
      );

      // Rest api doesn't reject on error by default, but it does in topic flow,
      // because we specify a throwing callback.
      await rejectPromises();
      await element.updateComplete;
      await waitUntil(() => query(element, '.error') !== undefined);

      assert.equal(
        queryAndAssert(element, '.error').textContent,
        'Failed to add'
      );
      assert.equal(
        queryAndAssert(element, 'gr-button#cancel-button').textContent,
        'Cancel'
      );
      assert.isUndefined(query(element, '.loadingText'));
    });

    test('cannot add existing hashtag already on selected changes', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      // selects "sharedHashtag"
      queryAll<HTMLButtonElement>(element, 'button.chip')[1].click();
      await element.updateComplete;

      assert.isTrue(
        queryAndAssert<GrButton>(element, '#add-hashtag-button').disabled
      );
    });
  });
});
