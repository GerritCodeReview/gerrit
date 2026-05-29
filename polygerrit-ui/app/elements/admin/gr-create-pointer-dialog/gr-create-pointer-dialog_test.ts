/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-pointer-dialog';
import {GrCreatePointerDialog} from './gr-create-pointer-dialog';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {BranchName} from '../../../types/common';
import {assert, fixture, html} from '@open-wc/testing';
import {RepoDetailView} from '../../../models/views/repo';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';

suite('gr-create-pointer-dialog tests', () => {
  let element: GrCreatePointerDialog;

  const queryInput = (element: Element) =>
    queryAndAssert<MdOutlinedTextField>(element, 'md-outlined-text-field');

  setup(async () => {
    element = await fixture(
      html`<gr-create-pointer-dialog></gr-create-pointer-dialog>`
    );
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles">
          <div id="form">
            <section id="itemNameSection">
              <span class="title"> name </span>
              <md-outlined-text-field
                autocomplete=""
                class="showBlueFocusBorder"
                inputmode=""
                placeholder=" Name"
                type="text"
              >
              </md-outlined-text-field>
            </section>
            <section id="createEmptyCommitSection">
              <div>
                <span class="title"> Point to </span>
              </div>
              <div>
                <span class="value">
                  <md-outlined-select id="initialCommit" value="false">
                    <md-select-option
                      data-aria-selected="true"
                      md-menu-item=""
                      tabindex="0"
                      value="false"
                    >
                      <div slot="headline">Existing Revision</div>
                    </md-select-option>
                    <md-select-option
                      md-menu-item=""
                      tabindex="-1"
                      value="true"
                    >
                      <div slot="headline">Initial empty commit</div>
                    </md-select-option>
                  </md-outlined-select>
                </span>
              </div>
            </section>
            <section id="itemRevisionSection">
              <span class="title"> Initial Revision </span>
              <md-outlined-text-field
                autocomplete=""
                class="showBlueFocusBorder"
                inputmode=""
                placeholder="Revision (Branch or SHA-1)"
                type="text"
              >
              </md-outlined-text-field>
            </section>
            <section id="itemAnnotationSection">
              <span class="title"> Annotation </span>
              <md-outlined-text-field
                autocomplete=""
                class="showBlueFocusBorder"
                inputmode=""
                placeholder="Annotation (Optional)"
                type="text"
              >
              </md-outlined-text-field>
            </section>
          </div>
        </div>
      `
    );
  });

  test('branch created', async () => {
    stubRestApi('createRepoBranch').returns(Promise.resolve(new Response()));

    const promise = mockPromise();
    element.addEventListener('update-item-name', () => {
      promise.resolve();
    });

    element.itemName = 'test-branch' as BranchName;
    element.itemDetail = 'branches' as RepoDetailView.BRANCHES;

    await element.updateComplete;

    const itemNameSection = queryInput(
      queryAndAssert(element, '#itemNameSection')
    );
    itemNameSection.value = 'test-branch2';
    itemNameSection.dispatchEvent(
      new Event('input', {bubbles: true, composed: true})
    );

    const itemRevisionSection = queryInput(
      queryAndAssert(element, '#itemRevisionSection')
    );
    itemRevisionSection.value = 'HEAD';
    itemRevisionSection.dispatchEvent(
      new Event('input', {bubbles: true, composed: true})
    );

    await promise;

    assert.equal(element.itemName, 'test-branch2' as BranchName);
    assert.equal(element.itemRevision, 'HEAD');
  });

  test('tag created', async () => {
    stubRestApi('createRepoTag').returns(Promise.resolve(new Response()));

    const promise = mockPromise();
    element.addEventListener('update-item-name', () => {
      promise.resolve();
    });

    element.itemName = 'test-tag' as BranchName;
    element.itemDetail = 'tags' as RepoDetailView.TAGS;

    const itemNameSection = queryInput(
      queryAndAssert(element, '#itemNameSection')
    );
    itemNameSection.value = 'test-tag2';
    itemNameSection.dispatchEvent(
      new Event('input', {bubbles: true, composed: true})
    );

    const itemRevisionSection = queryInput(
      queryAndAssert(element, '#itemRevisionSection')
    );
    itemRevisionSection.value = 'HEAD';
    itemRevisionSection.dispatchEvent(
      new Event('input', {bubbles: true, composed: true})
    );

    await promise;

    assert.equal(element.itemName, 'test-tag2' as BranchName);
    assert.equal(element.itemRevision, 'HEAD');
  });

  test('tag created with annotations', async () => {
    stubRestApi('createRepoTag').returns(Promise.resolve(new Response()));

    const promise = mockPromise();
    element.addEventListener('update-item-name', () => {
      promise.resolve();
    });

    element.itemName = 'test-tag' as BranchName;
    element.itemAnnotation = 'test-message';
    element.itemDetail = 'tags' as RepoDetailView.TAGS;

    const itemNameSection = queryInput(
      queryAndAssert(element, '#itemNameSection')
    );
    itemNameSection.value = 'test-tag2';
    itemNameSection.dispatchEvent(
      new Event('input', {bubbles: true, composed: true})
    );

    const itemAnnotationSection = queryInput(
      queryAndAssert(element, '#itemAnnotationSection')
    );
    itemAnnotationSection.value = 'test-message2';
    itemAnnotationSection.dispatchEvent(
      new Event('input', {bubbles: true, composed: true})
    );

    const itemRevisionSection = queryInput(
      queryAndAssert(element, '#itemRevisionSection')
    );
    itemRevisionSection.value = 'HEAD';
    itemRevisionSection.dispatchEvent(
      new Event('input', {bubbles: true, composed: true})
    );

    await promise;

    assert.equal(element.itemName, 'test-tag2' as BranchName);
    assert.equal(element.itemAnnotation, 'test-message2');
    assert.equal(element.itemRevision, 'HEAD');
  });
});
