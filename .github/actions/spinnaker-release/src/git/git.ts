import * as core from '@actions/core';
import { execSync } from 'child_process';

export interface Tag {
  name: string;
  sha: string;
}

function gitCmd(command: string): string | undefined {
  const out = gitCmdMulti(command);
  return out ? out[0] : undefined;
}

function gitCmdMulti(command: string): Array<string> | undefined {
  try {
    const out = execSync(command, {}).toString();
    const lines = out.split(/\r?\n/);
    return lines;
  } catch (e) {
    core.error('Failed to execute Git command');
    core.error(e);
    return undefined;
  }
}

export function head(): string | undefined {
  return gitCmd('git rev-parse HEAD');
}

export function parseTag(name: string): Tag | undefined {
  const sha = gitCmd(`git rev-parse ${name}`);
  if (!sha) {
    core.error(`Failed to resolve git tag ${name}`);
    return undefined;
  }

  return {
    name,
    sha,
  };
}

export function parseTagPrefixed(prefix: string): Tag | undefined {
  // Find one unique tag with the provided prefix, if exists, and parse it
  // If there are multiple candidates with different shas, fail
  // If there are multiple candidates with the same sha, return that sha and the first tag name alphabetically
  if (!prefix) {
    throw new Error(`Tag prefix must not be empty`);
  }

  const tags = gitCmdMulti(`git tag`)
    ?.filter((it) => it.startsWith(prefix))
    ?.sort();

  if (!tags) {
    return undefined;
  }

  if (tags.length == 1) {
    return {
      name: tags[0],
      sha: gitCmd(`git rev-parse ${tags[0]}`)!,
    };
  }

  const resolvedShas = new Set(
    tags.map((it) => gitCmd(`git rev-parse ${it}`)!),
  );

  if (resolvedShas.size == 1) {
    return {
      name: tags[0],
      sha: resolvedShas.values().next().value,
    };
  }

  return;
}
