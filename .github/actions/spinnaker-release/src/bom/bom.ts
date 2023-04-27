import * as core from '@actions/core';
import { Service } from './service';
import { StoredYml } from '../gcp/stored_yml';
import * as Path from 'path';

export class Bom extends StoredYml {
  artifactSources: Map<string, string>;
  dependencies: Map<string, Map<string, string>>;
  services: Map<string, Map<string, string>>;
  timestamp: string;
  version: string;

  constructor(version: string) {
    super();
    this.artifactSources = this.getDefaultArtifactSources();
    this.dependencies = this.getDefaultDependencies();
    this.services = new Map<string, Map<string, string>>();
    this.timestamp = new Date().toISOString();
    this.version = version;
  }

  getDefaultArtifactSources(): Map<string, string> {
    return new Map(
      Object.entries({
        debianRepository: core.getInput('as-debian-repository'),
        dockerRegistry: core.getInput('as-docker-registry'),
        gitPrefix: core.getInput('as-git-prefix'),
        googleImageProject: core.getInput('as-google-image-project'),
      }),
    );
  }

  getDefaultDependencies(): Map<string, Map<string, string>> {
    return new Map(
      Object.entries({
        consul: new Map(
          Object.entries({
            version: core.getInput('dep-consul-version'),
          }),
        ),
        redis: new Map(
          Object.entries({
            version: core.getInput('dep-redis-version'),
          }),
        ),
        vault: new Map(
          Object.entries({
            version: core.getInput('dep-vault-version'),
          }),
        ),
      }),
    );
  }

  setArtifactSources(sources: Map<string, string>) {
    this.artifactSources = sources;
  }

  setDependency(dep: string, version: string) {
    this.dependencies.set(
      dep,
      new Map(
        Object.entries({
          version: version,
        }),
      ),
    );
  }

  setService(service: Service) {
    this.services.set(
      service.name,
      new Map(
        Object.entries({
          commit: service.getCommit(),
          version: service.getVersion(),
        }),
      ),
    );
  }

  setTimestamp(timestamp: string) {
    this.timestamp = timestamp;
  }

  override getBucket(): string {
    return core.getInput('bom-bucket-path');
  }

  override getBucketFilePath(): string {
    return Path.join(this.getBucket(), this.getFilename());
  }

  override getFilename(): string {
    return `${this.version}.yml`;
  }
}
