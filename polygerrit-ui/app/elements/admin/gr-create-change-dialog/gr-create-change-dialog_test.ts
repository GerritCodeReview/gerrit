/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-create-change-dialog';
import {GrCreateChangeDialog} from './gr-create-change-dialog';
import {BranchName, GitRef, RepoName} from '../../../types/common';
import {InheritedBooleanInfoConfiguredValue} from '../../../constants/constants';
import {createChange} from '../../../test/test-data-generators';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-create-change-dialog tests', () => {
  let element: GrCreateChangeDialog;

  setup(async () => {
    stubRestApi('getRepoBranches').callsFake((input: string) => {
      if (input.startsWith('test')) {
        return Promise.resolve([
          {
            ref: 'refs/heads/test-branch' as GitRef,
            revision: '67ebf73496383c6777035e374d2d664009e2aa5c',
            can_delete: true,
          },
        ]);
      } else {
        return Promise.resolve([]);
      }
    });
    element = await fixture(
      html`<gr-create-change-dialog></gr-create-change-dialog>`
    );
    element.repoName = 'test-repo' as RepoName;
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles">
          <section>
            <span class="title"> Select branch for new change </span>
            <span class="value">
              <gr-autocomplete
                id="branchInput"
                placeholder="Destination branch"
              >
              </gr-autocomplete>
            </span>
          </section>
          <section>
            <span class="title"> Provide base commit sha1 for change </span>
            <span class="value">
              <iron-input>
                <input
                  id="baseCommitInput"
                  maxlength="40"
                  placeholder="(optional)"
                />
              </iron-input>
            </span>
          </section>
          <section>
            <span class="title"> Enter topic for new change </span>
            <span class="value">
              <iron-input>
                <input
                  id="tagNameInput"
                  maxlength="1024"
                  placeholder="(optional)"
                />
              </iron-input>
            </span>
          </section>
          <section id="description">
            <span class="title"> Description </span>
            <span class="value">
              <iron-autogrow-textarea
                aria-disabled="false"
                autocomplete="on"
                class="message"
                id="messageInput"
                maxrows="15"
                placeholder="Insert the description of the change."
                rows="4"
              >
              </iron-autogrow-textarea>
            </span>
          </section>
          <section>
            <label class="title" for="privateChangeCheckBox">
              Private change
            </label>
            <span class="value">
              <input id="privateChangeCheckBox" type="checkbox" />
            </span>
          </section>
        </div>
      `
    );
  });

  test('new change created with default', async () => {
    const configInputObj = {
      branch: 'test-branch',
      subject: 'first change created with polygerrit ui',
      topic: 'test-topic',
      is_private: false,
      work_in_progress: true,
    };

    const saveStub = stubRestApi('createChange').returns(
      Promise.resolve(createChange())
    );

    element.branch = 'test-branch' as BranchName;
    element.topic = 'test-topic';
    element.subject = 'first change created with polygerrit ui';
    assert.isFalse(element.privateChangeCheckBox.checked);

    const messageInput = queryAndAssert<IronAutogrowTextareaElement>(
      element,
      '#messageInput'
    );
    messageInput.bindValue = configInputObj.subject;

    await element.handleCreateChange();
    // Private change
    assert.isFalse(saveStub.lastCall.args[4]);
    // WIP Change
    assert.isTrue(saveStub.lastCall.args[5]);
    assert.isTrue(saveStub.called);
  });

  test('new change created with private', async () => {
    element.privateByDefault = {
      configured_value: InheritedBooleanInfoConfiguredValue.TRUE,
      inherited_value: false,
      value: true,
    };
    sinon.stub(element, 'formatPrivateByDefaultBoolean').callsFake(() => true);
    await element.updateComplete;

    const configInputObj = {
      branch: 'test-branch',
      subject: 'first change created with polygerrit ui',
      topic: 'test-topic',
      is_private: true,
      work_in_progress: true,
    };

    const saveStub = stubRestApi('createChange').returns(
      Promise.resolve(createChange())
    );

    element.branch = 'test-branch' as BranchName;
    element.topic = 'test-topic';
    element.subject = 'first change created with polygerrit ui';
    assert.isTrue(element.privateChangeCheckBox.checked);

    const messageInput = queryAndAssert<IronAutogrowTextareaElement>(
      element,
      '#messageInput'
    );
    messageInput.bindValue = configInputObj.subject;

    await element.handleCreateChange();
    // Private change
    assert.isTrue(saveStub.lastCall.args[4]);
    // WIP Change
    assert.isTrue(saveStub.lastCall.args[5]);
    assert.isTrue(saveStub.called);
  });

  test('getRepoBranchesSuggestions empty', async () => {
    const branches = await element.getRepoBranchesSuggestions('nonexistent');
    assert.equal(branches.length, 0);
  });

  test('getRepoBranchesSuggestions non-empty', async () => {
    const branches = await element.getRepoBranchesSuggestions('test-branch');
    assert.equal(branches.length, 1);
    assert.equal(branches[0].name, 'test-branch');
  });
});
