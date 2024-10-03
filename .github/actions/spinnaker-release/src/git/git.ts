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

export function findTag(service: string, branch: string): Tag | undefined {
  // Find the newest tag with the provided prefix, if exists, and parse it
  if (!service) {
    throw new Error(`Tag service must not be empty`);
  }

  if (!branch) {
    throw new Error(`Tag branch must not be empty`);
  }

  const prefix = `${service}-${branch}-`;
  const tags = gitCmdMulti(`git tag`)
    ?.filter((it) => it.startsWith(prefix))
    ?.filter((it) => {
      // Ensure this matches standard tag format - all other tags should be disregarded
      const split = it.split('-');
      return split.length >= 3 && !isNaN(parseInt(split.slice(-1)[0], 10));
    })
    ?.sort((a, b) => {
      // Basic sorting fails beyond single-digit numbers, e.g. 10 < 2, so sort by parts
      // All tags should have three parts - service-branch-number, and the tags are prefiltered to fit that format
      // So, all we need to do is compare the third value
      const aSplit = a.split('-');
      const bSplit = b.split('-');
      const aNum = parseInt(aSplit.slice(-1)[0]);
      const bNum = parseInt(bSplit.slice(-1)[0]);

      // Sort in reverse order
      if (aNum > bNum) return -1;
      if (aNum < bNum) return 1;
      return 0;
    });

  if (!tags || !tags.length) {
    core.warning(`No tags found for prefix ${prefix}`);
    return undefined;
  }

  return {
    name: tags[0],
    sha: gitCmd(`git rev-parse ${tags[0]}`)!,
  };
}
