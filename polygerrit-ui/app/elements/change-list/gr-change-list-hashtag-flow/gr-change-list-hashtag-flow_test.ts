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

  suite('changes in existing hashtags', () => {
    const changesWithHashtags: ChangeInfo[] = [
      {
        ...createChange(),
        _number: 1 as NumericChangeId,
        subject: 'Subject 1',
        hashtags: ['hashtag1' as Hashtag],
      },
      {
        ...createChange(),
        _number: 2 as NumericChangeId,
        subject: 'Subject 2',
        hashtags: ['hashtag2' as Hashtag],
      },
    ];
    let setChangeHashtagPromises: MockPromise<string>[];
    let setChangeHashtagStub: sinon.SinonStub;

    async function resolvePromises() {
      setChangeHashtagPromises[0].resolve('foo');
      setChangeHashtagPromises[1].resolve('foo');
      await element.updateComplete;
    }

    setup(async () => {
      stubRestApi('getDetailedChangesWithActions').resolves(
        changesWithHashtags
      );
      setChangeHashtagPromises = [];
      setChangeHashtagStub = stubRestApi('setChangeHashtag');
      for (let i = 0; i < changesWithHashtags.length; i++) {
        const promise = mockPromise<string>();
        setChangeHashtagPromises.push(promise);
        setChangeHashtagStub
          .withArgs(changesWithHashtags[i]._number, sinon.match.any)
          .returns(promise);
      }
      model = new BulkActionsModel(getAppContext().restApiService);
      model.sync(changesWithHashtags);

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
      await selectChange(changesWithHashtags[0]);
      await selectChange(changesWithHashtags[1]);
      await waitUntilObserved(model.selectedChanges$, s => s.length === 2);
      await element.updateComplete;

      // open flow
      queryAndAssert<GrButton>(element, 'gr-button#start-flow').click();
      await element.updateComplete;
      await flush();
    });

    test('renders existing-hashtags flow', () => {
      expect(element).shadowDom.to.equal(
        /* HTML */ `
          <gr-button
            id="start-flow"
            flatten=""
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
                <span class="chip">hashtag1</span>
                <span class="chip">hashtag2</span>
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
                    id="remove-hashtags-button"
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

    test('remove single hashtag', async () => {
      queryAll<HTMLSpanElement>(element, 'span.chip')[0].click();
      await element.updateComplete;
      queryAndAssert<GrButton>(element, '#remove-hashtags-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Removing 1 hashtag...'
      );

      await resolvePromises();
      await element.updateComplete;

      // not called for second change which as a different hashtag
      assert.isTrue(setChangeHashtagStub.calledOnce);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changesWithHashtags[0]._number,
        {remove: ['hashtag1']},
      ]);
    });

    test('remove multiple hashtags', async () => {
      queryAll<HTMLSpanElement>(element, 'span.chip')[0].click();
      queryAll<HTMLSpanElement>(element, 'span.chip')[1].click();
      await element.updateComplete;
      queryAndAssert<GrButton>(element, '#remove-hashtags-button').click();
      await element.updateComplete;

      assert.equal(
        queryAndAssert(element, '.loadingText').textContent,
        'Removing 2 hashtags...'
      );

      await resolvePromises();
      await element.updateComplete;

      // not called for second change which as a different hashtag
      assert.isTrue(setChangeHashtagStub.calledTwice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changesWithHashtags[0]._number,
        {remove: ['hashtag1', 'hashtag2']},
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changesWithHashtags[1]._number,
        {remove: ['hashtag1', 'hashtag2']},
      ]);
    });

    test('can only apply a single hashtag', async () => {
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

    test('applies hashtag to all changes', async () => {
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

      assert.isTrue(setChangeHashtagStub.calledTwice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changesWithHashtags[0]._number,
        {add: ['hashtag1']},
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changesWithHashtags[1]._number,
        {add: ['hashtag1']},
      ]);
    });
  });

  suite('change have no existing hashtags', () => {
    const changesWithNoHashtags: ChangeInfo[] = [
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
    let setChangeHashtagPromises: MockPromise<string>[];
    let setChangeHashtagStub: sinon.SinonStub;

    async function resolvePromises() {
      setChangeHashtagPromises[0].resolve('foo');
      setChangeHashtagPromises[1].resolve('foo');
      await element.updateComplete;
    }

    setup(async () => {
      stubRestApi('getDetailedChangesWithActions').resolves(
        changesWithNoHashtags
      );
      setChangeHashtagPromises = [];
      setChangeHashtagStub = stubRestApi('setChangeHashtag');
      for (let i = 0; i < changesWithNoHashtags.length; i++) {
        const promise = mockPromise<string>();
        setChangeHashtagPromises.push(promise);
        setChangeHashtagStub
          .withArgs(changesWithNoHashtags[i]._number, sinon.match.any)
          .returns(promise);
      }

      model = new BulkActionsModel(getAppContext().restApiService);
      model.sync(changesWithNoHashtags);

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
      await selectChange(changesWithNoHashtags[0]);
      await selectChange(changesWithNoHashtags[1]);
      await waitUntilObserved(model.selectedChanges$, s => s.length === 2);
      await element.updateComplete;

      // open flow
      queryAndAssert<GrButton>(element, 'gr-button#start-flow').click();
      await element.updateComplete;
      await flush();
    });

    test('renders no-existing-hashtags flow', () => {
      expect(element).shadowDom.to.equal(
        /* HTML */ `
          <gr-button
            id="start-flow"
            flatten=""
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
              <gr-autocomplete
                placeholder="Type hashtag name to create or filter hashtags"
                show-blue-focus-border=""
              ></gr-autocomplete>
              <div class="footer">
                <div class="loadingOrError"></div>
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

    test('create new hashtag', async () => {
      const getHashtagsStub = stubRestApi(
        'getChangesWithSimilarHashtag'
      ).resolves([]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );
      autocomplete.focus();
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

      await resolvePromises();
      await element.updateComplete;

      assert.isTrue(setChangeHashtagStub.calledTwice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changesWithNoHashtags[0]._number,
        {add: ['foo']},
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changesWithNoHashtags[1]._number,
        {add: ['foo']},
      ]);
    });

    test('apply hashtag', async () => {
      const getHashtagsStub = stubRestApi(
        'getChangesWithSimilarHashtag'
      ).resolves([{...createChange(), hashtags: ['foo' as Hashtag]}]);
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        'gr-autocomplete'
      );

      autocomplete.focus();
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

      await resolvePromises();

      assert.isTrue(setChangeHashtagStub.calledTwice);
      assert.deepEqual(setChangeHashtagStub.firstCall.args, [
        changesWithNoHashtags[0]._number,
        {add: ['foo']},
      ]);
      assert.deepEqual(setChangeHashtagStub.secondCall.args, [
        changesWithNoHashtags[1]._number,
        {add: ['foo']},
      ]);
    });
  });
});
