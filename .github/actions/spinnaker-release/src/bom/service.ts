import * as core from '@actions/core';
import * as git from '../git/git';

// Default overrides for certain BoM entries
// Will be overridden by action input, if provided
export interface Overrides {
  commit?: string;
  version?: string;
}

export abstract class Service {
  abstract name: string;
  inputOverrides?: Overrides;
  overrides?: Overrides;

  constructor(overrides?: Overrides) {
    this.overrides = overrides;
    this.inputOverrides = this.getInputOverrides();
  }

  getBranch(): string {
    // If a `branch` input is not provided, attempt to infer it from the `version`
    const inputBranch = core.getInput('branch');
    if (!inputBranch) {
      const version = core.getInput('version');
      const versionParts = version.split('.');
      if (versionParts.length == 3) {
        // Release branches are named release-<year>.<major>.x
        return `release-${versionParts[0]}.${versionParts[1]}.x`;
      } else {
        throw new Error(
          `Cannot infer branch to determine which service versions to use: ${version} - please specify in inputs.`,
        );
      }
    } else {
      return inputBranch;
    }
  }

  getLastTag(): git.Tag | undefined {
    return git.parseTagPrefixed(`${this.name}-${this.getBranch()}-`);
  }

  getVersion(): string {
    const version =
      this.inputOverrides?.version ||
      this.overrides?.version ||
      this.getLastTag()?.name;
    if (!version) {
      throw new Error(`Unable to resolve version for service ${this.name}`);
    }
    return version;
  }

  getCommit(): string {
    const commit =
      this.inputOverrides?.commit ||
      this.overrides?.commit ||
      this.getLastTag()?.sha;
    if (!commit) {
      throw new Error(`Unable to resolve commit for service ${this.name}`);
    }
    return commit;
  }

  private getInputOverrides(): Overrides | undefined {
    const overridesInput: string = core.getInput('service-overrides');

    if (overridesInput) {
      for (const token in overridesInput.trim().split(',')) {
        const subtokens = token.trim().split(':');
        if (subtokens.length != 2) {
          core.warning(`Invalid BoM override from inputs: ${token}`);
        } else {
          const name = subtokens[0];
          const version = subtokens[1];
          if (name == this.name) {
            const tag = git.parseTag(version);
            if (tag) {
              return {
                version: tag.name,
                commit: tag.sha,
              };
            } else {
              throw new Error(
                `Could not resolve tag specifed as service override: ${name}:${version}`,
              );
            }
          }
        }
      }
    }

    return undefined;
  }
}
