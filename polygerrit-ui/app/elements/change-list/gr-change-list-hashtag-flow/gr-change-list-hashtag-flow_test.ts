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
  query,
  queryAll,
  queryAndAssert,
  stubRestApi,
  waitUntil,
  waitUntilCalled,
  waitUntilObserved,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId, Hashtag} from '../../../types/common';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrButton} from '../../shared/gr-button/gr-button';
import './gr-change-list-hashtag-flow';
import type {GrChangeListHashtagFlow} from './gr-change-list-hashtag-flow';

suite('gr-change-list-hashtag-flow tests', () => {
  let element: GrChangeListHashtagFlow;
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
      expect(element).shadowDom.to.equal(/* HTML */ `
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
          .withArgs(changes[i]._number, sinon.match.any)
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
      await flush();
    });

    test('renders hashtags flow', () => {
      expect(element).shadowDom.to.equal(
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
                    id="create-new-hashtag-button"
                    flatten=""
                    aria-disabled="true"
                    disabled=""
                    role="button"
                    tabindex="-1"
                    >Create new hashtag</gr-button
                  >
                  <gr-button
                    id="apply-hashtag-button"
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

    test('apply hashtag from selected change', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      // selects "hashtag1"
      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '#apply-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Applying hashtag...'
      );

      await resolvePromises(['hashtag1' as Hashtag]);
      await element.updateComplete;

      assert.isTrue(setChangeHashtagStub.calledThrice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changes[0]._number,
        {add: ['hashtag1']},
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changes[1]._number,
        {add: ['hashtag1']},
      ]);
      assert.deepEqual(setChangeHashtagStub.thirdCall.args, [
        changes[2]._number,
        {add: ['hashtag1']},
      ]);
      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: '3 Changes added to hashtag1',
        showDismiss: true,
      });
      assert.isTrue(
        queryAndAssert<IronDropdownElement>(element, 'iron-dropdown').opened
      );
    });

    test('shows error when apply hashtag fails', async () => {
      // selects "hashtag1"
      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '#apply-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Applying hashtag...'
      );

      await rejectPromises();
      await element.updateComplete;
      await waitUntil(() => query(element, '.error') !== undefined);

      assert.equal(
        queryAndAssert(element, '.error').textContent,
        'Failed to apply'
      );
      assert.equal(
        queryAndAssert(element, 'gr-button#cancel-button').textContent,
        'Cancel'
      );
      assert.isUndefined(query(element, '.loadingText'));
    });

    test('apply multiple hashtag from selected change', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      // selects "hashtag1"
      queryAll<HTMLButtonElement>(element, 'button.chip')[0].click();
      await element.updateComplete;

      // selects "hashtag2"
      queryAll<HTMLButtonElement>(element, 'button.chip')[2].click();
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '#apply-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Applying hashtag...'
      );

      await resolvePromises(['hashtag1' as Hashtag, 'hashtag2' as Hashtag]);
      await element.updateComplete;

      assert.isTrue(setChangeHashtagStub.calledThrice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changes[0]._number,
        {add: ['hashtag1', 'hashtag2']},
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changes[1]._number,
        {add: ['hashtag1', 'hashtag2']},
      ]);
      assert.deepEqual(setChangeHashtagStub.thirdCall.args, [
        changes[2]._number,
        {add: ['hashtag1', 'hashtag2']},
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: '2 hashtags added to changes',
        showDismiss: true,
      });
    });

    test('apply existing hashtag not on selected changes', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      const getHashtagsStub = stubRestApi(
        'getChangesWithSimilarHashtag'
      ).resolves([{...createChange(), hashtags: ['foo' as Hashtag]}]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );

      autocomplete.setFocus(true);
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getHashtagsStub, 'getHashtagsStub');
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#create-new-hashtag-button').disabled
      );

      queryAndAssert<GrButton>(element, '#apply-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Applying hashtag...'
      );

      await resolvePromises(['foo' as Hashtag]);

      assert.isTrue(setChangeHashtagStub.calledThrice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changes[0]._number,
        {add: ['foo']},
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changes[1]._number,
        {add: ['foo']},
      ]);
      assert.deepEqual(setChangeHashtagStub.thirdCall.args, [
        changes[2]._number,
        {add: ['foo']},
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: '3 Changes added to foo',
        showDismiss: true,
      });
      assert.isTrue(
        queryAndAssert<IronDropdownElement>(element, 'iron-dropdown').opened
      );
    });

    test('create new hashtag', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      const getHashtagsStub = stubRestApi(
        'getChangesWithSimilarHashtag'
      ).resolves([]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.setFocus(true);
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getHashtagsStub, 'getHashtagsStub');
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-hashtag-button').disabled
      );

      queryAndAssert<GrButton>(element, '#create-new-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Creating hashtag...'
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
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changes[1]._number,
        {add: ['foo']},
      ]);
      assert.deepEqual(setChangeHashtagStub.thirdCall.args, [
        changes[2]._number,
        {add: ['foo']},
      ]);

      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: 'foo created',
        showDismiss: true,
      });
      assert.isTrue(
        queryAndAssert<IronDropdownElement>(element, 'iron-dropdown').opened
      );
      assert.equal(
        queryAll<HTMLButtonElement>(element, 'button.chip')[2].innerText,
        'foo'
      );
    });

    test('shows error when create hashtag fails', async () => {
      const getHashtagsStub = stubRestApi(
        'getChangesWithSimilarHashtag'
      ).resolves([]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.setFocus(true);
      autocomplete.text = 'foo';
      await element.updateComplete;
      await waitUntilCalled(getHashtagsStub, 'getHashtagsStub');
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-hashtag-button').disabled
      );

      queryAndAssert<GrButton>(element, '#create-new-hashtag-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Creating hashtag...'
      );

      await rejectPromises();
      await element.updateComplete;
      await waitUntil(() => query(element, '.error') !== undefined);

      assert.equal(
        queryAndAssert(element, '.error').textContent,
        'Failed to create'
      );
      assert.equal(
        queryAndAssert(element, 'gr-button#cancel-button').textContent,
        'Cancel'
      );
      assert.isUndefined(query(element, '.loadingText'));
    });

    test('cannot apply existing hashtag already on selected changes', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      // selects "sharedHashtag"
      queryAll<HTMLButtonElement>(element, 'button.chip')[1].click();
      await element.updateComplete;

      assert.isTrue(
        queryAndAssert<GrButton>(element, '#apply-hashtag-button').disabled
      );
    });
  });
});
