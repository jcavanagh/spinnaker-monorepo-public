import type { Application, IManifest } from '@spinnaker/core';
import { ManifestReader } from '@spinnaker/core';

export interface IStageManifest {
  kind: string;
  apiVersion: string;
  metadata: {
    namespace: string;
    name: string;
  };
}

export interface IManifestParams {
  account: string;
  location: string;
  name: string;
}

export type IManifestCallback = (manifest: IManifest) => void;

export class CloudrunManifestService {
  public static subscribe(app: Application, params: IManifestParams, fn: IManifestCallback): () => void {
    CloudrunManifestService.updateManifest(params, fn);
    return app.onRefresh(null, () => CloudrunManifestService.updateManifest(params, fn));
  }

  private static updateManifest(params: IManifestParams, fn: IManifestCallback) {
    ManifestReader.getManifest(params.account, params.location, params.name).then((manifest) => fn(manifest));
  }

  public static manifestIdentifier(manifest: IStageManifest) {
    const kind = manifest.kind.toLowerCase();
    // manifest.metadata.namespace doesn't exist if it's a namespace being deployed
    const namespace = (manifest.metadata.namespace || '_').toLowerCase();
    const name = manifest.metadata.name.toLowerCase();
    const apiVersion = (manifest.apiVersion || '_').toLowerCase();
    // assuming this identifier is opaque and not parsed anywhere. Including the
    // apiVersion will prevent collisions with CRD kinds without having any visible
    // effect elsewhere
    return `${namespace} ${kind} ${apiVersion} ${name}`;
  }

  public static stageManifestToManifestParams(manifest: IStageManifest, account: string): IManifestParams {
    return {
      account,
      name: CloudrunManifestService.scopedKind(manifest) + ' ' + manifest.metadata.name,
      location: manifest.metadata.namespace == null ? '_' : manifest.metadata.namespace,
    };
  }

  private static apiGroup(manifest: IStageManifest): string {
    const parts = (manifest.apiVersion || '_').split('/');
    if (parts.length < 2) {
      return '';
    }
    return parts[0];
  }

  private static isCRDGroup(manifest: IStageManifest): boolean {
    return !CloudrunManifestService.BUILT_IN_GROUPS.includes(CloudrunManifestService.apiGroup(manifest));
  }

  private static scopedKind(manifest: IStageManifest): string {
    if (CloudrunManifestService.isCRDGroup(manifest)) {
      return manifest.kind + '.' + CloudrunManifestService.apiGroup(manifest);
    }

    return manifest.kind;
  }

  private static readonly BUILT_IN_GROUPS = [
    '',
    'core',
    'batch',
    'apps',
    'extensions',
    'storage.k8s.io',
    'apiextensions.k8s.io',
    'apiregistration.k8s.io',
    'policy',
    'scheduling.k8s.io',
    'settings.k8s.io',
    'authorization.k8s.io',
    'authentication.k8s.io',
    'rbac.authorization.k8s.io',
    'certifcates.k8s.io',
    'networking.k8s.io',
  ];
}
