/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
import {html} from 'lit';
import './gr-trigger-vote-hovercard';
import {GrTriggerVoteHovercard} from './gr-trigger-vote-hovercard';
import {createLabelInfo} from '../../../test/test-data-generators';

suite('gr-trigger-vote-hovercard tests', () => {
  let element: GrTriggerVoteHovercard;
  setup(async () => {
    element = await fixture<GrTriggerVoteHovercard>(
      html`<gr-trigger-vote-hovercard
        .labelInfo=${createLabelInfo()}
        .labelName=${'Foo'}
      ></gr-trigger-vote-hovercard>`
    );
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div id="container" role="tooltip" tabindex="-1">
          <div class="section">
            <div class="sectionContent">
              <h3 class="heading-3 name">
                <span> Foo </span>
              </h3>
            </div>
          </div>
          <div class="section">
            <div class="sectionIcon">
              <gr-icon icon="info" class=" small"></gr-icon>
            </div>
            <div class="sectionContent">
              <div class="row">
                <div class="title">Status</div>
                <div>
                  <slot name="label-info"> </slot>
                </div>
              </div>
            </div>
          </div>
        </div>
      `
    );
  });
});
