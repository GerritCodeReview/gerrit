/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CommentLinks, CommitId, RepoName, ServerInfo} from '../api/rest-api';
import {assertNever} from './common-util';

// TODO: Refactor to return only GeneratedWebLink[]
export type GenerateWebLinksCallback = (
  params: GenerateWebLinksParameters
) => GeneratedWebLink[] | GeneratedWebLink;

export type MapCommentLinksCallback = (patterns: CommentLinks) => CommentLinks;

export interface WebLink {
  name?: string;
  label: string;
  url: string;
}

export interface GeneratedWebLink {
  name?: string;
  label?: string;
  url?: string;
}

export enum WeblinkType {
  CHANGE = 'change',
  EDIT = 'edit',
  FILE = 'file',
  PATCHSET = 'patchset',
  RESOLVE_CONFLICTS = 'resolve-conflicts',
}

export interface GenerateWebLinksOptions {
  weblinks?: GeneratedWebLink[];
  config?: ServerInfo;
}

export interface GenerateWebLinksPatchsetParameters {
  type: WeblinkType.PATCHSET;
  repo: RepoName;
  commit?: CommitId;
  options?: GenerateWebLinksOptions;
}

export interface GenerateWebLinksResolveConflictsParameters {
  type: WeblinkType.RESOLVE_CONFLICTS;
  repo: RepoName;
  commit?: CommitId;
  options?: GenerateWebLinksOptions;
}

export interface GenerateWebLinksChangeParameters {
  type: WeblinkType.CHANGE;
  repo: RepoName;
  commit: CommitId;
  options?: GenerateWebLinksOptions;
}

export type GenerateWebLinksParameters =
  | GenerateWebLinksPatchsetParameters
  | GenerateWebLinksResolveConflictsParameters
  | GenerateWebLinksChangeParameters;

export function generateWeblinks(
  params: GenerateWebLinksParameters
): GeneratedWebLink[] | GeneratedWebLink {
  switch (params.type) {
    case WeblinkType.CHANGE:
      return getChangeWeblinks(params);
    case WeblinkType.PATCHSET:
      return getPatchSetWeblink(params);
    case WeblinkType.RESOLVE_CONFLICTS:
      return getResolveConflictsWeblinks(params);
    default:
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      assertNever(params, `Unsupported weblink ${(params as any).type}!`);
  }
}

function getPatchSetWeblink(
  params: GenerateWebLinksPatchsetParameters
): GeneratedWebLink {
  const {commit, options} = params;
  const {weblinks, config} = options || {};
  const name = commit && commit.slice(0, 7);
  const weblink = getBrowseCommitWeblink(weblinks, config);
  if (!weblink || !weblink.url) {
    return {name};
  } else {
    return {name, url: weblink.url};
  }
}

function getResolveConflictsWeblinks(
  params: GenerateWebLinksResolveConflictsParameters
): GeneratedWebLink[] {
  return params.options?.weblinks ?? [];
}

export function firstCodeBrowserWeblink(weblinks: GeneratedWebLink[]) {
  // is an ordered allowed list of web link types that provide direct
  // links to the commit in the url property.
  const codeBrowserLinks = ['gitiles', 'browse', 'gitweb'];
  for (let i = 0; i < codeBrowserLinks.length; i++) {
    const weblink = weblinks.find(
      weblink => weblink.name === codeBrowserLinks[i]
    );
    if (weblink) {
      return weblink;
    }
  }
  return null;
}

export function getBrowseCommitWeblink(
  weblinks?: GeneratedWebLink[],
  config?: ServerInfo
) {
  if (!weblinks) {
    return null;
  }
  let weblink;
  // Use primary weblink if configured and exists.
  if (config?.gerrit?.primary_weblink_name) {
    const primaryWeblinkName = config.gerrit.primary_weblink_name;
    weblink = weblinks.find(weblink => weblink.name === primaryWeblinkName);
  }
  if (!weblink) {
    weblink = firstCodeBrowserWeblink(weblinks);
  }
  if (!weblink) {
    return null;
  }
  return weblink;
}

export function getChangeWeblinks(
  params: GenerateWebLinksChangeParameters
): GeneratedWebLink[] {
  const weblinks = params.options?.weblinks;
  const config = params.options?.config;
  if (!weblinks || !weblinks.length) return [];
  const commitWeblink = getBrowseCommitWeblink(weblinks, config);
  return weblinks.filter(
    weblink =>
      !commitWeblink ||
      !commitWeblink.name ||
      weblink.name !== commitWeblink.name
  );
}
