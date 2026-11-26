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

  override willUpdate() {
    const shouldOverride = this.change?.branch === ('main' as BranchName);
    this.toggleAttribute('replace-content', shouldOverride);
  }

  override render() {
    if (this.change?.branch !== ('main' as BranchName)) return nothing;
    return html`<span class="custom-status"
      >Custom: ${this.change._number}</span
    >`;
  }
}

class TestSummarySecondaryDecoratorElement
  extends LitElement
  implements PluginElement
{
  @property({type: Object})
  change?: ChangeInfo;

  plugin!: PluginApi;

  override willUpdate() {
    const shouldOverride =
      this.change?.branch === ('experimental' as BranchName);
    this.toggleAttribute('replace-content', shouldOverride);
  }

  override render() {
    if (this.change?.branch === ('experimental' as BranchName)) {
      return html`<span class="secondary-status"
        >Secondary: ${this.change._number}</span
      >`;
    }
    return html`<span class="secondary-badge">Badge</span>`;
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
const SECONDARY_DECORATOR_TAG = 'test-requirements-summary-secondary-decorator';
const REPLACER_TAG = 'test-requirements-summary-replacer';
if (!customElements.get(DECORATOR_TAG)) {
  customElements.define(DECORATOR_TAG, TestSummaryDecoratorElement);
}
if (!customElements.get(SECONDARY_DECORATOR_TAG)) {
  customElements.define(
    SECONDARY_DECORATOR_TAG,
    TestSummarySecondaryDecoratorElement
  );
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
    const nonActiveChanges: ChangeInfo[] = [
      {...change, status: ChangeStatus.MERGED},
      {...change, work_in_progress: true},
      {...change, revert_of: 123 as NumericChangeId},
      {...change, mergeable: false},
    ];
    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary
        .change=${nonActiveChanges[0]}
      >
      </gr-change-list-column-requirements-summary>`
    );
    for (const nonActive of nonActiveChanges) {
      element.change = nonActive;
      await element.updateComplete;
      assert.isUndefined(query(element, 'gr-endpoint-decorator'));
      assert.isDefined(query(element, 'gr-change-status'));
    }
  });

  test('plugin decorates and conditionally hides default summary via replace-content across row reuse', async () => {
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
      unresolved_comment_count: 3,
    };
    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary .change=${mainChange}>
      </gr-change-list-column-requirements-summary>`
    );

    const pluginEl = await attached;
    await pluginEl.updateComplete;

    const decorator = queryAndAssert(element, 'gr-endpoint-decorator');
    const defaultContent = queryAndAssert<HTMLElement>(
      element,
      '.requirements-summary-content'
    );
    const commentIcon = queryAndAssert<HTMLElement>(
      element,
      'gr-icon.commentIcon'
    );
    assert.isFalse(decorator.contains(commentIcon));
    assert.notEqual(getComputedStyle(commentIcon).display, 'none');
    assert.isNotNull(
      defaultContent.querySelector('gr-submit-requirement-dashboard-hovercard')
    );
    assert.strictEqual(pluginEl.change, mainChange);
    assert.isTrue(pluginEl.hasAttribute('replace-content'));
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
    assert.isFalse(pluginEl.hasAttribute('replace-content'));
    assert.notEqual(getComputedStyle(defaultContent).display, 'none');
    assert.isUndefined(query(pluginEl, '.custom-status'));
    assert.include(defaultContent.textContent ?? '', 'Ready');

    // Reuse the same row element for an active change with no requirements (n/a).
    const noReqsOtherBranch: ChangeInfo = {
      ...change,
      branch: 'release' as BranchName,
      submit_requirements: [],
    };
    element.change = noReqsOtherBranch;
    await element.updateComplete;
    await pluginEl.updateComplete;

    assert.isFalse(pluginEl.hasAttribute('replace-content'));
    assert.notEqual(getComputedStyle(defaultContent).display, 'none');
    assert.equal(defaultContent.textContent?.trim(), 'n/a');

    const noReqsMain: ChangeInfo = {
      ...noReqsOtherBranch,
      branch: 'main' as BranchName,
    };
    element.change = noReqsMain;
    await element.updateComplete;
    await pluginEl.updateComplete;

    assert.isTrue(pluginEl.hasAttribute('replace-content'));
    assert.equal(getComputedStyle(defaultContent).display, 'none');
    assert.isDefined(query(pluginEl, '.custom-status'));
  });

  test('multiple decorator plugins compose replace-content without clobbering', async () => {
    let pluginA!: PluginApi;
    let pluginB!: PluginApi;
    window.Gerrit.install(
      p => {
        pluginA = p;
      },
      '0.1',
      'http://some/plugins/summary-decorator-a/url.js'
    );
    window.Gerrit.install(
      p => {
        pluginB = p;
      },
      '0.1',
      'http://some/plugins/summary-decorator-b/url.js'
    );

    const attachedA = mockPromise<TestSummaryDecoratorElement>();
    const attachedB = mockPromise<TestSummarySecondaryDecoratorElement>();
    pluginA
      .registerCustomComponent<TestSummaryDecoratorElement>(
        'change-list-requirements-summary',
        DECORATOR_TAG
      )
      .onAttached(el => attachedA.resolve(el));
    pluginB
      .registerCustomComponent<TestSummarySecondaryDecoratorElement>(
        'change-list-requirements-summary',
        SECONDARY_DECORATOR_TAG
      )
      .onAttached(el => attachedB.resolve(el));

    const mainChange: ChangeInfo = {
      ...change,
      branch: 'main' as BranchName,
    };
    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary .change=${mainChange}>
      </gr-change-list-column-requirements-summary>`
    );

    const elA = await attachedA;
    const elB = await attachedB;
    await elA.updateComplete;
    await elB.updateComplete;

    const defaultContent = queryAndAssert<HTMLElement>(
      element,
      '.requirements-summary-content'
    );
    // Plugin A sets replace-content while Plugin B does not; default stays hidden.
    assert.isTrue(elA.hasAttribute('replace-content'));
    assert.isFalse(elB.hasAttribute('replace-content'));
    assert.equal(getComputedStyle(defaultContent).display, 'none');

    // Switch to a branch where only Plugin B sets replace-content.
    element.change = {
      ...change,
      branch: 'experimental' as BranchName,
    };
    await element.updateComplete;
    await elA.updateComplete;
    await elB.updateComplete;

    assert.isFalse(elA.hasAttribute('replace-content'));
    assert.isTrue(elB.hasAttribute('replace-content'));
    assert.equal(getComputedStyle(defaultContent).display, 'none');

    // Switch to a branch where neither plugin sets replace-content.
    element.change = {
      ...change,
      branch: 'release' as BranchName,
    };
    await element.updateComplete;
    await elA.updateComplete;
    await elB.updateComplete;

    assert.isFalse(elA.hasAttribute('replace-content'));
    assert.isFalse(elB.hasAttribute('replace-content'));
    assert.notEqual(getComputedStyle(defaultContent).display, 'none');
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

    const changeWithComments: ChangeInfo = {
      ...change,
      unresolved_comment_count: 2,
    };
    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary
        .change=${changeWithComments}
      >
      </gr-change-list-column-requirements-summary>`
    );

    const pluginEl = await attached;
    await pluginEl.updateComplete;

    const decorator = queryAndAssert(element, 'gr-endpoint-decorator');
    const commentIcon = queryAndAssert<HTMLElement>(
      element,
      'gr-icon.commentIcon'
    );
    assert.isFalse(decorator.contains(commentIcon));
    assert.notEqual(getComputedStyle(commentIcon).display, 'none');
    assert.isUndefined(query(element, '.requirements-summary-content'));
    assert.strictEqual(pluginEl.change, changeWithComments);
    assert.equal(
      queryAndAssert(pluginEl, '.replaced-status').textContent?.trim(),
      `Replaced: ${changeWithComments._number}`
    );

    // Transition through Ready, n/a, and back to unsatisfied on the same row.
    const readyChange: ChangeInfo = {
      ...changeWithComments,
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
      ...changeWithComments,
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

    element.change = changeWithComments;
    await element.updateComplete;
    await pluginEl.updateComplete;
    assert.strictEqual(pluginEl.change, changeWithComments);
  });

  test('mounts, detaches, and re-attaches endpoint across non-active and active row reuse', async () => {
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

    // Start on a non-active change: endpoint is not mounted.
    element = await fixture<GrChangeListColumnRequirementsSummary>(
      html`<gr-change-list-column-requirements-summary
        .change=${{...change, status: ChangeStatus.MERGED}}
      >
      </gr-change-list-column-requirements-summary>`
    );
    assert.isUndefined(query(element, 'gr-endpoint-decorator'));
    assert.equal(hook.getAllAttached().length, 0);

    // Transition non-active -> active: endpoint mounts and attaches cleanly.
    const firstActiveChange: ChangeInfo = {
      ...change,
      _number: 201 as NumericChangeId,
      branch: 'main' as BranchName,
    };
    element.change = firstActiveChange;
    await element.updateComplete;
    const firstEl = await attachedPromise;
    await firstEl.updateComplete;
    assert.equal(hook.getAllAttached().length, 1);
    assert.strictEqual(firstEl.change, firstActiveChange);
    assert.equal(
      getComputedStyle(
        queryAndAssert<HTMLElement>(element, '.requirements-summary-content')
      ).display,
      'none'
    );

    // Transition active -> non-active (WIP): endpoint detaches cleanly.
    element.change = {
      ...change,
      work_in_progress: true,
    };
    await element.updateComplete;
    assert.isUndefined(query(element, 'gr-endpoint-decorator'));
    assert.equal(hook.getAllAttached().length, 0);

    // Transition non-active -> active again: endpoint mounts and attaches again.
    attachedPromise = mockPromise<TestSummaryDecoratorElement>();
    const nextActiveChange: ChangeInfo = {
      ...change,
      _number: 202 as NumericChangeId,
      branch: 'main' as BranchName,
    };
    element.change = nextActiveChange;
    await element.updateComplete;
    const reattachedEl = await attachedPromise;
    await reattachedEl.updateComplete;
    assert.equal(hook.getAllAttached().length, 1);
    assert.strictEqual(reattachedEl.change, nextActiveChange);
    assert.isTrue(reattachedEl.hasAttribute('replace-content'));
    assert.equal(
      getComputedStyle(
        queryAndAssert<HTMLElement>(element, '.requirements-summary-content')
      ).display,
      'none'
    );
  });
});
