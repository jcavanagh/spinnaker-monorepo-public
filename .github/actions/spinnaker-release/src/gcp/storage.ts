import * as core from '@actions/core';
import { Storage } from '@google-cloud/storage';
import { CredentialBody } from 'google-auth-library';

let _storageClient: Storage = null!;

export function storageClient(): Storage {
  if (!_storageClient) {
    _storageClient = new Storage({
      credentials: JSON.parse(
        core.getInput('credentials_json'),
      ) as CredentialBody,
    });
  }

  return _storageClient;
}
