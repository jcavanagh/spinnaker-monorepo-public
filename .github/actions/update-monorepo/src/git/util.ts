import { execWithOutput, getInput } from '../util';
import { getOctokit } from '@actions/github';
import { MergeResult } from './types';

export const github = getOctokit(getInput('github-token'));

// Convert a remote ref to a local ref
export function getLocalRef() {
  const remoteRef = getInput('remote-ref');
  return remoteRef == 'master' ? 'main' : remoteRef;
}

export function getMergeBranchName() {
  return `auto-merge-${getLocalRef()}`;
}

export function getRepoOwnerAndName() {
  const currentRepo = process.env.GITHUB_REPOSITORY || 'spinnaker/spinnaker';
  return currentRepo.split('/');
}

// If any PR with the source branch as `auto-merge-<ref>` exists and is open, return it
export async function getMergeBranchPrIfExists() {
  const [owner, repo] = getRepoOwnerAndName();
  const pulls = await github.rest.pulls.list({
    owner,
    repo,
    state: 'open',
    // For some reason, heads need an organization/user-org prefix
    head: `${getRepoOwnerAndName()[0]}:${getMergeBranchName()}`,
  });
  return pulls.data[0];
}

export async function checkoutMergeBranch() {
  await execWithOutput(`git checkout -b ${getMergeBranchName()}`);
}

export async function pushMergeBranch() {
  // This intentionally force-pushes, as it needs to overwrite the old, stale branch
  await execWithOutput(`git push -f origin HEAD:${getMergeBranchName()}`);
}

export function generatePrBody(results: MergeResult[]) {
  const title = `## Auto-merge results for branch: ${getLocalRef()}`;

  let body = '';
  for (const result of results) {
    if (!result.isClean) {
      // Print instructions on how to manually resolve the conflicts
      body += `\n\nMerge failed for \`${result.repo}\` - run the following command, resolve conflicts, and push:\n`;

      // By default, the pull script merges `master`/`main`.  Release refs need an additional argument.
      const refArg = getLocalRef() == 'main' ? '' : `-r ${getLocalRef()} `;
      body += '```zsh\n';
      body += `./scripts/pull.sh ${refArg}${result.repo}\n`;
      // The Git editor script will format the message with individual commits imported, so it's nice to read
      // Manual resolutions should use the output of that script as the commit message
      body +=
        '# Resolve any conflicts, then commit with the following command:\n';
      body += `git commit -a -F SUBTREE_MERGE_MSG\n`;
      body += '```\n';
      body += `<details><summary>Output</summary>\`\`\`zsh\n${result.exec.out}\n\`\`\`</details>`;
    }
  }

  // Collect successful merges into one line
  const successes = results.filter((it) => it.isClean).map((it) => it.repo);
  body += `\n\n`;
  body += `Successfully merged: ${successes.join(', ')}`;

  return title + body;
}

export async function createMergeBranchPr(results: MergeResult[]) {
  // Generate a human-readable PR body from the results
  const body = generatePrBody(results);

  // If every merge failed, we won't have any diff - GitHub doesn't allow diff-less pulls
  const allFailed = results.every((it) => !it.isClean);
  if (allFailed) {
    console.log(body);
    throw new Error(
      'All merges failed - merge branch must be updated manually.',
    );
  }

  // Push whatever merge result we got to the auto-merge-<foo> branch at the origin
  await pushMergeBranch();

  // Create a pull from that branch we just pushed
  const [owner, repo] = getRepoOwnerAndName();
  return github.rest.pulls.create({
    owner,
    repo,
    head: getMergeBranchName(),
    base: getLocalRef(),
    title: `Auto-merge of individual repos (${getLocalRef()})`,
    body,
  });
}

export async function setup() {
  await execWithOutput('git -v');
  await execWithOutput(`git config user.email "${getInput('git-email')}"`);
  await execWithOutput(`git config user.name "${getInput('git-name')}"`);

  const existingMergeBranch = await getMergeBranchPrIfExists();
  if (existingMergeBranch) {
    console.log(
      `Auto-merge PR already exists - not overwriting: ${existingMergeBranch.html_url}
      Please resolve the open PR or close it to allow this script to regenerate it.`,
    );
    return;
  }
  await checkoutMergeBranch();
}
