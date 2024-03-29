/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.InstanceGroupManagersSetAutoHealingRequest
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.RegionInstanceGroupManagersSetAutoHealingRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationsRegistry
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor
import org.springframework.beans.factory.annotation.Autowired

class DeleteGoogleAutoscalingPolicyAtomicOperation extends GoogleAtomicOperation<Void>{

  private static final String BASE_PHASE = "DELETE_SCALING_POLICY"
  private final DeleteGoogleAutoscalingPolicyDescription description

  private GoogleClusterProvider googleClusterProvider

  private GoogleOperationPoller googleOperationPoller

  AtomicOperationsRegistry atomicOperationsRegistry

  OrchestrationProcessor orchestrationProcessor

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DeleteGoogleAutoscalingPolicyAtomicOperation(DeleteGoogleAutoscalingPolicyDescription description, @Autowired GoogleClusterProvider googleClusterProvider, @Autowired GoogleOperationPoller googleOperationPoller, @Autowired AtomicOperationsRegistry atomicOperationsRegistry, @Autowired OrchestrationProcessor orchestrationProcessor) {
    this.description = description
    this.googleClusterProvider = googleClusterProvider
    this.googleOperationPoller = googleOperationPoller
    this.atomicOperationsRegistry = atomicOperationsRegistry
    this.orchestrationProcessor = orchestrationProcessor
  }

  /**
   * Autoscaling policy:
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteScalingPolicy": { "serverGroupName": "autoscale-regional", "credentials": "my-google-account", "region": "us-central1" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteScalingPolicy": { "serverGroupName": "autoscale-zonal", "credentials": "my-google-account", "region": "us-central1" }} ]' localhost:7002/gce/ops
   *
   * AutoHealing policy:
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteScalingPolicy": { "serverGroupName": "autoscale-zonal", "credentials": "my-google-account", "region": "us-central1", "deleteAutoHealingPolicy": true }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {


    def credentials = description.credentials
    def serverGroupName = description.serverGroupName
    def project = credentials.project
    def compute = credentials.compute
    def accountName = description.accountName
    def region = description.region
    def serverGroup = GCEUtil.queryServerGroup(googleClusterProvider, accountName, region, serverGroupName)
    def isRegional = serverGroup.regional
    def zone = serverGroup.zone

    if (description.deleteAutoHealingPolicy) {
      task.updateStatus BASE_PHASE, "Initializing deletion of autoHealing policy for $description.serverGroupName..."
      if (isRegional) {
        def request = new RegionInstanceGroupManagersSetAutoHealingRequest().setAutoHealingPolicies([])
        def deleteOp = timeExecute(
          compute.regionInstanceGroupManagers().setAutoHealingPolicies(project, region, serverGroupName, request),
          "compute.regionInstanceGroupManagers.setAutoHealingPolicies",
          GoogleExecutor.TAG_SCOPE, GoogleExecutor.SCOPE_REGIONAL, GoogleExecutor.TAG_REGION, region)
        googleOperationPoller.waitForRegionalOperation(compute, project, region,
          deleteOp.getName(), null, task, "autoHealing policy for $serverGroupName", BASE_PHASE)
        deletePolicyMetadata(compute, credentials, project, GCEUtil.buildRegionalServerGroupUrl(project, region, serverGroupName))
      } else {
        def request = new InstanceGroupManagersSetAutoHealingRequest().setAutoHealingPolicies([])
        def deleteOp = timeExecute(
          compute.instanceGroupManagers().setAutoHealingPolicies(project, zone, serverGroupName, request),
          "compute.instanceGroupManagers.setAutoHealingPolicies",
          GoogleExecutor.TAG_SCOPE, GoogleExecutor.SCOPE_ZONAL, GoogleExecutor.TAG_ZONE, zone)
        googleOperationPoller.waitForZonalOperation(compute, project, zone,
          deleteOp.getName(), null, task, "autoHealing policy for $serverGroupName", BASE_PHASE)
        deletePolicyMetadata(compute, credentials, project, GCEUtil.buildZonalServerGroupUrl(project, zone, serverGroupName))
      }
      task.updateStatus BASE_PHASE, "Done deleting autoHealing policy for $serverGroupName."
    } else {
      task.updateStatus BASE_PHASE, "Initializing deletion of scaling policy for $description.serverGroupName..."
      if (isRegional) {
        def deleteOp = timeExecute(
            compute.regionAutoscalers().delete(project, region, serverGroupName),
            "compute.regionAutoscalers.delete",
          GoogleExecutor.TAG_SCOPE, GoogleExecutor.SCOPE_REGIONAL, GoogleExecutor.TAG_REGION, region)
        googleOperationPoller.waitForRegionalOperation(compute, project, region,
          deleteOp.getName(), null, task, "autoScaling policy for $serverGroupName", BASE_PHASE)
        deletePolicyMetadata(compute, credentials, project, GCEUtil.buildRegionalServerGroupUrl(project, region, serverGroupName))
      } else {
        def deleteOp = timeExecute(
            compute.autoscalers().delete(project, zone, serverGroupName),
            "compute.autoscalers.delete",
            GoogleExecutor.TAG_SCOPE, GoogleExecutor.SCOPE_ZONAL, GoogleExecutor.TAG_ZONE, zone)
        googleOperationPoller.waitForZonalOperation(compute, project, zone,
          deleteOp.getName(), null, task, "autoScaling policy for $serverGroupName", BASE_PHASE)
        deletePolicyMetadata(compute, credentials, project, GCEUtil.buildZonalServerGroupUrl(project, zone, serverGroupName))
      }
      task.updateStatus BASE_PHASE, "Done deleting scaling policy for $serverGroupName."
    }

    return null
  }

  void deletePolicyMetadata(Compute compute,
                            GoogleNamedAccountCredentials credentials,
                            String project,
                            String groupUrl) {
    def groupName = Utils.getLocalName(groupUrl)
    def groupRegion = Utils.getRegionFromGroupUrl(groupUrl)

    String templateUrl = null
    switch (Utils.determineServerGroupType(groupUrl)) {
      case GoogleServerGroup.ServerGroupType.REGIONAL:
        templateUrl = timeExecute(
          compute.regionInstanceGroupManagers().get(project, groupRegion, groupName),
          "compute.regionInstanceGroupManagers.get",
          GoogleExecutor.TAG_SCOPE, GoogleExecutor.SCOPE_REGIONAL, GoogleExecutor.TAG_REGION, groupRegion)
          .getInstanceTemplate()
        break
      case GoogleServerGroup.ServerGroupType.ZONAL:
        def groupZone = Utils.getZoneFromGroupUrl(groupUrl)
        templateUrl = timeExecute(
          compute.instanceGroupManagers().get(project, groupZone, groupName),
          "compute.instanceGroupManagers.get",
          GoogleExecutor.TAG_SCOPE, GoogleExecutor.SCOPE_ZONAL, GoogleExecutor.TAG_ZONE, groupZone)
          .getInstanceTemplate()
        break
      default:
        throw new IllegalStateException("Server group referenced by ${groupUrl} has illegal type.")
        break
    }

    InstanceTemplate template = timeExecute(
      compute.instanceTemplates().get(project, Utils.getLocalName(templateUrl)),
      "compute.instancesTemplates.get",
      GoogleExecutor.TAG_SCOPE, GoogleExecutor.SCOPE_GLOBAL)
    def instanceDescription = GCEUtil.buildInstanceDescriptionFromTemplate(project, template)

    def templateOpMap = [
      image              : instanceDescription.image,
      instanceType       : instanceDescription.instanceType,
      credentials        : credentials.getName(),
      disks              : instanceDescription.disks,
      instanceMetadata   : instanceDescription.instanceMetadata,
      tags               : instanceDescription.tags,
      network            : instanceDescription.network,
      subnet             : instanceDescription.subnet,
      serviceAccountEmail: instanceDescription.serviceAccountEmail,
      authScopes         : instanceDescription.authScopes,
      preemptible        : instanceDescription.preemptible,
      automaticRestart   : instanceDescription.automaticRestart,
      onHostMaintenance  : instanceDescription.onHostMaintenance,
      region             : groupRegion,
      serverGroupName    : groupName
    ]

    if (instanceDescription.minCpuPlatform) {
      templateOpMap.minCpuPlatform = instanceDescription.minCpuPlatform
    }

    if (templateOpMap?.instanceMetadata) {
      templateOpMap.instanceMetadata.remove(GCEUtil.AUTOSCALING_POLICY)
      def converter = atomicOperationsRegistry.getAtomicOperationConverter('modifyGoogleServerGroupInstanceTemplateDescription', 'gce')
      AtomicOperation templateOp = converter.convertOperation(templateOpMap)
      orchestrationProcessor.process('gce', [templateOp], UUID.randomUUID().toString())
    }
  }
}
