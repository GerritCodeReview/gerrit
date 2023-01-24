/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-group-dialog';
import {GrCreateGroupDialog} from './gr-create-group-dialog';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {IronInputElement} from '@polymer/iron-input';
import {GroupId} from '../../../types/common';
import {fixture, html, assert} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';

suite('gr-create-group-dialog tests', () => {
  let element: GrCreateGroupDialog;

  const GROUP_NAME = 'test-group';

  setup(async () => {
    element = await fixture(
      html`<gr-create-group-dialog></gr-create-group-dialog>`
    );
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles">
          <div id="form">
            <section>
              <span class="title"> Group name </span>
              <iron-input>
                <input />
              </iron-input>
            </section>
          </div>
        </div>
      `
    );
  });

  test('name is updated correctly', async () => {
    const promise = mockPromise();
    element.addEventListener('has-new-group-name', () => {
      promise.resolve();
    });

    const inputEl = queryAndAssert<IronInputElement>(element, 'iron-input');
    inputEl.bindValue = GROUP_NAME;
    inputEl.dispatchEvent(new Event('input', {bubbles: true, composed: true}));

    await promise;

    assert.deepEqual(element.name, GROUP_NAME);
  });

  test('test for redirecting to group on successful creation', async () => {
    stubRestApi('createGroup').returns(
      Promise.resolve({status: 201} as Response)
    );
    stubRestApi('getGroupConfig').returns(
      Promise.resolve({id: 'testId551' as GroupId, group_id: 551})
    );

    const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
    await element.handleCreateGroup();
    assert.isTrue(setUrlStub.calledWith('/admin/groups/551'));
  });

  test('test for unsuccessful group creation', async () => {
    stubRestApi('createGroup').returns(
      Promise.resolve({status: 409} as Response)
    );
    stubRestApi('getGroupConfig').returns(
      Promise.resolve({id: 'testId551' as GroupId, group_id: 551})
    );

    const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
    await element.handleCreateGroup();
    assert.isFalse(setUrlStub.called);
  });
});
