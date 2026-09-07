/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {
  createChange,
  createChangeConfig,
  createParsedChange,
  createRevision,
  createServerInfo,
  createThread,
} from '../../../test/test-data-generators';
import {EDIT, RepoName, TopicName} from '../../../types/common';
import {GrConfirmSubmitDialog} from './gr-confirm-submit-dialog';
import './gr-confirm-submit-dialog';
import {configModelToken} from '../../../models/config/config-model';
import {assert, fixture, html} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {relatedChangesModelToken} from '../../../models/change/related-changes-model';

suite('gr-confirm-submit-dialog tests', () => {
  let element: GrConfirmSubmitDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-confirm-submit-dialog></gr-confirm-submit-dialog>`
    );
    element.initialised = true;
  });

  test('render', async () => {
    element.action = {label: 'my-label'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      revisions: {},
    };
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-dialog
          cancel-label=""
          confirm-label=""
          confirm-on-enter=""
          id="dialog"
          role="dialog"
        >
          <div class="header" slot="header">my-label</div>
          <div class="main" slot="main">
            <gr-endpoint-decorator name="confirm-submit-change">
              <p>
                Ready to submit "
                <strong> my-subject </strong>
                "?
              </p>
              <gr-endpoint-param name="change"> </gr-endpoint-param>
              <gr-endpoint-param name="action"> </gr-endpoint-param>
            </gr-endpoint-decorator>
          </div>
          <div class="footer-buttons" slot="footer">
            <div class="footer-space"></div>
            <gr-button aria-disabled="false" link="" role="button" tabindex="0">
              Cancel
            </gr-button>
            <gr-button
              aria-disabled="false"
              primary=""
              role="button"
              tabindex="0"
            >
              Submit
            </gr-button>
          </div>
        </gr-dialog>
      `
    );
  });

  test('computeUnresolvedCommentsWarning', () => {
    element.change = {...createParsedChange()};
    element.unresolvedThreads = [createThread()];
    assert.equal(
      element.computeUnresolvedCommentsWarning(),
      'Heads Up! 1 unresolved comment.'
    );

    element.unresolvedThreads = [...element.unresolvedThreads, createThread()];
    assert.equal(
      element.computeUnresolvedCommentsWarning(),
      'Heads Up! 2 unresolved comments.'
    );
  });

  test('renders topic dashboard link when change has a topic', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    const link = element.shadowRoot!.querySelector(
      'a.topicDashboardLink'
    ) as HTMLAnchorElement;
    assert.isNotNull(link);
    assert.include(link.textContent, 'my-topic');
    assert.equal(link.target, '_blank');
    assert.include(link.href, 'my-topic');
  });

  test('does not render topic section when change has no topic', async () => {
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      revisions: {},
    };
    await element.updateComplete;

    assert.isNull(element.shadowRoot!.querySelector('a.topicDashboardLink'));
  });

  test('warning shown when topic has unsubmittable changes', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: false,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    const warning = element.shadowRoot!.querySelector('.warningBox');
    assert.isNotNull(warning);
    assert.include(
      warning.textContent,
      'Some changes in this topic are not ready to submit.'
    );
  });

  test('no warning shown when all topic changes are submittable', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: true,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    assert.isNull(element.shadowRoot!.querySelector('.warningBox'));
  });

  test('Submit Whole Topic button present when topic has changes', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: true,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    const btns = element.shadowRoot!.querySelectorAll(
      '.footer-buttons gr-button[primary]'
    );
    assert.equal(btns.length, 2);
    const wholeTopicBtn = btns[1] as HTMLElement;
    assert.include(wholeTopicBtn.textContent, 'Submit Whole Topic');
    assert.isFalse(wholeTopicBtn.hasAttribute('disabled'));
  });

  test('Submit Whole Topic button disabled when topic has unsubmittable changes', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: false,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    const btns = element.shadowRoot!.querySelectorAll(
      '.footer-buttons gr-button[primary]'
    );
    assert.equal(btns.length, 2);
    const wholeTopicBtn = btns[1] as HTMLElement;
    assert.isTrue(wholeTopicBtn.hasAttribute('disabled'));
  });

  test('Submit Whole Topic button absent when change has no topic', async () => {
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      revisions: {},
    };
    await element.updateComplete;

    const btns = element.shadowRoot!.querySelectorAll(
      '.footer-buttons gr-button[primary]'
    );
    assert.equal(btns.length, 1);
    assert.include(btns[0].textContent, 'Submit');
    assert.notInclude(btns[0].textContent, 'Whole');
  });

  test('Submit Whole Topic button fires confirm with submitWholeTopic=true', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: true,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    let detail: {submitWholeTopic: boolean} | undefined;
    element.addEventListener('confirm', (e: Event) => {
      detail = (e as CustomEvent).detail;
    });

    const btns = element.shadowRoot!.querySelectorAll(
      '.footer-buttons gr-button[primary]'
    );
    (btns[1] as HTMLElement).click();

    assert.isTrue(detail?.submitWholeTopic);
  });

  test('computeHasChangeEdit', () => {
    element.change = {
      ...createParsedChange(),
      revisions: {
        d442ff05d6c4f2a3af0eeca1f67374b39f9dc3d8: createRevision(EDIT),
      },
      unresolved_comment_count: 0,
    };

    assert.isTrue(element.computeHasChangeEdit());

    element.change = {
      ...createParsedChange(),
      revisions: {
        d442ff05d6c4f2a3af0eeca1f67374b39f9dc3d8: createRevision(2),
      },
    };
    assert.isFalse(element.computeHasChangeEdit());
  });

  suite('whole topic enforced by server config', () => {
    setup(() => {
      const configModel = testResolver(configModelToken);
      configModel.updateState({
        serverConfig: {
          ...createServerInfo(),
          change: {...createChangeConfig(), submit_whole_topic: true},
        },
      });
    });

    test('only Submit Whole Topic button shown when enforced', async () => {
      const relatedChangesModel = testResolver(relatedChangesModelToken);
      relatedChangesModel.updateState({
        sameTopicChanges: [
          {
            ...createChange(),
            subject: 'Change A',
            project: 'repo/a' as RepoName,
            submittable: true,
          },
        ],
      });
      element.action = {label: 'Submit'};
      element.change = {
        ...createParsedChange(),
        subject: 'my-subject',
        topic: 'my-topic' as TopicName,
        revisions: {},
      };
      await element.updateComplete;

      const btns = element.shadowRoot!.querySelectorAll(
        '.footer-buttons gr-button[primary]'
      );
      assert.equal(btns.length, 1);
      assert.include(btns[0].textContent, 'Submit Whole Topic');
    });

    test('Submit Whole Topic fires confirm with submitWholeTopic=true when enforced', async () => {
      const relatedChangesModel = testResolver(relatedChangesModelToken);
      relatedChangesModel.updateState({
        sameTopicChanges: [
          {
            ...createChange(),
            subject: 'Change A',
            project: 'repo/a' as RepoName,
            submittable: true,
          },
        ],
      });
      element.action = {label: 'Submit'};
      element.change = {
        ...createParsedChange(),
        subject: 'my-subject',
        topic: 'my-topic' as TopicName,
        revisions: {},
      };
      await element.updateComplete;

      let detail: {submitWholeTopic: boolean} | undefined;
      element.addEventListener('confirm', (e: Event) => {
        detail = (e as CustomEvent).detail;
      });
      const btn = element.shadowRoot!.querySelector<HTMLElement>(
        '.footer-buttons gr-button[primary]'
      )!;
      btn.click();

      assert.isTrue(detail?.submitWholeTopic);
    });
  });
});
