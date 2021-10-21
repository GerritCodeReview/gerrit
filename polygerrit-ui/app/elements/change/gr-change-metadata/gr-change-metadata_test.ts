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
import '../../core/gr-router/gr-router';
import './gr-change-metadata';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit';
import {GrChangeMetadata} from './gr-change-metadata';
import {
  createServerInfo,
  createUserConfig,
  createParsedChange,
  createAccountWithId,
  createRequirement,
  createCommitInfoWithRequiredCommit,
  createWebLinkInfo,
  createGerritInfo,
  createGitPerson,
  createCommit,
  createRevision,
  createAccountDetailWithId,
} from '../../../test/test-data-generators';
import {
  ChangeStatus,
  SubmitType,
  RequirementStatus,
  GpgKeyInfoStatus,
} from '../../../constants/constants';
import {
  EmailAddress,
  AccountId,
  CommitId,
  ServerInfo,
  RevisionInfo,
  ParentCommitInfo,
  TopicName,
  ElementPropertyDeepChange,
  PatchSetNum,
  NumericChangeId,
  LabelValueToDescriptionMap,
  Hashtag,
} from '../../../types/common';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {GrEditableLabel} from '../../shared/gr-editable-label/gr-editable-label';
import {PluginApi} from '../../../api/plugin';
import {GrEndpointDecorator} from '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {ParsedChangeInfo} from '../../../types/types';
import {GrLinkedChip} from '../../shared/gr-linked-chip/gr-linked-chip';
import {GrButton} from '../../shared/gr-button/gr-button';

const basicFixture = fixtureFromElement('gr-change-metadata');

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-change-metadata tests', () => {
  let element: GrChangeMetadata;

  setup(() => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getConfig').returns(
      Promise.resolve({
        ...createServerInfo(),
        user: {
          ...createUserConfig(),
          anonymous_coward_name: 'test coward name',
        },
      })
    );
    element = basicFixture.instantiate();
  });

  test('_computeMergedCommitInfo', () => {
    const dummyRevs: {[revisionId: string]: RevisionInfo} = {
      1: createRevision(1),
      2: createRevision(2),
    };
    assert.deepEqual(
      element._computeMergedCommitInfo('0' as CommitId, dummyRevs),
      {}
    );
    assert.deepEqual(
      element._computeMergedCommitInfo('1' as CommitId, dummyRevs),
      dummyRevs[1].commit
    );

    // Regression test for issue 5337.
    const commit = element._computeMergedCommitInfo('2' as CommitId, dummyRevs);
    assert.notDeepEqual(commit, dummyRevs[2]);
    assert.deepEqual(commit, dummyRevs[2].commit);
  });

  test('computed fields', () => {
    assert.isFalse(
      element._computeHideStrategy({
        ...createParsedChange(),
        status: ChangeStatus.NEW,
      })
    );
    assert.isTrue(
      element._computeHideStrategy({
        ...createParsedChange(),
        status: ChangeStatus.MERGED,
      })
    );
    assert.isTrue(
      element._computeHideStrategy({
        ...createParsedChange(),
        status: ChangeStatus.ABANDONED,
      })
    );
    assert.equal(
      element._computeStrategy({
        ...createParsedChange(),
        submit_type: SubmitType.CHERRY_PICK,
      }),
      'Cherry Pick'
    );
    assert.equal(
      element._computeStrategy({
        ...createParsedChange(),
        submit_type: SubmitType.REBASE_ALWAYS,
      }),
      'Rebase Always'
    );
  });

  test('computed fields requirements', () => {
    assert.isFalse(
      element._computeShowRequirements({
        ...createParsedChange(),
        status: ChangeStatus.MERGED,
      })
    );
    assert.isFalse(
      element._computeShowRequirements({
        ...createParsedChange(),
        status: ChangeStatus.ABANDONED,
      })
    );

    // No labels and no requirements: submit status is useless
    assert.isFalse(
      element._computeShowRequirements({
        ...createParsedChange(),
        status: ChangeStatus.NEW,
        labels: {},
      })
    );

    // Work in Progress: submit status should be present
    assert.isTrue(
      element._computeShowRequirements({
        ...createParsedChange(),
        status: ChangeStatus.NEW,
        labels: {},
        work_in_progress: true,
      })
    );

    // We have at least one reason to display Submit Status
    assert.isTrue(
      element._computeShowRequirements({
        ...createParsedChange(),
        status: ChangeStatus.NEW,
        labels: {
          Verified: {
            approved: createAccountWithId(),
          },
        },
        requirements: [],
      })
    );
    assert.isTrue(
      element._computeShowRequirements({
        ...createParsedChange(),
        status: ChangeStatus.NEW,
        labels: {},
        requirements: [
          {
            ...createRequirement(),
            fallbackText: 'Resolve all comments',
            status: RequirementStatus.OK,
          },
        ],
      })
    );
  });

  test('show strategy for open change', () => {
    element.change = {
      ...createParsedChange(),
      status: ChangeStatus.NEW,
      submit_type: SubmitType.CHERRY_PICK,
      labels: {},
    };
    flush();
    const strategy = element.shadowRoot?.querySelector('.strategy');
    assert.ok(strategy);
    assert.isFalse(strategy?.hasAttribute('hidden'));
    assert.equal(strategy?.children[1].innerHTML, 'Cherry Pick');
  });

  test('hide strategy for closed change', () => {
    element.change = {
      ...createParsedChange(),
      status: ChangeStatus.MERGED,
      labels: {},
    };
    flush();
    assert.isTrue(
      element.shadowRoot?.querySelector('.strategy')?.hasAttribute('hidden')
    );
  });

  test('weblinks use GerritNav interface', () => {
    const weblinksStub = sinon
      .stub(GerritNav, '_generateWeblinks')
      .returns([{name: 'stubb', url: '#s'}]);
    element.commitInfo = createCommitInfoWithRequiredCommit();
    element.serverConfig = createServerInfo();
    flush();
    const webLinks = element.$.webLinks;
    assert.isTrue(weblinksStub.called);
    assert.isFalse(webLinks.hasAttribute('hidden'));
    assert.equal(element._computeWebLinks(element.commitInfo)?.length, 1);
  });

  test('weblinks hidden when no weblinks', () => {
    element.commitInfo = createCommitInfoWithRequiredCommit();
    element.serverConfig = createServerInfo();
    flush();
    const webLinks = element.$.webLinks;
    assert.isTrue(webLinks.hasAttribute('hidden'));
  });

  test('weblinks hidden when only gitiles weblink', () => {
    element.commitInfo = {
      ...createCommitInfoWithRequiredCommit(),
      web_links: [{...createWebLinkInfo(), name: 'gitiles', url: '#'}],
    };
    element.serverConfig = createServerInfo();
    flush();
    const webLinks = element.$.webLinks;
    assert.isTrue(webLinks.hasAttribute('hidden'));
    assert.equal(element._computeWebLinks(element.commitInfo), null);
  });

  test('weblinks hidden when sole weblink is set as primary', () => {
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
    flush();
    const webLinks = element.$.webLinks;
    assert.isTrue(webLinks.hasAttribute('hidden'));
  });

  test('weblinks are visible when other weblinks', () => {
    const router = document.createElement('gr-router');
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router._generateWeblinks.bind(router));

    element.commitInfo = {
      ...createCommitInfoWithRequiredCommit(),
      web_links: [{...createWebLinkInfo(), name: 'test', url: '#'}],
    };
    flush();
    const webLinks = element.$.webLinks;
    assert.isFalse(webLinks.hasAttribute('hidden'));
    assert.equal(element._computeWebLinks(element.commitInfo)?.length, 1);
    // With two non-gitiles weblinks, there are two returned.
    element.commitInfo = {
      ...createCommitInfoWithRequiredCommit(),
      web_links: [
        {...createWebLinkInfo(), name: 'test', url: '#'},
        {...createWebLinkInfo(), name: 'test2', url: '#'},
      ],
    };
    assert.equal(element._computeWebLinks(element.commitInfo)?.length, 2);
  });

  test('weblinks are visible when gitiles and other weblinks', () => {
    const router = document.createElement('gr-router');
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router._generateWeblinks.bind(router));

    element.commitInfo = {
      ...createCommitInfoWithRequiredCommit(),
      web_links: [
        {...createWebLinkInfo(), name: 'test', url: '#'},
        {...createWebLinkInfo(), name: 'gitiles', url: '#'},
      ],
    };
    flush();
    const webLinks = element.$.webLinks;
    assert.isFalse(webLinks.hasAttribute('hidden'));
    // Only the non-gitiles weblink is returned.
    assert.equal(element._computeWebLinks(element.commitInfo)?.length, 1);
  });

  suite('_getNonOwnerRole', () => {
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
      test('_getNonOwnerRole for uploader', () => {
        assert.deepEqual(
          element._getNonOwnerRole(change, element._CHANGE_ROLE.UPLOADER),
          {
            ...createAccountWithId(),
            email: 'ghi@def' as EmailAddress,
            _account_id: 1011123 as AccountId,
          }
        );
      });

      test('_getNonOwnerRole that it does not return uploader', () => {
        // Set the uploader email to be the same as the owner.
        change!.revisions.rev1.uploader!._account_id = 1019328 as AccountId;
        assert.isNotOk(
          element._getNonOwnerRole(change, element._CHANGE_ROLE.UPLOADER)
        );
      });

      test('_computeShowRoleClass show uploader', () => {
        assert.equal(
          element._computeShowRoleClass(change, element._CHANGE_ROLE.UPLOADER),
          ''
        );
      });

      test('_computeShowRoleClass hide uploader', () => {
        // Set the uploader email to be the same as the owner.
        change!.revisions.rev1.uploader!._account_id = 1019328 as AccountId;
        assert.equal(
          element._computeShowRoleClass(change, element._CHANGE_ROLE.UPLOADER),
          'hideDisplay'
        );
      });
    });

    suite('role=committer', () => {
      test('_getNonOwnerRole for committer', () => {
        change!.revisions.rev1.uploader!.email = 'ghh@def' as EmailAddress;
        assert.deepEqual(
          element._getNonOwnerRole(change, element._CHANGE_ROLE.COMMITTER),
          {...createGitPerson(), email: 'ghi@def' as EmailAddress}
        );
      });

      test('_getNonOwnerRole is null if committer is same as uploader', () => {
        assert.isNotOk(
          element._getNonOwnerRole(change, element._CHANGE_ROLE.COMMITTER)
        );
      });

      test('_getNonOwnerRole that it does not return committer', () => {
        // Set the committer email to be the same as the owner.
        change!.revisions.rev1.commit!.committer.email =
          'abc@def' as EmailAddress;
        assert.isNotOk(
          element._getNonOwnerRole(change, element._CHANGE_ROLE.COMMITTER)
        );
      });

      test('_getNonOwnerRole null for committer with no commit', () => {
        delete change!.revisions.rev1.commit;
        assert.isNotOk(
          element._getNonOwnerRole(change, element._CHANGE_ROLE.COMMITTER)
        );
      });
    });

    suite('role=author', () => {
      test('_getNonOwnerRole for author', () => {
        assert.deepEqual(
          element._getNonOwnerRole(change, element._CHANGE_ROLE.AUTHOR),
          {...createGitPerson(), email: 'jkl@def' as EmailAddress}
        );
      });

      test('_getNonOwnerRole that it does not return author', () => {
        // Set the author email to be the same as the owner.
        change!.revisions.rev1.commit!.author.email = 'abc@def' as EmailAddress;
        assert.isNotOk(
          element._getNonOwnerRole(change, element._CHANGE_ROLE.AUTHOR)
        );
      });

      test('_getNonOwnerRole null for author with no commit', () => {
        delete change!.revisions.rev1.commit;
        assert.isNotOk(
          element._getNonOwnerRole(change, element._CHANGE_ROLE.AUTHOR)
        );
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
    });

    test('Push Certificate Validation test BAD', () => {
      change!.revisions.rev1!.push_certificate = {
        certificate: 'Push certificate',
        key: {
          status: GpgKeyInfoStatus.BAD,
          problems: ['No public keys found for key ID E5E20E52'],
        },
      };
      const result = element._computePushCertificateValidation(
        serverConfig,
        change
      );
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
      const result = element._computePushCertificateValidation(
        serverConfig,
        change
      );
      assert.equal(
        result?.message,
        'Push certificate is valid and key is trusted'
      );
      assert.equal(result?.icon, 'gr-icons:check');
      assert.equal(result?.class, 'trusted');
    });

    test('Push Certificate Validation is missing test', () => {
      change!.revisions.rev1! = createRevision(1);
      const result = element._computePushCertificateValidation(
        serverConfig,
        change
      );
      assert.equal(
        result?.message,
        'This patch set was created without a push certificate'
      );
      assert.equal(result?.icon, 'gr-icons:help');
      assert.equal(result?.class, 'help');
    });
  });

  test('_computeParents', () => {
    const parents: ParentCommitInfo[] = [
      {...createCommit(), commit: '123' as CommitId, subject: 'abc'},
    ];
    const revision: RevisionInfo = {
      ...createRevision(1),
      commit: {...createCommit(), parents},
    };
    assert.equal(element._computeParents(undefined, revision), parents);
    const change = (current_revision: CommitId): ParsedChangeInfo => {
      return {
        ...createParsedChange(),
        current_revision,
        revisions: {456: revision},
      };
    };
    const change_bad_revision = change('789' as CommitId);
    assert.deepEqual(
      element._computeParents(change_bad_revision, createRevision()),
      []
    );
    const change_no_commit: ParsedChangeInfo = {
      ...createParsedChange(),
      current_revision: '456' as CommitId,
      revisions: {456: createRevision()},
    };
    assert.deepEqual(element._computeParents(change_no_commit, undefined), []);
    const change_good = change('456' as CommitId);
    assert.equal(element._computeParents(change_good, undefined), parents);
  });

  test('_currentParents', () => {
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
    assert.equal(element._currentParents[0].commit, '222');
    element.revision = revision('333' as CommitId);
    assert.equal(element._currentParents[0].commit, '333');
    element.revision = undefined;
    assert.equal(element._currentParents[0].commit, '111');
    element.change = createParsedChange();
    assert.deepEqual(element._currentParents, []);
  });

  test('_computeParentsLabel', () => {
    const parent: ParentCommitInfo = {
      ...createCommit(),
      commit: 'abc123' as CommitId,
      subject: 'My parent commit',
    };
    assert.equal(element._computeParentsLabel([parent]), 'Parent');
    assert.equal(element._computeParentsLabel([parent, parent]), 'Parents');
  });

  test('_computeParentListClass', () => {
    const parent: ParentCommitInfo = {
      ...createCommit(),
      commit: 'abc123' as CommitId,
      subject: 'My parent commit',
    };
    assert.equal(
      element._computeParentListClass([parent], true),
      'parentList nonMerge current'
    );
    assert.equal(
      element._computeParentListClass([parent], false),
      'parentList nonMerge notCurrent'
    );
    assert.equal(
      element._computeParentListClass([parent, parent], false),
      'parentList merge notCurrent'
    );
    assert.equal(
      element._computeParentListClass([parent, parent], true),
      'parentList merge current'
    );
  });

  test('_showAddTopic', () => {
    const changeRecord: ElementPropertyDeepChange<GrChangeMetadata, 'change'> =
      {
        base: {...createParsedChange()},
        path: '',
        value: undefined,
      };
    assert.isTrue(element._showAddTopic(undefined, false));
    assert.isTrue(element._showAddTopic(changeRecord, false));
    assert.isFalse(element._showAddTopic(changeRecord, true));
    changeRecord.base!.topic = 'foo' as TopicName;
    assert.isFalse(element._showAddTopic(changeRecord, true));
    assert.isFalse(element._showAddTopic(changeRecord, false));
  });

  test('_showTopicChip', () => {
    const changeRecord: ElementPropertyDeepChange<GrChangeMetadata, 'change'> =
      {
        base: {...createParsedChange()},
        path: '',
        value: undefined,
      };
    assert.isFalse(element._showTopicChip(undefined, false));
    assert.isFalse(element._showTopicChip(changeRecord, false));
    assert.isFalse(element._showTopicChip(changeRecord, true));
    changeRecord.base!.topic = 'foo' as TopicName;
    assert.isFalse(element._showTopicChip(changeRecord, true));
    assert.isTrue(element._showTopicChip(changeRecord, false));
  });

  test('_showCherryPickOf', () => {
    const changeRecord: ElementPropertyDeepChange<GrChangeMetadata, 'change'> =
      {
        base: {...createParsedChange()},
        path: '',
        value: undefined,
      };
    assert.isFalse(element._showCherryPickOf(undefined));
    assert.isFalse(element._showCherryPickOf(changeRecord));
    changeRecord.base!.cherry_pick_of_change = 123 as NumericChangeId;
    changeRecord.base!.cherry_pick_of_patch_set = 1 as PatchSetNum;
    assert.isTrue(element._showCherryPickOf(changeRecord));
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

    test('_computeTopicReadOnly', () => {
      let mutable = false;
      assert.isTrue(element._computeTopicReadOnly(mutable, change));
      mutable = true;
      assert.isTrue(element._computeTopicReadOnly(mutable, change));
      change!.actions!.topic!.enabled = true;
      assert.isFalse(element._computeTopicReadOnly(mutable, change));
      mutable = false;
      assert.isTrue(element._computeTopicReadOnly(mutable, change));
    });

    test('topic read only hides delete button', async () => {
      element.account = createAccountDetailWithId();
      element.change = change;
      sinon.stub(GerritNav, 'getUrlForTopic').returns('/q/topic:test');
      await flush();
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const button = queryAndAssert<GrButton>(chip, 'gr-button');
      assert.isTrue(button.hasAttribute('hidden'));
    });

    test('topic not read only does not hide delete button', async () => {
      element.account = createAccountDetailWithId();
      change.actions!.topic!.enabled = true;
      element.change = change;
      sinon.stub(GerritNav, 'getUrlForTopic').returns('/q/topic:test');
      await flush();
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

    test('_computeHashtagReadOnly', async () => {
      await flush();
      let mutable = false;
      assert.isTrue(element._computeHashtagReadOnly(mutable, change));
      mutable = true;
      assert.isTrue(element._computeHashtagReadOnly(mutable, change));
      change!.actions!.hashtags!.enabled = true;
      assert.isFalse(element._computeHashtagReadOnly(mutable, change));
      mutable = false;
      assert.isTrue(element._computeHashtagReadOnly(mutable, change));
    });

    test('hashtag read only hides delete button', async () => {
      await flush();
      element.account = createAccountDetailWithId();
      element.change = change;
      sinon
        .stub(GerritNav, 'getUrlForHashtag')
        .returns('/q/hashtag:test+(status:open%20OR%20status:merged)');
      await flush();
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const button = queryAndAssert<GrButton>(chip, 'gr-button');
      assert.isTrue(button.hasAttribute('hidden'));
    });

    test('hashtag not read only does not hide delete button', async () => {
      await flush();
      element.account = createAccountDetailWithId();
      change!.actions!.hashtags!.enabled = true;
      element.change = change;
      sinon
        .stub(GerritNav, 'getUrlForHashtag')
        .returns('/q/hashtag:test+(status:open%20OR%20status:merged)');
      await flush();
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const button = queryAndAssert<GrButton>(chip, 'gr-button');
      assert.isFalse(button.hasAttribute('hidden'));
    });
  });

  suite('remove reviewer votes', () => {
    setup(() => {
      sinon.stub(element, '_computeTopicReadOnly').returns(true);
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
      flush();
    });

    test('changing topic', () => {
      const newTopic = 'the new topic' as TopicName;
      const setChangeTopicStub = stubRestApi('setChangeTopic').returns(
        Promise.resolve(newTopic)
      );
      element._handleTopicChanged(new CustomEvent('test', {detail: newTopic}));
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
      await flush();
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
      await flush();
      element._newHashtag = 'new hashtag' as Hashtag;
      const newHashtag: Hashtag[] = ['new hashtag' as Hashtag];
      const setChangeHashtagStub = stubRestApi('setChangeHashtag').returns(
        Promise.resolve(newHashtag)
      );
      element._handleHashtagChanged();
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
    await flush();

    const label = element.shadowRoot!.querySelector(
      '.topicEditableLabel'
    ) as GrEditableLabel;
    assert.ok(label);
    const openStub = sinon.stub(label, 'open');
    element.editTopic();
    await flush();

    assert.isTrue(openStub.called);
  });

  suite('plugin endpoints', () => {
    test('endpoint params', async () => {
      element.change = createParsedChange();
      element.revision = createRevision();
      interface MetadataGrEndpointDecorator extends GrEndpointDecorator {
        plugin: PluginApi;
        change: ParsedChangeInfo;
        revision: RevisionInfo;
      }
      let hookEl: MetadataGrEndpointDecorator;
      let plugin: PluginApi;
      pluginApi.install(
        p => {
          plugin = p;
          plugin
            .hook('change-metadata-item')
            .getLastAttached()
            .then(el => (hookEl = el as MetadataGrEndpointDecorator));
        },
        '0.1',
        'http://some/plugins/url.js'
      );
      getPluginLoader().loadPlugins([]);
      await flush();
      assert.strictEqual(hookEl!.plugin, plugin!);
      assert.strictEqual(hookEl!.change, element.change);
      assert.strictEqual(hookEl!.revision, element.revision);
    });
  });
});
