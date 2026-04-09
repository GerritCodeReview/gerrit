/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './splash-page-action';
import {SplashPageAction} from './splash-page-action';
import {ChatModel, chatModelToken} from '../../models/chat/chat-model';
import {Action} from '../../api/ai-code-review';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {
  chatActions,
  chatProvider,
  createChange,
} from '../../test/test-data-generators';
import {changeModelToken} from '../../models/change/change-model';
import {ParsedChangeInfo} from '../../types/types';

suite('splash-page-action tests', () => {
  let element: SplashPageAction;
  let chatModel: ChatModel;

  setup(async () => {
    const pluginLoader = testResolver(pluginLoaderToken);
    pluginLoader.pluginsModel.aiCodeReviewRegister({
      pluginName: 'test-plugin',
      provider: chatProvider,
    });

    const changeModel = testResolver(changeModelToken);
    changeModel.updateState({
      change: createChange() as ParsedChangeInfo,
    });

    chatModel = testResolver(chatModelToken);

    element = await fixture<SplashPageAction>(
      html`<splash-page-action></splash-page-action>`
    );
  });

  test('renders with action', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      hover_text: 'Test hover',
      subtext: 'Test subtext',
      icon: 'test-icon',
      initial_user_prompt: 'Test prompt',
    };
    element.action = action;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container">
          <md-assist-chip class="action-chip" title="Test hover">
            <div class="chip-content">
              <gr-icon class="action-icon" icon="test-icon"></gr-icon>
              <div class="action-text-container">
                <div class="main-action-text-container has-subtext">
                  <span class="action-text">Test Action</span>
                </div>

                <span class="action-subtext">Test subtext</span>
              </div>
            </div>
          </md-assist-chip>
          <gr-tooltip-content
            class="info-button-container"
            has-tooltip=""
            title="Capability details"
          >
            <gr-button
              aria-disabled="false"
              class="info-button"
              flatten=""
              role="button"
              tabindex="0"
            >
              <gr-icon icon="info"></gr-icon>
            </gr-button>
          </gr-tooltip-content>
        </div>
        <dialog id="detailsModal" tabindex="-1">
          <div role="dialog" aria-labelledby="detailsTitle">
            <h3 class="heading-3 modalHeader" id="detailsTitle">Test Action</h3>
            <div class="detailsContent">
              <div class="modal-row instruction-row">
                <gr-icon icon="terminal"></gr-icon>
                <div class="modal-row-content">
                  <div class="modal-row-title">Instruction:</div>
                  <div class="modal-row-text instruction-text collapsed">
                    Test prompt
                  </div>
                </div>
              </div>
            </div>
            <div class="modalActions">
              <gr-button
                aria-disabled="false"
                id="closeButton"
                link=""
                primary=""
                role="button"
                tabindex="0"
              >
                Close
              </gr-button>
            </div>
          </div>
        </dialog>
      `
    );
  });

  test('handles click with predefined prompt', async () => {
    // Summarize action
    element.action = chatActions.actions[1];
    await element.updateComplete;

    const chip = element.shadowRoot?.querySelector('md-assist-chip');
    assert.isOk(chip);
    chip.click();

    const turns = chatModel.getState().turns;
    assert.lengthOf(turns, 1);
    assert.equal(turns[0].userMessage.content, 'Summarize the change');
  });

  test('handles click with user input', async () => {
    // Freeform action
    element.action = chatActions.actions[0];
    await element.updateComplete;

    const chip = element.shadowRoot?.querySelector('md-assist-chip');
    assert.isOk(chip);
    chip.click();

    const turns = chatModel.getState().turns;
    assert.lengthOf(turns, 0);
  });

  test('opens details dialog on info button click', async () => {
    element.action = {
      id: 'test-action',
      display_text: 'Test Action',
    };
    await element.updateComplete;

    const infoButton = element.shadowRoot?.querySelector('.info-button');
    assert.isOk(infoButton);

    const dialog = element.shadowRoot?.querySelector(
      '#detailsModal'
    ) as HTMLDialogElement;
    assert.isOk(dialog);
    assert.isFalse(dialog.open);

    (infoButton as HTMLElement).click();
    await element.updateComplete;

    assert.isTrue(dialog.open);

    const turns = chatModel.getState().turns;
    assert.lengthOf(turns, 0);
  });
  test('computeCodeSearchLink', () => {
    const elementWithPrivateMethods = element as unknown as {
      computeCodeSearchLink: (s?: string) => string;
    };
    assert.equal(
      elementWithPrivateMethods.computeCodeSearchLink(
        '//depot/google3/configs/GERRIT_CAPABILITIES.textproto:symbol'
      ),
      'http://cs/depot/google3/configs/GERRIT_CAPABILITIES.textproto'
    );
    assert.equal(
      elementWithPrivateMethods.computeCodeSearchLink(
        '//depot/google3/configs/GERRIT_CAPABILITIES.textproto'
      ),
      'http://cs/depot/google3/configs/GERRIT_CAPABILITIES.textproto'
    );
    assert.equal(
      elementWithPrivateMethods.computeCodeSearchLink('just/path'),
      'just/path'
    );
    assert.equal(elementWithPrivateMethods.computeCodeSearchLink(''), '');
    assert.equal(
      elementWithPrivateMethods.computeCodeSearchLink(undefined),
      ''
    );
  });

  test('renders with custom_action_source', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      custom_action_source: {
        custom_action_id:
          '//depot/google3/configs/GERRIT_CAPABILITIES.textproto:symbol',
      },
    };
    element.action = action;
    await element.updateComplete;

    const modal = element.shadowRoot?.querySelector('#detailsModal');
    assert.isOk(modal);

    const sourceLink = modal?.querySelector(
      '.modal-row-text a'
    ) as HTMLAnchorElement;
    assert.isOk(sourceLink);
    assert.equal(
      sourceLink.href,
      'http://cs/depot/google3/configs/GERRIT_CAPABILITIES.textproto'
    );
    assert.equal(sourceLink.innerText.trim(), 'Capability Definition');
  });

  test('renders with matched_files', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      matched_files: ['file1.txt', 'file2.txt'],
    };
    element.action = action;
    await element.updateComplete;

    const modal = element.shadowRoot?.querySelector('#detailsModal');
    assert.isOk(modal);

    const fileItems = modal?.querySelectorAll('.file-item');
    assert.equal(fileItems?.length, 2);
    assert.equal(fileItems?.[0].textContent?.trim(), 'file1.txt');
    assert.equal(fileItems?.[1].textContent?.trim(), 'file2.txt');

    const expandButton = modal?.querySelector(
      '.matched-files-row .expand-button'
    );
    assert.isNotOk(expandButton);
  });

  test('renders with many matched_files and expands', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      matched_files: [
        'file1.txt',
        'file2.txt',
        'file3.txt',
        'file4.txt',
        'file5.txt',
      ],
    };
    element.action = action;
    await element.updateComplete;

    const modal = element.shadowRoot?.querySelector('#detailsModal');
    assert.isOk(modal);

    let fileItems = modal?.querySelectorAll('.file-item');
    assert.equal(fileItems?.length, 4);

    const expandButton = modal?.querySelector(
      '.matched-files-row .expand-button'
    ) as HTMLElement;
    assert.isOk(expandButton);
    assert.equal(expandButton.innerText.trim(), 'Show more');

    expandButton.click();
    await element.updateComplete;

    fileItems = modal?.querySelectorAll('.file-item');
    assert.equal(fileItems?.length, 5);
    assert.equal(expandButton.innerText.trim(), 'Show less');
  });
});
