/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
import {html, LitElement, nothing} from 'lit';
import {property} from 'lit/decorators.js';
import './gr-change-list-column-requirements-summary';
import {GrChangeListColumnRequirementsSummary} from './gr-change-list-column-requirements-summary';
import {
  createApproval,
  createChange,
  createDetailedLabelInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
import {
  BranchName,
  ChangeInfo,
  ChangeStatus,
  NumericChangeId,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {PluginApi} from '../../../api/plugin';
import {PluginElement} from '../../../api/hook';
import {mockPromise, query, queryAndAssert} from '../../../test/test-utils';

class TestSummaryDecoratorElement extends LitElement implements PluginElement {
  @property({type: Object})
  change?: ChangeInfo;

  plugin!: PluginApi;

  content?: HTMLElement;

  override willUpdate() {
    const shouldOverride = this.change?.branch === ('main' as BranchName);
    if (this.content) {
      this.content.hidden = shouldOverride;
    }
  }

  override render() {
    if (this.change?.branch !== ('main' as BranchName)) return nothing;
    return html`<span class="custom-status"
      >Custom: ${this.change._number}</span
    >`;
  }
}

class TestSummaryReplaceElement extends LitElement implements PluginElement {
  @property({type: Object})
  change?: ChangeInfo;

  plugin!: PluginApi;

  override render() {
    return html`<span class="replaced-status"
      >Replaced: ${this.change?._number}</span
    >`;
  }
}

const DECORATOR_TAG = 'test-requirements-summary-decorator';
const REPLACER_TAG = 'test-requirements-summary-replacer';
if (!customElements.get(DECORATOR_TAG)) {
  customElements.define(DECORATOR_TAG, TestSummaryDecoratorElement);
}
if (!customElements.get(REPLACER_TAG)) {
  customElements.define(REPLACER_TAG, TestSummaryReplaceElement);
}

suite('gr-change-list-column-requirements-summary tests', () => {
  let element: GrChangeListColumnRequirementsSummary;
  let change: ChangeInfo;
  setup(() => {
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      status: SubmitRequirementStatus.UNSATISFIED,
      description: 'Test Description',
      submittability_expression_result: createSubmitRequirementExpressionInfo(),
    };
    change = {
      ...createChange(),
      submit_requirements: [
        submitRequirement,
        createNonApplicableSubmitRequirementResultInfo(),
      ],
      labels: {
        Verified: {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createApproval(),
              value: 2,
            },
          ],
        },
      },
    };
  });

  test('renders', async () => {
    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary .change=${change}>
      </gr-change-list-column-requirements-summary>`
    );
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-endpoint-decorator name="change-list-requirements-summary">
          <span class="requirements-summary-content">
            <span class="block" role="button" tabindex="0">
              <gr-submit-requirement-dashboard-hovercard>
              </gr-submit-requirement-dashboard-hovercard>
              <gr-icon class="block" role="img" icon="block"></gr-icon>
              <span class="unsatisfied">1 missing</span>
            </span>
          </span>
          <gr-endpoint-param name="change"></gr-endpoint-param>
        </gr-endpoint-decorator>
      `
    );
  });

  test('renders comment count', async () => {
    change = {
      ...change,
      unresolved_comment_count: 5,
    };
    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary .change=${change}>
      </gr-change-list-column-requirements-summary>`
    );
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-endpoint-decorator name="change-list-requirements-summary">
          <span class="requirements-summary-content">
            <span class="block" role="button" tabindex="0">
              <gr-submit-requirement-dashboard-hovercard>
              </gr-submit-requirement-dashboard-hovercard>
              <gr-icon class="block" role="img" icon="block"></gr-icon>
              <span class="unsatisfied">1 missing</span>
            </span>
          </span>
          <gr-endpoint-param name="change"></gr-endpoint-param>
        </gr-endpoint-decorator>
        <gr-icon
          class="commentIcon"
          small
          filled
          icon="chat_bubble"
          title="5 unresolved comments"
        ></gr-icon>
      `
    );
  });

  test('does not render endpoint decorator for non-active statuses', async () => {
    const mergedChange: ChangeInfo = {
      ...change,
      status: ChangeStatus.MERGED,
    };
    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary .change=${mergedChange}>
      </gr-change-list-column-requirements-summary>`
    );
    assert.isUndefined(query(element, 'gr-endpoint-decorator'));
    assert.isDefined(query(element, 'gr-change-status'));

    element.change = {
      ...change,
      work_in_progress: true,
    };
    await element.updateComplete;
    assert.isUndefined(query(element, 'gr-endpoint-decorator'));
    assert.isDefined(query(element, 'gr-change-status'));
  });

  test('plugin decorates and conditionally hides default summary across row reuse', async () => {
    let plugin!: PluginApi;
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://some/plugins/summary-decorator/url.js'
    );

    const attached = mockPromise<TestSummaryDecoratorElement>();
    const hook = plugin.registerCustomComponent<TestSummaryDecoratorElement>(
      'change-list-requirements-summary',
      DECORATOR_TAG
    );
    hook.onAttached(el => attached.resolve(el));

    const mainChange: ChangeInfo = {
      ...change,
      branch: 'main' as BranchName,
    };
    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary .change=${mainChange}>
      </gr-change-list-column-requirements-summary>`
    );

    const pluginEl = await attached;
    await pluginEl.updateComplete;

    const defaultContent = queryAndAssert<HTMLElement>(
      element,
      '.requirements-summary-content'
    );
    assert.strictEqual(pluginEl.content, defaultContent);
    assert.isNotNull(
      defaultContent.querySelector('gr-submit-requirement-dashboard-hovercard')
    );
    assert.strictEqual(pluginEl.change, mainChange);
    assert.isTrue(defaultContent.hidden);
    assert.equal(getComputedStyle(defaultContent).display, 'none');
    assert.equal(
      queryAndAssert(pluginEl, '.custom-status').textContent?.trim(),
      `Custom: ${mainChange._number}`
    );

    // Reuse the same row element for an active change on another branch.
    const readyOtherBranch: ChangeInfo = {
      ...change,
      branch: 'release' as BranchName,
      submit_requirements: [
        {
          ...createSubmitRequirementResultInfo(),
          status: SubmitRequirementStatus.SATISFIED,
        },
      ],
    };
    element.change = readyOtherBranch;
    await element.updateComplete;
    await pluginEl.updateComplete;

    assert.strictEqual(pluginEl.change, readyOtherBranch);
    assert.isFalse(defaultContent.hidden);
    assert.notEqual(getComputedStyle(defaultContent).display, 'none');
    assert.isUndefined(query(pluginEl, '.custom-status'));
    assert.include(defaultContent.textContent ?? '', 'Ready');

    // Reuse the same row element back to a matching branch.
    element.change = mainChange;
    await element.updateComplete;
    await pluginEl.updateComplete;

    assert.isTrue(defaultContent.hidden);
    assert.equal(getComputedStyle(defaultContent).display, 'none');
    assert.isDefined(query(pluginEl, '.custom-status'));
  });

  test('plugin replaces active summary and updates reactively across row reuse', async () => {
    let plugin!: PluginApi;
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://some/plugins/summary-replacer/url.js'
    );

    const attached = mockPromise<TestSummaryReplaceElement>();
    const hook = plugin.registerCustomComponent<TestSummaryReplaceElement>(
      'change-list-requirements-summary',
      REPLACER_TAG,
      {replace: true}
    );
    hook.onAttached(el => attached.resolve(el));

    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary .change=${change}>
      </gr-change-list-column-requirements-summary>`
    );

    const pluginEl = await attached;
    await pluginEl.updateComplete;

    assert.isUndefined(query(element, '.requirements-summary-content'));
    assert.strictEqual(pluginEl.change, change);
    assert.equal(
      queryAndAssert(pluginEl, '.replaced-status').textContent?.trim(),
      `Replaced: ${change._number}`
    );

    // Transition through Ready, n/a, and back to unsatisfied on the same row.
    const readyChange: ChangeInfo = {
      ...change,
      _number: 101 as NumericChangeId,
      submit_requirements: [
        {
          ...createSubmitRequirementResultInfo(),
          status: SubmitRequirementStatus.SATISFIED,
        },
      ],
    };
    element.change = readyChange;
    await element.updateComplete;
    await pluginEl.updateComplete;
    assert.strictEqual(pluginEl.change, readyChange);
    assert.equal(
      queryAndAssert(pluginEl, '.replaced-status').textContent?.trim(),
      'Replaced: 101'
    );

    const noReqsChange: ChangeInfo = {
      ...change,
      _number: 102 as NumericChangeId,
      submit_requirements: [],
    };
    element.change = noReqsChange;
    await element.updateComplete;
    await pluginEl.updateComplete;
    assert.strictEqual(pluginEl.change, noReqsChange);
    assert.equal(
      queryAndAssert(pluginEl, '.replaced-status').textContent?.trim(),
      'Replaced: 102'
    );

    element.change = change;
    await element.updateComplete;
    await pluginEl.updateComplete;
    assert.strictEqual(pluginEl.change, change);
  });

  test('detaches and re-attaches endpoint across active and non-active row reuse', async () => {
    let plugin!: PluginApi;
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://some/plugins/summary-lifecycle/url.js'
    );

    let attachedPromise = mockPromise<TestSummaryDecoratorElement>();
    const hook = plugin.registerCustomComponent<TestSummaryDecoratorElement>(
      'change-list-requirements-summary',
      DECORATOR_TAG
    );
    hook.onAttached(el => attachedPromise.resolve(el));

    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary .change=${change}>
      </gr-change-list-column-requirements-summary>`
    );
    await attachedPromise;
    assert.equal(hook.getAllAttached().length, 1);

    // Reuse row for a merged change: endpoint detaches cleanly.
    element.change = {
      ...change,
      status: ChangeStatus.MERGED,
    };
    await element.updateComplete;
    assert.isUndefined(query(element, 'gr-endpoint-decorator'));
    assert.equal(hook.getAllAttached().length, 0);

    // Reuse row for a new active change: endpoint mounts and attaches again.
    attachedPromise = mockPromise<TestSummaryDecoratorElement>();
    const nextActiveChange: ChangeInfo = {
      ...change,
      _number: 202 as NumericChangeId,
      branch: 'main' as BranchName,
    };
    element.change = nextActiveChange;
    await element.updateComplete;
    const reattachedEl = await attachedPromise;
    assert.equal(hook.getAllAttached().length, 1);
    assert.strictEqual(reattachedEl.change, nextActiveChange);
  });
});
