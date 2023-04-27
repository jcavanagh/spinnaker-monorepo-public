import * as core from '@actions/core';
import * as Path from 'path';
import { parse } from 'yaml';
import { StoredYml } from '../gcp/stored_yml';

export interface IllegalVersion {
  reason: string;
  version: string;
}

export interface Version {
  alias: string;
  changelog: string;
  lastUpdate: number;
  minimumHalyardVersion: string;
  version: string;
}

export function empty(): VersionsDotYml {
  return new VersionsDotYml([], '', '', []);
}

export function fromYml(yamlStr: string): VersionsDotYml {
  const parsed = parse(yamlStr);
  if (isVersionsDotYml(parsed)) {
    return new VersionsDotYml(
      parsed.illegalVersions,
      parsed.latestHalyard,
      parsed.latestSpinnaker,
      parsed.versions
    );
  }
  throw new Error(`Invalid versions.yml - does not conform: ${yamlStr}`);
}

export async function fromCurrent(): Promise<VersionsDotYml> {
  const current = await empty().getCurrent();
  if (!current) {
    throw new Error('Unable to retrieve current versions.yml');
  }

  return fromYml(current);
}

export function isVersionsDotYml(obj: any): obj is VersionsDotYml {
  const requiredKeys = [
    'illegalVersions',
    'latestHalyard',
    'latestSpinnaker',
    'versions',
  ];
  const objKeys = new Set(Object.keys(obj));
  return requiredKeys.every((rk) => objKeys.has(rk) && obj[rk] != null);
}

export class VersionsDotYml extends StoredYml {
  illegalVersions: Array<IllegalVersion>;
  latestHalyard: string;
  latestSpinnaker: string;
  versions: Array<Version>;

  constructor(
    illegalVersions: Array<IllegalVersion>,
    latestHalyard: string,
    latestSpinnaker: string,
    versions: Array<Version>,
  ) {
    super();
    this.illegalVersions = illegalVersions;
    this.latestHalyard = latestHalyard;
    this.latestSpinnaker = latestSpinnaker;
    this.versions = versions;
  }

  addVersion(versionStr: string) {
    // Halyard builds with the rest of everything, and is now on the same version
    // So, we can generally simplify this block and only require one version string as input
    this.versions.push({
      alias: `v${versionStr}`,
      changelog: `https://spinnaker.io/changelogs/${versionStr}-changelog`,
      lastUpdate: new Date().getTime(),
      minimumHalyardVersion: versionStr,
      version: versionStr,
    });
  }

  removeVersion(versionStr: string) {
    this.versions = this.versions.filter((v) => v.version !== versionStr);
  }

  override getBucket(): string {
    return core.getInput('bucket');
  }

  override getBucketFilePath(): string {
    return Path.join(core.getInput('versions-yml-bucket-path'), this.getFilename());
  }

  override getFilename(): string {
    return `versions.yml`;
  }
}
