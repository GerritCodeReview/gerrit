/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma';
import './gr-change-metadata';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {ChangeRole, GrChangeMetadata} from './gr-change-metadata';
import {
  createServerInfo,
  createUserConfig,
  createParsedChange,
  createAccountWithId,
  createCommitInfoWithRequiredCommit,
  createWebLinkInfo,
  createGerritInfo,
  createGitPerson,
  createCommit,
  createRevision,
  createAccountDetailWithId,
  createConfig,
} from '../../../test/test-data-generators';
import {
  ChangeStatus,
  SubmitType,
  GpgKeyInfoStatus,
  InheritedBooleanInfoConfiguredValue,
} from '../../../constants/constants';
import {
  EmailAddress,
  AccountId,
  CommitId,
  ServerInfo,
  RevisionInfo,
  ParentCommitInfo,
  TopicName,
  PatchSetNum,
  NumericChangeId,
  LabelValueToDescriptionMap,
  Hashtag,
  CommitInfo,
} from '../../../types/common';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {GrEditableLabel} from '../../shared/gr-editable-label/gr-editable-label';
import {PluginApi} from '../../../api/plugin';
import {GrEndpointDecorator} from '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {
  queryAndAssert,
  resetPlugins,
  stubRestApi,
} from '../../../test/test-utils';
import {ParsedChangeInfo} from '../../../types/types';
import {GrLinkedChip} from '../../shared/gr-linked-chip/gr-linked-chip';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrRouter} from '../../core/gr-router/gr-router';
import {nothing} from 'lit';

const basicFixture = fixtureFromElement('gr-change-metadata');

suite('gr-change-metadata tests', () => {
  let element: GrChangeMetadata;

  setup(async () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getConfig').returns(
      Promise.resolve({
        ...createServerInfo(),
        user: {
          ...createUserConfig(),
          anonymouscowardname: 'test coward name',
        },
      })
    );
    element = basicFixture.instantiate();
    element.change = createParsedChange();
    await element.updateComplete;
  });

  test('renders', async () => {
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `<div>
      <div class="metadata-header">
        <h3 class="heading-3 metadata-title">Change Info</h3>
        <gr-button
          class="show-all-button"
          link=""
          role="button"
          tabindex="0"
          aria-disabled="false"
        >
          Show all <iron-icon icon="gr-icons:expand-more"> </iron-icon>
          <iron-icon hidden="" icon="gr-icons:expand-less"> </iron-icon>
        </gr-button>
      </div>
      <section class="hideDisplay">
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="Last update of (meta)data for this change."
          >
            Updated
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-date-formatter showyesterday="" withtooltip="">
          </gr-date-formatter>
        </span>
      </section>
      <section>
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="This user created or uploaded the first patchset of this change."
          >
            Owner
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-account-chip highlightattention=""
            ><gr-vote-chip circle-shape="" slot="vote-chip"> </gr-vote-chip
          ></gr-account-chip>
        </span>
      </section>
      <section>
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="This user wrote the code change."
          >
            Author
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-account-chip><gr-vote-chip circle-shape="" slot="vote-chip"></gr-vote-chip
          ></gr-account-chip>
        </span>
      </section>
      <section>
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="This user committed the code change to the Git repository (typically to the local Git repo before uploading)."
          >
            Committer
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-account-chip><gr-vote-chip circle-shape="" slot="vote-chip"></gr-account-chip>
        </span>
      </section>
      <section>
        <span class="title"> Reviewers </span>
        <span class="value">
          <gr-reviewer-list reviewers-only=""> </gr-reviewer-list>
        </span>
      </section>
      <section class="hideDisplay">
        <span class="title"> CC </span>
        <span class="value">
          <gr-reviewer-list ccs-only=""> </gr-reviewer-list>
        </span>
      </section>
      <section>
          <span class="title">
            Repo | Branch
          </span>
          <span class="value">
            <a href="">
              test-project
            </a>
            |
            <a href="">
              test-branch
            </a>
          </span>
        </section>
      <section class="hideDisplay">
        <span class="title">Parent</span>
        <span class="value">
          <ol  class="nonMerge notCurrent parentList"></ol>
        </span>
      </section>
      <section class="hideDisplay strategy">
        <span class="title"> Strategy </span> <span class="value"> </span>
      </section>
      <section class="hashtag hideDisplay">
        <span class="title"> Hashtags </span>
        <span class="value"> </span>
      </section>
      <div class="separatedSection">
      <gr-submit-requirements></gr-submit-requirements>
      </div>
      <gr-endpoint-decorator name="change-metadata-item">
        <gr-endpoint-param name="labels"> </gr-endpoint-param>
        <gr-endpoint-param name="change"> </gr-endpoint-param>
        <gr-endpoint-param name="revision"> </gr-endpoint-param>
      </gr-endpoint-decorator>
    </div>`);
  });

  test('computeMergedCommitInfo', () => {
    const dummyRevs: {[revisionId: string]: RevisionInfo} = {
      1: createRevision(1),
      2: createRevision(2),
    };
    assert.deepEqual(
      element.computeMergedCommitInfo('0' as CommitId, dummyRevs),
      undefined
    );
    assert.deepEqual(
      element.computeMergedCommitInfo('1' as CommitId, dummyRevs),
      dummyRevs[1].commit
    );

    // Regression test for issue 5337.
    const commit = element.computeMergedCommitInfo('2' as CommitId, dummyRevs);
    assert.notDeepEqual(commit, dummyRevs[2] as unknown as CommitInfo);
    assert.deepEqual(commit, dummyRevs[2].commit);
  });

  test('show strategy for open change', async () => {
    element.change = {
      ...createParsedChange(),
      status: ChangeStatus.NEW,
      submit_type: SubmitType.CHERRY_PICK,
      labels: {},
    };
    await element.updateComplete;
    const strategy = element.shadowRoot?.querySelector('.strategy');
    assert.ok(strategy);
    assert.isFalse(strategy?.hasAttribute('hidden'));
    assert.equal(strategy?.children[1].textContent, 'Cherry Pick');
  });

  test('hide strategy for closed change', async () => {
    element.change = {
      ...createParsedChange(),
      status: ChangeStatus.MERGED,
      labels: {},
    };
    await element.updateComplete;
    assert.isNull(element.shadowRoot?.querySelector('.strategy'));
  });

  test('weblinks use GerritNav interface', async () => {
    const weblinksStub = sinon
      .stub(GerritNav, '_generateWeblinks')
      .returns([{name: 'stubb', url: '#s'}]);
    element.commitInfo = createCommitInfoWithRequiredCommit();
    element.serverConfig = createServerInfo();
    await element.updateComplete;
    const webLinks = element.webLinks!;
    assert.isTrue(weblinksStub.called);
    assert.isNotNull(webLinks);
    assert.equal(element.computeWebLinks().length, 1);
  });

  test('weblinks hidden when no weblinks', async () => {
    element.commitInfo = createCommitInfoWithRequiredCommit();
    element.serverConfig = createServerInfo();
    await element.updateComplete;
    assert.isNull(element.webLinks);
  });

  test('weblinks hidden when only gitiles weblink', async () => {
    element.commitInfo = {
      ...createCommitInfoWithRequiredCommit(),
      web_links: [{...createWebLinkInfo(), name: 'gitiles', url: '#'}],
    };
    element.serverConfig = createServerInfo();
    await element.updateComplete;
    assert.isNull(element.webLinks);
    assert.equal(element.computeWebLinks().length, 0);
  });

  test('weblinks hidden when sole weblink is set as primary', async () => {
    const browser = 'browser';
    element.commitInfo = {
      ...createCommitInfoWithRequiredCommit(),
      web_links: [{...createWebLinkInfo(), name: browser, url: '#'}],
    };
    element.serverConfig = {
      ...createServerInfo(),
      gerrit: {
        ...createGerritInfo(),
        primary_weblink_name: browser,
      },
    };
    await element.updateComplete;
    assert.isNull(element.webLinks);
  });

  test('weblinks are visible when other weblinks', async () => {
    const router = new GrRouter();
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router.generateWeblinks.bind(router));

    element.commitInfo = {
      ...createCommitInfoWithRequiredCommit(),
      web_links: [{...createWebLinkInfo(), name: 'test', url: '#'}],
    };
    await element.updateComplete;
    const webLinks = element.webLinks!;
    assert.isFalse(webLinks.hasAttribute('hidden'));
    assert.equal(element.computeWebLinks().length, 1);
    // With two non-gitiles weblinks, there are two returned.
    element.commitInfo = {
      ...createCommitInfoWithRequiredCommit(),
      web_links: [
        {...createWebLinkInfo(), name: 'test', url: '#'},
        {...createWebLinkInfo(), name: 'test2', url: '#'},
      ],
    };
    assert.equal(element.computeWebLinks().length, 2);
  });

  test('weblinks are visible when gitiles and other weblinks', async () => {
    const router = new GrRouter();
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router.generateWeblinks.bind(router));

    element.commitInfo = {
      ...createCommitInfoWithRequiredCommit(),
      web_links: [
        {...createWebLinkInfo(), name: 'test', url: '#'},
        {...createWebLinkInfo(), name: 'gitiles', url: '#'},
      ],
    };
    await element.updateComplete;
    const webLinks = element.webLinks!;
    assert.isFalse(webLinks.hasAttribute('hidden'));
    // Only the non-gitiles weblink is returned.
    assert.equal(element.computeWebLinks().length, 1);
  });

  suite('getNonOwnerRole', () => {
    let change: ParsedChangeInfo | undefined;

    setup(() => {
      change = {
        ...createParsedChange(),
        owner: {
          ...createAccountWithId(),
          email: 'abc@def' as EmailAddress,
          _account_id: 1019328 as AccountId,
        },
        revisions: {
          rev1: {
            ...createRevision(),
            uploader: {
              ...createAccountWithId(),
              email: 'ghi@def' as EmailAddress,
              _account_id: 1011123 as AccountId,
            },
            commit: {
              ...createCommit(),
              author: {...createGitPerson(), email: 'jkl@def' as EmailAddress},
              committer: {
                ...createGitPerson(),
                email: 'ghi@def' as EmailAddress,
              },
            },
          },
        },
        current_revision: 'rev1' as CommitId,
      };
    });

    suite('role=uploader', () => {
      test('getNonOwnerRole for uploader', () => {
        element.change = change;
        assert.deepEqual(element.getNonOwnerRole(ChangeRole.UPLOADER), {
          ...createAccountWithId(),
          email: 'ghi@def' as EmailAddress,
          _account_id: 1011123 as AccountId,
        });
      });

      test('getNonOwnerRole that it does not return uploader', () => {
        // Set the uploader email to be the same as the owner.
        change!.revisions.rev1.uploader!._account_id = 1019328 as AccountId;
        element.change = change;
        assert.isNotOk(element.getNonOwnerRole(ChangeRole.UPLOADER));
      });

      test('computeShowRoleClass show uploader', () => {
        element.change = change;
        assert.notEqual(element.renderNonOwner(ChangeRole.UPLOADER), nothing);
      });

      test('computeShowRoleClass hide uploader', () => {
        // Set the uploader email to be the same as the owner.
        change!.revisions.rev1.uploader!._account_id = 1019328 as AccountId;
        element.change = change;
        assert.equal(element.renderNonOwner(ChangeRole.UPLOADER), nothing);
      });
    });

    suite('role=committer', () => {
      test('getNonOwnerRole for committer', () => {
        change!.revisions.rev1.uploader!.email = 'ghh@def' as EmailAddress;
        element.change = change;
        assert.deepEqual(element.getNonOwnerRole(ChangeRole.COMMITTER), {
          ...createGitPerson(),
          email: 'ghi@def' as EmailAddress,
        });
      });

      test('getNonOwnerRole is null if committer is same as uploader', () => {
        element.change = change;
        assert.isNotOk(element.getNonOwnerRole(ChangeRole.COMMITTER));
      });

      test('getNonOwnerRole that it does not return committer', () => {
        // Set the committer email to be the same as the owner.
        change!.revisions.rev1.commit!.committer.email =
          'abc@def' as EmailAddress;
        element.change = change;
        assert.isNotOk(element.getNonOwnerRole(ChangeRole.COMMITTER));
      });

      test('getNonOwnerRole null for committer with no commit', () => {
        delete change!.revisions.rev1.commit;
        element.change = change;
        assert.isNotOk(element.getNonOwnerRole(ChangeRole.COMMITTER));
      });
    });

    suite('role=author', () => {
      test('getNonOwnerRole for author', () => {
        element.change = change;
        assert.deepEqual(element.getNonOwnerRole(ChangeRole.AUTHOR), {
          ...createGitPerson(),
          email: 'jkl@def' as EmailAddress,
        });
      });

      test('getNonOwnerRole that it does not return author', () => {
        // Set the author email to be the same as the owner.
        change!.revisions.rev1.commit!.author.email = 'abc@def' as EmailAddress;
        element.change = change;
        assert.isNotOk(element.getNonOwnerRole(ChangeRole.AUTHOR));
      });

      test('getNonOwnerRole null for author with no commit', () => {
        delete change!.revisions.rev1.commit;
        element.change = change;
        assert.isNotOk(element.getNonOwnerRole(ChangeRole.AUTHOR));
      });
    });
  });

  suite('Push Certificate Validation', () => {
    let serverConfig: ServerInfo | undefined;
    let change: ParsedChangeInfo | undefined;

    setup(() => {
      serverConfig = {
        ...createServerInfo(),
        receive: {
          enable_signed_push: 'true',
        },
      };
      change = {
        ...createParsedChange(),
        revisions: {
          rev1: {
            ...createRevision(1),
            push_certificate: {
              certificate: 'Push certificate',
              key: {
                status: GpgKeyInfoStatus.BAD,
                problems: ['No public keys found for key ID E5E20E52'],
              },
            },
          },
        },
        current_revision: 'rev1' as CommitId,
        status: ChangeStatus.NEW,
        labels: {},
        mergeable: true,
      };
      element.repoConfig = {
        ...createConfig(),
        enable_signed_push: {
          configured_value: 'TRUE' as InheritedBooleanInfoConfiguredValue,
          value: true,
        },
      };
    });

    test('Push Certificate Validation test BAD', () => {
      change!.revisions.rev1!.push_certificate = {
        certificate: 'Push certificate',
        key: {
          status: GpgKeyInfoStatus.BAD,
          problems: ['No public keys found for key ID E5E20E52'],
        },
      };
      element.change = change;
      element.serverConfig = serverConfig;
      const result = element.computePushCertificateValidation();
      assert.equal(
        result?.message,
        'Push certificate is invalid:\n' +
          'No public keys found for key ID E5E20E52'
      );
      assert.equal(result?.icon, 'gr-icons:close');
      assert.equal(result?.class, 'invalid');
    });

    test('Push Certificate Validation test TRUSTED', () => {
      change!.revisions.rev1!.push_certificate = {
        certificate: 'Push certificate',
        key: {
          status: GpgKeyInfoStatus.TRUSTED,
        },
      };
      element.change = change;
      element.serverConfig = serverConfig;
      const result = element.computePushCertificateValidation();
      assert.equal(
        result?.message,
        'Push certificate is valid and key is trusted'
      );
      assert.equal(result?.icon, 'gr-icons:check');
      assert.equal(result?.class, 'trusted');
    });

    test('Push Certificate Validation is missing test', () => {
      change!.revisions.rev1 = createRevision(1);
      element.change = change;
      element.serverConfig = serverConfig;
      const result = element.computePushCertificateValidation();
      assert.equal(
        result?.message,
        'This patch set was created without a push certificate'
      );
      assert.equal(result?.icon, 'gr-icons:help');
      assert.equal(result?.class, 'help');
    });

    test('computePushCertificateValidation returns undefined', () => {
      element.change = change;
      delete serverConfig!.receive!.enable_signed_push;
      element.serverConfig = serverConfig;
      assert.isUndefined(element.computePushCertificateValidation());

      serverConfig!.receive!.enable_signed_push = 'true';
      element.serverConfig = serverConfig;
      element.repoConfig?.enable_signed_push?.inherited_value;
    });

    test('getEnabledSignedPushRepoConifg', () => {
      change!.revisions.rev1!.push_certificate = {
        certificate: 'Push certificate',
        key: {
          status: GpgKeyInfoStatus.TRUSTED,
        },
      };
      element.change = change;
      element.serverConfig = serverConfig;
      element.repoConfig!.enable_signed_push!.configured_value =
        'INHERIT' as InheritedBooleanInfoConfiguredValue;
      element.repoConfig!.enable_signed_push!.inherited_value = true;
      assert.isTrue(element.getEnabledSignedPushRepoConifg());
      element.repoConfig!.enable_signed_push!.inherited_value = false;
      assert.isFalse(element.getEnabledSignedPushRepoConifg());
      element.repoConfig!.enable_signed_push!.configured_value =
        InheritedBooleanInfoConfiguredValue.TRUE;
      assert.isTrue(element.getEnabledSignedPushRepoConifg());
      element.repoConfig = undefined;
      assert.isFalse(element.getEnabledSignedPushRepoConifg());
    });
  });

  test('computeParents', () => {
    const parents: ParentCommitInfo[] = [
      {...createCommit(), commit: '123' as CommitId, subject: 'abc'},
    ];
    const revision: RevisionInfo = {
      ...createRevision(1),
      commit: {...createCommit(), parents},
    };
    element.change = undefined;
    element.revision = revision;
    assert.equal(element.computeParents(), parents);
    const change = (current_revision: CommitId): ParsedChangeInfo => {
      return {
        ...createParsedChange(),
        current_revision,
        revisions: {456: revision},
      };
    };
    const changebadrevision = change('789' as CommitId);
    element.change = changebadrevision;
    element.revision = createRevision();
    assert.deepEqual(element.computeParents(), []);
    const changenocommit: ParsedChangeInfo = {
      ...createParsedChange(),
      current_revision: '456' as CommitId,
      revisions: {456: createRevision()},
    };
    element.change = changenocommit;
    element.revision = undefined;
    assert.deepEqual(element.computeParents(), []);
    const changegood = change('456' as CommitId);
    element.change = changegood;
    element.revision = undefined;
    assert.equal(element.computeParents(), parents);
  });

  test('currentParents', async () => {
    const revision = (parent: CommitId): RevisionInfo => {
      return {
        ...createRevision(),
        commit: {
          ...createCommit(),
          parents: [{...createCommit(), commit: parent, subject: 'abc'}],
        },
      };
    };
    element.change = {
      ...createParsedChange(),
      current_revision: '456' as CommitId,
      revisions: {456: revision('111' as CommitId)},
      owner: {},
    };
    element.revision = revision('222' as CommitId);
    await element.updateComplete;
    assert.equal(element.currentParents[0].commit, '222');
    element.revision = revision('333' as CommitId);
    await element.updateComplete;
    assert.equal(element.currentParents[0].commit, '333');
    element.revision = undefined;
    await element.updateComplete;
    assert.equal(element.currentParents[0].commit, '111');
    element.change = createParsedChange();
    await element.updateComplete;
    assert.deepEqual(element.currentParents, []);
  });

  test('computeParentListClass', () => {
    const parent: ParentCommitInfo = {
      ...createCommit(),
      commit: 'abc123' as CommitId,
      subject: 'My parent commit',
    };
    element.currentParents = [parent];
    element.parentIsCurrent = true;
    assert.equal(
      element.computeParentListClass(),
      'parentList nonMerge current'
    );
    element.currentParents = [parent];
    element.parentIsCurrent = false;
    assert.equal(
      element.computeParentListClass(),
      'parentList nonMerge notCurrent'
    );
    element.currentParents = [parent, parent];
    element.parentIsCurrent = false;
    assert.equal(
      element.computeParentListClass(),
      'parentList merge notCurrent'
    );
    element.currentParents = [parent, parent];
    element.parentIsCurrent = true;
    assert.equal(element.computeParentListClass(), 'parentList merge current');
  });

  test('showAddTopic', () => {
    const change = createParsedChange();
    element.change = undefined;
    element.settingTopic = false;
    element.topicReadOnly = false;
    assert.isTrue(element.showAddTopic());
    // do not show for 'readonly'
    element.change = undefined;
    element.settingTopic = false;
    element.topicReadOnly = true;
    assert.isFalse(element.showAddTopic());
    element.change = change;
    element.settingTopic = false;
    element.topicReadOnly = false;
    assert.isTrue(element.showAddTopic());
    element.change = change;
    element.settingTopic = true;
    element.topicReadOnly = false;
    assert.isFalse(element.showAddTopic());
    change.topic = 'foo' as TopicName;
    element.change = change;
    element.settingTopic = true;
    element.topicReadOnly = false;
    assert.isFalse(element.showAddTopic());
    element.change = change;
    element.settingTopic = false;
    element.topicReadOnly = false;
    assert.isFalse(element.showAddTopic());
  });

  test('showTopicChip', async () => {
    const change = createParsedChange();
    element.change = change;
    element.settingTopic = true;
    await element.updateComplete;
    assert.isFalse(element.showTopicChip());
    element.change = change;
    element.settingTopic = false;
    await element.updateComplete;
    assert.isFalse(element.showTopicChip());
    element.change = change;
    element.settingTopic = true;
    await element.updateComplete;
    assert.isFalse(element.showTopicChip());
    change.topic = 'foo' as TopicName;
    element.change = change;
    element.settingTopic = true;
    await element.updateComplete;
    assert.isFalse(element.showTopicChip());
    element.change = change;
    element.settingTopic = false;
    await element.updateComplete;
    assert.isTrue(element.showTopicChip());
  });

  test('showCherryPickOf', async () => {
    element.change = undefined;
    await element.updateComplete;
    assert.isFalse(element.showCherryPickOf());
    const change = createParsedChange();
    element.change = change;
    await element.updateComplete;
    assert.isFalse(element.showCherryPickOf());
    change.cherry_pick_of_change = 123 as NumericChangeId;
    change.cherry_pick_of_patch_set = 1 as PatchSetNum;
    element.change = change;
    await element.updateComplete;
    assert.isTrue(element.showCherryPickOf());
  });

  suite('Topic removal', () => {
    let change: ParsedChangeInfo;
    setup(() => {
      change = {
        ...createParsedChange(),
        actions: {
          topic: {enabled: false},
        },
        topic: 'the topic' as TopicName,
        status: ChangeStatus.NEW,
        submit_type: SubmitType.CHERRY_PICK,
        labels: {
          test: {
            all: [{_account_id: 1 as AccountId, name: 'bojack', value: 1}],
            default_value: 0,
            values: [] as unknown as LabelValueToDescriptionMap,
          },
        },
        removable_reviewers: [],
      };
    });

    test('computeTopicReadOnly', () => {
      let mutable = false;
      element.mutable = mutable;
      element.change = change;
      assert.isTrue(element.computeTopicReadOnly());
      mutable = true;
      element.mutable = mutable;
      assert.isTrue(element.computeTopicReadOnly());
      change!.actions!.topic!.enabled = true;
      element.mutable = mutable;
      element.change = change;
      assert.isFalse(element.computeTopicReadOnly());
      mutable = false;
      element.mutable = mutable;
      assert.isTrue(element.computeTopicReadOnly());
    });

    test('topic read only hides delete button', async () => {
      element.account = createAccountDetailWithId();
      element.change = change;
      sinon.stub(GerritNav, 'getUrlForTopic').returns('/q/topic:test');
      await element.updateComplete;
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const button = queryAndAssert<GrButton>(chip, 'gr-button');
      assert.isTrue(button.hasAttribute('hidden'));
    });

    test('topic not read only does not hide delete button', async () => {
      element.account = createAccountDetailWithId();
      change.actions!.topic!.enabled = true;
      element.change = change;
      sinon.stub(GerritNav, 'getUrlForTopic').returns('/q/topic:test');
      await element.updateComplete;
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const button = queryAndAssert<GrButton>(chip, 'gr-button');
      assert.isFalse(button.hasAttribute('hidden'));
    });
  });

  suite('Hashtag removal', () => {
    let change: ParsedChangeInfo;
    setup(() => {
      change = {
        ...createParsedChange(),
        actions: {
          hashtags: {enabled: false},
        },
        hashtags: ['test-hashtag' as Hashtag],
        labels: {
          test: {
            all: [{_account_id: 1 as AccountId, name: 'bojack', value: 1}],
            default_value: 0,
            values: [] as unknown as LabelValueToDescriptionMap,
          },
        },
        removable_reviewers: [],
      };
    });

    test('computeHashtagReadOnly', async () => {
      await element.updateComplete;
      let mutable = false;
      element.change = change;
      element.mutable = mutable;
      await element.updateComplete;
      assert.isTrue(element.computeHashtagReadOnly());
      mutable = true;
      element.change = change;
      element.mutable = mutable;
      await element.updateComplete;
      assert.isTrue(element.computeHashtagReadOnly());
      change!.actions!.hashtags!.enabled = true;
      element.change = change;
      element.mutable = mutable;
      await element.updateComplete;
      assert.isFalse(element.computeHashtagReadOnly());
      mutable = false;
      element.change = change;
      element.mutable = mutable;
      await element.updateComplete;
      assert.isTrue(element.computeHashtagReadOnly());
    });

    test('hashtag read only hides delete button', async () => {
      element.account = createAccountDetailWithId();
      element.change = change;
      sinon
        .stub(GerritNav, 'getUrlForHashtag')
        .returns('/q/hashtag:test+(status:open%20OR%20status:merged)');
      await element.updateComplete;
      assert.isTrue(element.mutable, 'Mutable');
      assert.isFalse(
        element.change.actions?.hashtags?.enabled,
        'hashtags disabled'
      );
      assert.isTrue(element.hashtagReadOnly, 'hashtag read only');
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const button = queryAndAssert<GrButton>(chip, 'gr-button');
      assert.isTrue(button.hasAttribute('hidden'), 'button hidden');
    });

    test('hashtag not read only does not hide delete button', async () => {
      await element.updateComplete;
      element.account = createAccountDetailWithId();
      change!.actions!.hashtags!.enabled = true;
      element.change = change;
      sinon
        .stub(GerritNav, 'getUrlForHashtag')
        .returns('/q/hashtag:test+(status:open%20OR%20status:merged)');
      await element.updateComplete;
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const button = queryAndAssert<GrButton>(chip, 'gr-button');
      assert.isFalse(button.hasAttribute('hidden'));
    });
  });

  suite('remove reviewer votes', () => {
    setup(async () => {
      sinon.stub(element, 'computeTopicReadOnly').returns(true);
      element.change = {
        ...createParsedChange(),
        topic: 'the topic' as TopicName,
        labels: {
          test: {
            all: [{_account_id: 1 as AccountId, name: 'bojack', value: 1}],
            default_value: 0,
            values: [] as unknown as LabelValueToDescriptionMap,
          },
        },
        removable_reviewers: [],
      };
      await element.updateComplete;
    });

    test('changing topic', () => {
      const newTopic = 'the new topic' as TopicName;
      const setChangeTopicStub = stubRestApi('setChangeTopic').returns(
        Promise.resolve(newTopic)
      );
      element.handleTopicChanged(new CustomEvent('test', {detail: newTopic}));
      const topicChangedSpy = sinon.spy();
      element.addEventListener('topic-changed', topicChangedSpy);
      assert.isTrue(
        setChangeTopicStub.calledWith(42 as NumericChangeId, newTopic)
      );
      return setChangeTopicStub.lastCall.returnValue.then(() => {
        assert.equal(element.change!.topic, newTopic);
        assert.isTrue(topicChangedSpy.called);
      });
    });

    test('topic removal', async () => {
      const newTopic = 'the new topic' as TopicName;
      const setChangeTopicStub = stubRestApi('setChangeTopic').returns(
        Promise.resolve(newTopic)
      );
      sinon.stub(GerritNav, 'getUrlForTopic').returns('/q/topic:the+new+topic');
      await element.updateComplete;
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const remove = queryAndAssert(chip, '#remove');
      const topicChangedSpy = sinon.spy();
      element.addEventListener('topic-changed', topicChangedSpy);
      tap(remove);
      assert.isTrue(chip?.disabled);
      assert.isTrue(setChangeTopicStub.calledWith(42 as NumericChangeId));
      return setChangeTopicStub.lastCall.returnValue.then(() => {
        assert.isFalse(chip?.disabled);
        assert.equal(element.change!.topic, '' as TopicName);
        assert.isTrue(topicChangedSpy.called);
      });
    });

    test('changing hashtag', async () => {
      await element.updateComplete;
      const newHashtag: Hashtag[] = ['new hashtag' as Hashtag];
      const setChangeHashtagStub = stubRestApi('setChangeHashtag').returns(
        Promise.resolve(newHashtag)
      );
      element.handleHashtagChanged(
        new CustomEvent('test', {detail: 'new hashtag'})
      );
      assert.isTrue(
        setChangeHashtagStub.calledWith(42 as NumericChangeId, {
          add: ['new hashtag' as Hashtag],
        })
      );
      return setChangeHashtagStub.lastCall.returnValue.then(() => {
        assert.equal(element.change!.hashtags, newHashtag);
      });
    });
  });

  test('editTopic', async () => {
    element.account = createAccountDetailWithId();
    element.change = {
      ...createParsedChange(),
      actions: {topic: {enabled: true}},
    };
    await element.updateComplete;

    const label = element.shadowRoot!.querySelector(
      '.topicEditableLabel'
    ) as GrEditableLabel;
    assert.ok(label);
    const openStub = sinon.stub(label, 'open');
    element.editTopic();
    await element.updateComplete;

    assert.isTrue(openStub.called);
  });

  suite('plugin endpoints', () => {
    setup(async () => {
      resetPlugins();
      element = basicFixture.instantiate();
      element.change = createParsedChange();
      element.revision = createRevision();
      await element.updateComplete;
    });

    teardown(() => {
      resetPlugins();
    });

    test('endpoint params', async () => {
      interface MetadataGrEndpointDecorator extends GrEndpointDecorator {
        plugin: PluginApi;
        change: ParsedChangeInfo;
        revision: RevisionInfo;
      }
      let plugin: PluginApi;
      window.Gerrit.install(
        p => {
          plugin = p;
        },
        '0.1',
        'http://some/plugins/url.js'
      );
      await element.updateComplete;
      const hookEl = (await plugin!
        .hook('change-metadata-item')
        .getLastAttached()) as MetadataGrEndpointDecorator;
      getPluginLoader().loadPlugins([]);
      await element.updateComplete;
      assert.strictEqual(hookEl.plugin, plugin!);
      assert.strictEqual(hookEl.change, element.change);
      assert.strictEqual(hookEl.revision, element.revision);
    });
  });
});
