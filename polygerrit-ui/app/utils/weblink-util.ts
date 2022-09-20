/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CommitId, ServerInfo} from '../api/rest-api';

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

export function getPatchSetWeblink(
  commit?: CommitId,
  weblinks?: GeneratedWebLink[],
  config?: ServerInfo
): GeneratedWebLink | undefined {
  if (!commit) return undefined;
  const name = commit.slice(0, 7);
  const weblink = getBrowseCommitWeblink(weblinks, config);
  if (!weblink?.url) return {name};
  return {name, url: weblink.url};
}

// visible for testing
export function getCodeBrowserWeblink(weblinks: GeneratedWebLink[]) {
  // is an ordered allowed list of web link types that provide direct
  // links to the commit in the url property.
  const codeBrowserLinks = ['gitiles', 'browse', 'gitweb'];
  for (let i = 0; i < codeBrowserLinks.length; i++) {
    const weblink = weblinks.find(
      weblink => weblink.name === codeBrowserLinks[i]
    );
    if (weblink) return weblink;
  }
  return undefined;
}

// visible for testing
export function getBrowseCommitWeblink(
  weblinks?: GeneratedWebLink[],
  config?: ServerInfo
): GeneratedWebLink | undefined {
  if (!weblinks) return undefined;

  // Use primary weblink if configured and exists.
  const primaryWeblinkName = config?.gerrit?.primary_weblink_name;
  if (primaryWeblinkName) {
    const weblink = weblinks.find(link => link.name === primaryWeblinkName);
    if (weblink) return weblink;
  }

  return getCodeBrowserWeblink(weblinks);
}

export function getChangeWeblinks(
  weblinks?: GeneratedWebLink[],
  config?: ServerInfo
): GeneratedWebLink[] {
  if (!weblinks?.length) return [];
  const commitWeblink = getBrowseCommitWeblink(weblinks, config);
  return weblinks.filter(
    weblink => !commitWeblink?.name || weblink.name !== commitWeblink.name
  );
}
