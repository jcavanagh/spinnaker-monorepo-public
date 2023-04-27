import { Storage } from '@google-cloud/storage';
import { CredentialBody } from 'google-auth-library';
import * as util from '../util';

let _storageClient: Storage = null!;

export function storageClient(): Storage {
  if (!_storageClient) {
    _storageClient = new Storage({
      credentials: JSON.parse(
        util.getInput('credentials-json'),
      ) as CredentialBody,
    });
  }

  return _storageClient;
}
