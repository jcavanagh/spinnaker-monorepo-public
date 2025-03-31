import * as core from '@actions/core';
import dayjs from 'dayjs';
import * as git from '../git/git';
import * as fs from 'fs';
import * as os from 'os';
import * as util from '../util';
import * as uuid from 'uuid';

const monorepo = util.getInput('monorepo-location');
const docsRepo = util.getInput('docs-repo-location');

const partitions = [
  {
    title: 'Breaking Changes',
    pattern: /(.*?BREAKING CHANGE.*)/,
  },
  {
    title: 'Features',
    pattern: /((?:feat|feature)[(:].*)/,
  },
  {
    title: 'Configuration',
    pattern: /((?:config)[(:].*)/,
  },
  {
    title: 'Fixes',
    pattern: /((?:bug|fix)[(:].*)/,
  },
  {
    title: 'Other',
    pattern: /.*/,
  },
];

const conventionalCommit = /.+\((.+)\):\s*(.+)/;

export async function forVersion(
  version: string,
  previousVersion: string,
): Promise<Changelog> {
  if (!previousVersion) {
    const parsed = util.parseVersion(version);
    if (!parsed) {
      throw new Error(`Unable to parse version ${version}`);
    }

    const previousPatch = Math.max(parsed.patch - 1, 0);
    previousVersion = `${parsed.major}.${parsed.minor}.${previousPatch}`;
  }

  return generate(version, previousVersion);
}

async function generate(
  version: string,
  previousVersion: string,
): Promise<Changelog> {
  const parsed = util.parseVersion(version);
  if (!parsed) {
    throw new Error(`Failed to parse version ${version}`);
  }

  const tag = `spinnaker-release-${version}`;
  const prevTag = `spinnaker-release-${previousVersion}`;
  const commits = git.changelogCommits(tag, prevTag) || [];

  // Filter certain characters to ensure Markdown compat
  commits.map((line) => {
    line = line.replace('%', '%25');
    line = line.replace('\n', '%0A');
    line = line.replace('\r', '%0D');
    return line;
  });

  // Render changelog Markdown
  let markdown = `---
title: Spinnaker Release ${version}
date: ${dayjs().format('YYYY-MM-DD HH:MM:SS +0000')}
major_minor: ${parsed.major}.${parsed.minor}
version: ${version}
---
`;

  // Partition by severity of change
  let remainingLines = [...commits];
  const partitioned = partitions.map((part) => {
    const matching = remainingLines.filter((line) => part.pattern.test(line));
    remainingLines = remainingLines.filter((r) => !matching.includes(r));

    return {
      title: part.title,
      commits: matching.map((line) => {
        const [sha, ...rest] = line.split(' ');
        const shortSha = sha.substring(0, 8);
        let message = rest.join(' ');

        // Try to extract the conventional commit component
        let component = 'change';
        if (conventionalCommit.test(message)) {
          try {
            const matches = message.match(conventionalCommit);
            if (matches) {
              component = matches[1];
              message = matches[2];
            }
          } catch (e) {
            // No need to blow anything up over an error matching this
            core.warning(
              `Could not parse changelog message as conventional commit ${e.message}`,
            );
          }
        }

        return {
          sha,
          shortSha,
          component,
          message,
        };
      }),
    };
  });

  // Render partitions
  for (const partition of partitioned) {
    markdown += `\n#### ${partition.title}\n\n`;

    const lines = partition.commits
      .map((commit) => {
        const url = `https://github.com/${monorepo}/commit/${commit.sha}`;
        return `* **${commit.component}:** ${commit.message} ([${commit.shortSha}](${url}))`;
      })
      .sort();

    lines.forEach((line) => (markdown += `${line}\n`));
  }

  core.info("Found commits:");
  core.info(JSON.stringify(commits, null, 2));

  core.info("Changelog contents:");
  core.info(markdown);

  return new Changelog(version, previousVersion, commits, markdown);
}

export class Changelog {
  version: string;
  previousVersion: string;
  commits: string[];
  markdown: string;

  constructor(
    version: string,
    previousVersion: string,
    commits: string[],
    markdown: string,
  ) {
    this.version = version;
    this.previousVersion = previousVersion;
    this.commits = commits;
    this.markdown = markdown;
  }

  async publish() {
    const [owner, repo] = docsRepo.split('/', 2);
    const folder = uuid.v4();
    const tmpdir = process.env.RUNNER_TEMP || os.tmpdir();

    // Clone docs repo
    git.gitCmd(`git clone https://github.com/${docsRepo} ${folder}`, {
      cwd: tmpdir,
    });

    const docsCwd = `${tmpdir}/${folder}`;
    git.gitCmd(`git config user.email "${util.getInput('git-email')}"`, {
      cwd: docsCwd,
    });
    git.gitCmd(`git config user.name "${util.getInput('git-name')}"`, {
      cwd: docsCwd,
    });

    // Commit our changelog file
    const branch = `auto-changelog-spinnaker-${this.version}`;
    git.gitCmd(`git checkout -b ${branch}`, { cwd: docsCwd });

    const docsRepoPathTarget = `${docsCwd}/content/en/changelogs/${this.version}-changelog.md`;
    fs.writeFileSync(docsRepoPathTarget, this.markdown);

    const commitMsg = `Automatic changelog for Spinnaker ${this.version}`;
    git.gitCmd(`git add --all`, { cwd: docsCwd });
    git.gitCmd(`git commit -a -m '${commitMsg}'`, { cwd: docsCwd });

    const ghPat = util.getInput('github-pat');
    git.gitCmd(
      `git remote set-url origin https://${ghPat}@github.com/${docsRepo}.git`,
      { cwd: docsCwd },
    );
    git.gitCmd(`git push -f origin HEAD:${branch}`, { cwd: docsCwd });
    git.gitCmd(`git remote set-url origin https://github.com/${docsRepo}.git`, {
      cwd: docsCwd,
    });

    // Create PR
    return git.github.rest.pulls.create({
      owner,
      repo,
      head: branch,
      base: 'master',
      title: commitMsg,
    });
  }
}
