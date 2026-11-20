/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-change-metadata';

import {ChangeRole, GrChangeMetadata} from './gr-change-metadata';
import {
  createAccountDetailWithId,
  createAccountWithId,
  createCommit,
  createCommitInfoWithRequiredCommit,
  createConfig,
  createEditRevision,
  createGerritInfo,
  createGitPerson,
  createParsedChange,
  createRevision,
  createServerInfo,
  createWebLinkInfo,
} from '../../../test/test-data-generators';
import {
  ChangeStatus,
  CommitSignatureReason,
  CommitSignatureSignerType,
  CommitSignatureStatus,
  GpgKeyInfoStatus,
  InheritedBooleanInfoConfiguredValue,
  SubmitType,
} from '../../../constants/constants';
import {
  AccountId,
  CommitId,
  CommitInfo,
  CommitSignatureInfo,
  EmailAddress,
  GpgKeyFingerprint,
  Hashtag,
  LabelValueToDescriptionMap,
  NumericChangeId,
  ParentCommitInfo,
  PatchSetNumber,
  RevisionInfo,
  RevisionPatchSetNum,
  ServerInfo,
  TopicName,
} from '../../../types/common';
import {GrEditableLabel} from '../../shared/gr-editable-label/gr-editable-label';
import {PluginApi} from '../../../api/plugin';
import {GrEndpointDecorator} from '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
  waitEventLoop,
  waitUntilCalled,
} from '../../../test/test-utils';
import {ParsedChangeInfo} from '../../../types/types';
import {GrLinkedChip} from '../../shared/gr-linked-chip/gr-linked-chip';
import {GrButton} from '../../shared/gr-button/gr-button';
import {nothing} from 'lit';
import {assert, fixture, html} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';

suite('gr-change-metadata tests', () => {
  let element: GrChangeMetadata;

  setup(async () => {
    element = await fixture(html`<gr-change-metadata></gr-change-metadata>`);
    element.change = createParsedChange();
    element.account = undefined;
    await element.updateComplete;
  });

  test('renders', async () => {
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div>
      <div class="metadata-header">
        <h3 class="heading-3 metadata-title">Change Info</h3>
        <gr-button
          class="show-all-button"
          link=""
          role="button"
          tabindex="0"
          aria-disabled="false"
        >
          Show All <gr-icon icon="expand_more"></gr-icon>
          <gr-icon hidden="" icon="expand_less"></gr-icon>
        </gr-button>
      </div>
      <section class="hideDisplay">
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="Time when the first patchset of this change was uploaded."
          >
            Created
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-date-formatter showyesterday="" withtooltip="">
          </gr-date-formatter>
        </span>
      </section>
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
            <gr-tooltip-content
              has-tooltip=""
              title="Repository and branch that the change will be merged into if submitted."
            >
              Repo | Branch
            </gr-tooltip-content>
          </span>
          <span class="value">
            <a href="/q/project:test-project">
              test-project
            </a>
            |
            <a href="/q/project:test-project+branch:test-branch">
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
      <gr-endpoint-decorator name="change-metadata-item">
        <gr-endpoint-param name="labels"> </gr-endpoint-param>
        <gr-endpoint-param name="change"> </gr-endpoint-param>
        <gr-endpoint-param name="revision"> </gr-endpoint-param>
        <gr-endpoint-slot name="above-submit-requirements"></gr-endpoint-slot>
        <div class="separatedSection">
          <gr-submit-requirements></gr-submit-requirements>
        </div>
      </gr-endpoint-decorator>
    </div>`
    );
  });

  suite('commit signature', () => {
    const signingFingerprint =
      'C378369ACBCD34CC138D90B145311A6F378A0AED' as GpgKeyFingerprint;
    const primaryFingerprint =
      'F0D5A7A3A8F10E8E104317849445D26DB96792CA' as GpgKeyFingerprint;
    const signer = {...createAccountWithId(42), name: 'Commit signer'};
    const verified: CommitSignatureInfo = {
      status: CommitSignatureStatus.VERIFIED,
      signer,
      signing_key_fingerprint: signingFingerprint,
      primary_key_fingerprint: primaryFingerprint,
    };
    let signatureStub: sinon.SinonStub<
      [NumericChangeId, PatchSetNumber],
      Promise<CommitSignatureInfo | undefined>
    >;

    setup(() => {
      signatureStub = stubRestApi('getCommitSignature').resolves(verified);
      element.serverConfig = {
        ...createServerInfo(),
        gerrit: {...createGerritInfo(), commit_signature_verification: true},
      };
      element.revision =
        element.change!.revisions[element.change!.current_revision];
    });

    async function flushSignature() {
      await element.updateComplete;
      await waitEventLoop();
      await element.updateComplete;
    }

    function signatureRow() {
      return queryAndAssert<HTMLElement>(element, '.commitSignature');
    }

    function signatureStatus() {
      return queryAndAssert<HTMLElement>(
        signatureRow(),
        '.signatureStatus'
      ).textContent?.trim();
    }

    const statuses: {signature: CommitSignatureInfo; label: string}[] = [
      {signature: verified, label: 'Verified'},
      {signature: {status: CommitSignatureStatus.UNSIGNED}, label: 'Unsigned'},
      {
        signature: {
          status: CommitSignatureStatus.UNVERIFIED,
          reason: CommitSignatureReason.UNKNOWN_KEY,
          message: 'Signing key is not registered',
          signing_key_fingerprint: signingFingerprint,
          primary_key_fingerprint: primaryFingerprint,
        },
        label: 'Unverified',
      },
    ];
    for (const {signature, label} of statuses) {
      test(`renders ${label} without conflating commit and push signatures`, async () => {
        signatureStub.resolves(signature);
        await flushSignature();

        assert.equal(signatureStatus(), label);
        assert.include(signatureRow().textContent, 'Commit signature');
        const chip =
          signatureRow().querySelector<GrAccountChip>('gr-account-chip');
        if (signature.signer) {
          assert.deepEqual(chip?.account, signer);
          assert.notDeepEqual(chip?.account, element.change!.owner);
        } else {
          assert.isNull(chip);
        }
        if (signature.signing_key_fingerprint) {
          const keys = signatureRow().querySelectorAll('code');
          assert.deepEqual(
            Array.from(keys).map(key => key.textContent),
            [signingFingerprint, primaryFingerprint]
          );
        } else {
          assert.isNull(signatureRow().querySelector('code'));
          assert.isNull(signatureRow().querySelector('gr-icon'));
        }
        if (signature.message) {
          assert.include(signatureRow().textContent, signature.message);
        }
        const explanation = queryAndAssert<HTMLElement>(
          signatureRow(),
          '.title gr-tooltip-content'
        ).title;
        assert.include(explanation, 'committer');
        assert.include(explanation, 'author');
        assert.include(explanation, 'uploader');
        assert.include(explanation, 'push certificate');
      });
    }

    test('verified key remains useful without a disclosed signer account', async () => {
      signatureStub.resolves({...verified, signer: undefined});
      await flushSignature();
      assert.equal(signatureStatus(), 'Verified');
      assert.isNull(signatureRow().querySelector('gr-account-chip'));
      assert.include(signatureRow().textContent, 'Signer account unavailable');
      assert.include(signatureRow().textContent, signingFingerprint);
    });

    test('server attestation is labeled separately and does not invent an account', async () => {
      signatureStub.resolves({
        ...verified,
        signer: undefined,
        signer_type: CommitSignatureSignerType.SERVER,
      });
      await flushSignature();
      assert.equal(signatureStatus(), 'Verified (server)');
      assert.isNull(signatureRow().querySelector('gr-account-chip'));
      assert.notInclude(
        signatureRow().textContent,
        'Signer account unavailable'
      );
      assert.include(signatureRow().textContent, 'server attestation');
      assert.include(
        signatureRow().textContent,
        'does not identify the committer'
      );
      assert.include(signatureRow().textContent, signingFingerprint);
    });

    test('unverified explanation is text, not markup', async () => {
      const message = '<img src=x onerror="alert(1)">';
      signatureStub.resolves({
        status: CommitSignatureStatus.UNVERIFIED,
        reason: CommitSignatureReason.MALFORMED_SIGNATURE,
        message,
      });
      await flushSignature();
      assert.include(signatureRow().textContent, message);
      assert.isNull(signatureRow().querySelector('img'));
    });

    for (const capability of [false, undefined]) {
      test(`does not fetch when effective capability is ${capability}`, async () => {
        element.serverConfig = {
          ...createServerInfo(),
          gerrit: {
            ...createGerritInfo(),
            commit_signature_verification: capability,
          },
          receive: {enable_signed_push: true},
        };
        await flushSignature();
        assert.isTrue(signatureStub.notCalled);
        assert.isNull(element.shadowRoot?.querySelector('.commitSignature'));
      });
    }

    test('does not fetch before the selected revision is known', async () => {
      element.revision = undefined;
      await flushSignature();
      assert.isTrue(signatureStub.notCalled);
      assert.isNull(element.shadowRoot?.querySelector('.commitSignature'));
    });

    test('does not verify an unpublished edit', async () => {
      element.revision = createEditRevision();
      await flushSignature();
      assert.isTrue(signatureStub.notCalled);
      assert.isNull(element.shadowRoot?.querySelector('.commitSignature'));
    });

    test('hides verification while entering edit mode', async () => {
      element.editMode = true;
      await flushSignature();
      assert.isTrue(signatureStub.notCalled);
      assert.isNull(element.shadowRoot?.querySelector('.commitSignature'));
    });

    test('fetches the selected stored patch set, not the latest', async () => {
      element.change = {
        ...createParsedChange(),
        current_revision: 'latest' as CommitId,
        revisions: {older: createRevision(1), latest: createRevision(2)},
      };
      element.revision = element.change.revisions.older;
      await flushSignature();
      assert.isTrue(
        signatureStub.calledOnceWithExactly(
          element.change._number,
          1 as PatchSetNumber
        )
      );
      assert.equal(signatureStatus(), 'Verified');
    });

    for (const failure of ['rejected', 'empty']) {
      test(`${failure} request reports unavailable, not a signature verdict`, async () => {
        if (failure === 'rejected') {
          signatureStub.rejects(new Error('Verifier storage failure'));
        } else {
          signatureStub.resolves(undefined);
        }
        await flushSignature();
        assert.equal(signatureStatus(), 'Verification unavailable');
        assert.isNull(signatureRow().querySelector('gr-account-chip'));
        assert.notInclude(signatureRow().textContent, 'Unsigned');
        assert.notInclude(signatureRow().textContent, 'Unverified');
        assert.notInclude(
          signatureRow().textContent,
          'Verifier storage failure'
        );
      });
    }

    test('clears the previous verdict while asynchronously loading a selection', async () => {
      await flushSignature();
      assert.equal(signatureStatus(), 'Verified');
      const pending = mockPromise<CommitSignatureInfo>();
      signatureStub.returns(pending);
      element.revision = createRevision(2);
      await element.updateComplete;
      assert.equal(signatureStatus(), 'Verifying…');
      assert.isNull(signatureRow().querySelector('gr-account-chip'));
      assert.isNull(signatureRow().querySelector('code'));
      assert.ok(element.shadowRoot?.querySelector('gr-reviewer-list'));

      pending.resolve({status: CommitSignatureStatus.UNSIGNED});
      await flushSignature();
      assert.equal(signatureStatus(), 'Unsigned');
    });

    for (const stale of ['resolved', 'rejected']) {
      test(`ignores a ${stale} request for a previous selection`, async () => {
        const older = mockPromise<CommitSignatureInfo>();
        const newer = mockPromise<CommitSignatureInfo>();
        signatureStub.onFirstCall().returns(older);
        signatureStub.onSecondCall().returns(newer);
        await element.updateComplete;
        element.revision = createRevision(2);
        await element.updateComplete;
        assert.isTrue(signatureStub.calledTwice);

        newer.resolve({status: CommitSignatureStatus.UNSIGNED});
        await flushSignature();
        if (stale === 'resolved') {
          older.resolve(verified);
        } else {
          older.reject(new Error('Old verification failed'));
        }
        await flushSignature();
        assert.equal(signatureStatus(), 'Unsigned');
        assert.isNull(signatureRow().querySelector('gr-account-chip'));
      });
    }

    test('does not reuse a pending verdict after returning to its patch set', async () => {
      const original = mockPromise<CommitSignatureInfo>();
      const other = mockPromise<CommitSignatureInfo>();
      const current = mockPromise<CommitSignatureInfo>();
      signatureStub.onFirstCall().returns(original);
      signatureStub.onSecondCall().returns(other);
      signatureStub.onThirdCall().returns(current);
      await element.updateComplete;
      element.revision = createRevision(2);
      await element.updateComplete;
      element.revision = createRevision(1);
      await element.updateComplete;

      current.resolve({status: CommitSignatureStatus.UNSIGNED});
      await flushSignature();
      original.resolve(verified);
      other.resolve({status: CommitSignatureStatus.UNVERIFIED});
      await flushSignature();
      assert.equal(signatureStatus(), 'Unsigned');
      assert.isNull(signatureRow().querySelector('gr-account-chip'));
    });

    test('change navigation invalidates requests with the same patch set number', async () => {
      const previous = mockPromise<CommitSignatureInfo>();
      signatureStub.onFirstCall().returns(previous);
      signatureStub
        .onSecondCall()
        .resolves({status: CommitSignatureStatus.UNSIGNED});
      await element.updateComplete;
      element.change = {...element.change!, _number: 999 as NumericChangeId};
      await flushSignature();
      assert.isTrue(
        signatureStub.calledWithExactly(
          999 as NumericChangeId,
          1 as PatchSetNumber
        )
      );
      previous.resolve(verified);
      await flushSignature();
      assert.equal(signatureStatus(), 'Unsigned');
      assert.isNull(signatureRow().querySelector('gr-account-chip'));
    });

    test('ignores a pending result when verification is disabled', async () => {
      const pending = mockPromise<CommitSignatureInfo>();
      signatureStub.returns(pending);
      await element.updateComplete;
      element.serverConfig = createServerInfo();
      await element.updateComplete;
      pending.resolve(verified);
      await flushSignature();
      assert.isNull(element.shadowRoot?.querySelector('.commitSignature'));
      assert.isTrue(signatureStub.calledOnce);
    });
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

  test('weblinks hidden when no weblinks', async () => {
    element.revision = {
      ...createRevision(),
      commit: createCommitInfoWithRequiredCommit(),
    };
    element.serverConfig = createServerInfo();
    await element.updateComplete;
    assert.isNull(element.webLinks);
  });

  test('weblinks hidden when only gitiles weblink', async () => {
    element.revision = {
      ...createRevision(),
      commit: {
        ...createCommitInfoWithRequiredCommit(),
        web_links: [{...createWebLinkInfo(), name: 'gitiles', url: '#'}],
      },
    };
    element.serverConfig = createServerInfo();
    await element.updateComplete;
    assert.isNull(element.webLinks);
    assert.equal(element.computeWebLinks().length, 0);
  });

  test('weblinks hidden when sole weblink is set as primary', async () => {
    const browser = 'browser';
    element.revision = {
      ...createRevision(),
      commit: {
        ...createCommitInfoWithRequiredCommit(),
        web_links: [{...createWebLinkInfo(), name: browser, url: '#'}],
      },
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
    element.revision = {
      ...createRevision(),
      commit: {
        ...createCommitInfoWithRequiredCommit(),
        web_links: [{...createWebLinkInfo(), name: 'test', url: '#'}],
      },
    };
    await element.updateComplete;
    const webLinks = element.webLinks!;
    assert.isFalse(webLinks.hasAttribute('hidden'));
    assert.equal(element.computeWebLinks().length, 1);
  });

  test('weblinks are visible when gitiles and other weblinks', async () => {
    element.revision = {
      ...createRevision(),
      commit: {
        ...createCommitInfoWithRequiredCommit(),
        web_links: [
          {...createWebLinkInfo(), name: 'test', url: '#'},
          {...createWebLinkInfo(), name: 'gitiles', url: '#'},
        ],
      },
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

      test('getNonOwnerRole returns committer with same email as owner in edit mode', () => {
        // Set the committer email to be the same as the owner.
        change!.revisions.rev1.commit!.committer.email =
          'abc@def' as EmailAddress;
        element.change = change;
        element.editMode = true;
        assert.isOk(element.getNonOwnerRole(ChangeRole.COMMITTER));
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

      test('getNonOwnerRole returns author with same email as owner in edit mode', () => {
        // Set the author email to be the same as the owner.
        change!.revisions.rev1.commit!.author.email = 'abc@def' as EmailAddress;
        element.change = change;
        element.editMode = true;
        assert.isOk(element.getNonOwnerRole(ChangeRole.AUTHOR));
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
          enable_signed_push: true,
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
      change!.revisions.rev1.push_certificate = {
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
      assert.equal(result?.icon, 'close');
      assert.equal(result?.class, 'invalid');
    });

    test('Push Certificate Validation test TRUSTED', () => {
      change!.revisions.rev1.push_certificate = {
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
      assert.equal(result?.icon, 'check');
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
      assert.equal(result?.icon, 'help');
      assert.equal(result?.class, 'help filled');
    });

    test('computePushCertificateValidation returns undefined', () => {
      element.change = change;
      delete serverConfig!.receive!.enable_signed_push;
      element.serverConfig = serverConfig;
      assert.isUndefined(element.computePushCertificateValidation());
    });

    test('isEnabledSignedPushOnRepo', () => {
      change!.revisions.rev1.push_certificate = {
        certificate: 'Push certificate',
        key: {
          status: GpgKeyInfoStatus.TRUSTED,
        },
      };
      element.change = change;
      element.serverConfig = serverConfig;
      element.repoConfig!.enable_signed_push!.configured_value =
        InheritedBooleanInfoConfiguredValue.INHERIT;
      element.repoConfig!.enable_signed_push!.inherited_value = true;
      assert.isTrue(element.isEnabledSignedPushOnRepo());

      element.repoConfig!.enable_signed_push!.inherited_value = false;
      assert.isFalse(element.isEnabledSignedPushOnRepo());

      element.repoConfig!.enable_signed_push!.configured_value =
        InheritedBooleanInfoConfiguredValue.TRUE;
      assert.isTrue(element.isEnabledSignedPushOnRepo());

      element.repoConfig = undefined;
      assert.isFalse(element.isEnabledSignedPushOnRepo());
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
      owner: createAccountWithId(),
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
    change.cherry_pick_of_patch_set = 1 as RevisionPatchSetNum;
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
      change.actions!.topic!.enabled = true;
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
      await element.updateComplete;
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const button = queryAndAssert<GrButton>(chip, 'gr-button');
      assert.isTrue(button.hasAttribute('hidden'));
    });

    test('topic not read only does not hide delete button', async () => {
      element.account = createAccountDetailWithId();
      change.actions!.topic!.enabled = true;
      element.change = change;
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
      change.actions!.hashtags!.enabled = true;
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
      change.actions!.hashtags!.enabled = true;
      element.change = change;
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

    test('changing topic', async () => {
      const newTopic = 'the new topic' as TopicName;
      const setChangeTopicStub = stubRestApi('setChangeTopic').returns(
        Promise.resolve(newTopic)
      );
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      element.handleTopicChanged(new CustomEvent('test', {detail: newTopic}));

      assert.isTrue(setChangeTopicStub.calledWith(42, newTopic));
      await setChangeTopicStub.lastCall.returnValue;
      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: 'Saving topic and reloading ...',
        showDismiss: true,
      });
    });

    test('topic removal', async () => {
      const newTopic = 'the new topic' as TopicName;
      const setChangeTopicStub = stubRestApi('setChangeTopic').returns(
        Promise.resolve(newTopic)
      );
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      await element.updateComplete;
      const chip = queryAndAssert<GrLinkedChip>(element, 'gr-linked-chip');
      const remove = queryAndAssert<GrButton>(chip, '#remove');

      remove.click();

      assert.isTrue(chip?.disabled);
      assert.isTrue(setChangeTopicStub.calledWith(42));
      await setChangeTopicStub.lastCall.returnValue;
      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: 'Removing topic and reloading ...',
        showDismiss: true,
      });
    });

    test('changing hashtag', async () => {
      await element.updateComplete;
      const newHashtag: Hashtag[] = ['new hashtag' as Hashtag];
      const setChangeHashtagStub = stubRestApi('setChangeHashtag').returns(
        Promise.resolve(newHashtag)
      );
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      element.handleHashtagChanged(
        new CustomEvent('test', {detail: 'new hashtag'})
      );
      assert.isTrue(
        setChangeHashtagStub.calledWith(42, {
          add: ['new hashtag' as Hashtag],
        })
      );
      await setChangeHashtagStub.lastCall.returnValue;
      await waitUntilCalled(alertStub, 'alertStub');
      assert.deepEqual(alertStub.lastCall.args[0].detail, {
        message: 'Saving hashtag and reloading ...',
        showDismiss: true,
      });
    });
  });

  test('update author identity', async () => {
    const change = createParsedChange();
    element.change = change;
    element.editMode = true;
    await element.updateComplete;
    const updateIdentityInChangeEditStub = stubRestApi(
      'updateIdentityInChangeEdit'
    ).resolves();
    const alertStub = sinon.stub();
    element.addEventListener('show-alert', alertStub);
    queryAndAssert(element, '#author-edit-label').dispatchEvent(
      new CustomEvent('changed', {detail: 'user <user@example.com>'})
    );
    assert.isTrue(
      updateIdentityInChangeEditStub.calledWith(
        42,
        'user',
        'user@example.com',
        'AUTHOR'
      )
    );
    await updateIdentityInChangeEditStub.lastCall.returnValue;
    await waitUntilCalled(alertStub, 'alertStub');
    assert.deepEqual(alertStub.lastCall.args[0].detail, {
      message: 'Saving identity and reloading ...',
      showDismiss: true,
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
      element = await fixture(html`<gr-change-metadata></gr-change-metadata>`);
      element.change = createParsedChange();
      element.revision = createRevision();
      await element.updateComplete;
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
      testResolver(pluginLoaderToken).loadPlugins([]);
      await element.updateComplete;
      assert.strictEqual(hookEl.plugin, plugin!);
      assert.strictEqual(hookEl.change, element.change);
      assert.strictEqual(hookEl.revision, element.revision);
    });
  });
});
