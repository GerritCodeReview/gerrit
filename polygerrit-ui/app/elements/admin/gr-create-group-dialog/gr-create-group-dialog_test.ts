/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-create-group-dialog';
import {GrCreateGroupDialog} from './gr-create-group-dialog';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {GroupId} from '../../../types/common';
import {assert, fixture, html} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';

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
              <div class="title-flex">
                <span class="title"> Group name </span>
              </div>
              <div class="value-flex">
                <span class="value">
                  <md-outlined-text-field
                    autocomplete=""
                    class="showBlueFocusBorder"
                    inputmode=""
                    type="text"
                  >
                  </md-outlined-text-field>
                </span>
              </div>
            </section>
            <section>
              <div class="title-flex">
                <span class="title">
                  Make group visible to all registered users
                </span>
              </div>
              <div class="value-flex">
                <span class="value">
                  <md-checkbox> </md-checkbox>
                </span>
              </div>
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

    const inputEl = queryAndAssert<MdOutlinedTextField>(
      element,
      'md-outlined-text-field'
    );
    inputEl.value = GROUP_NAME;
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
    assert.isTrue(setUrlStub.calledWith('/admin/groups/testId551'));
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
