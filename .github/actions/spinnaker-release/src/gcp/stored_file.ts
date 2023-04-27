import * as artifact from '@actions/artifact';
import * as core from '@actions/core';
import { storageClient } from './storage';
import * as fs from 'fs';
import * as os from 'os';
import * as Path from 'path';

export abstract class StoredFile {
  abstract getBucket(): string;
  abstract getBucketFilePath(): string;
  abstract getFilename(): string;

  getTmpdirPath(): string {
    return process.env['RUNNER_TEMP'] ?? os.tmpdir();
  }

  async getCurrent(): Promise<string | null> {
    const response = await storageClient()
      .bucket(this.getBucket())
      .file(this.getBucketFilePath())
      .download();
    return response.toString();
  }

  async publish(): Promise<void> {
    await storageClient()
      .bucket(this.getBucket())
      .file(this.getBucketFilePath())
      .save(this.toString());
    core.info(
      `Successfully published ${this.getFilename()} to GCP: ${this.getBucket()}/${this.getBucketFilePath()}`,
    );
  }

  async uploadGhaArtifact(
    artifactName: string,
  ): Promise<artifact.UploadResponse> {
    // This needs to be written to a tmpfile, then uploaded
    const tmpfilePath = Path.join(this.getTmpdirPath(), this.getFilename());
    fs.writeFileSync(tmpfilePath, this.toString());

    const resp = await artifact
      .create()
      .uploadArtifact(artifactName, [tmpfilePath], '/', {});
    core.info(`Successfully published ${this.getFilename()} to GHA`);
    return resp;
  }
}
