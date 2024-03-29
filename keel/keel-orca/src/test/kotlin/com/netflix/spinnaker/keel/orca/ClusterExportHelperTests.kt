package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.TaskStatus.SUCCEEDED
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.Capacity
import com.netflix.spinnaker.keel.clouddriver.model.InstanceCounts
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.core.parseMoniker
import com.netflix.spinnaker.keel.retrofit.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.tags.EntityRef
import com.netflix.spinnaker.keel.tags.EntityTag
import com.netflix.spinnaker.keel.tags.EntityTags
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import strikt.assertions.isSuccess
import java.time.Duration
import java.time.Instant

class ClusterExportHelperTests : JUnit5Minutests {
  class Fixture {
    val cloudDriverService = mockk<CloudDriverService>()
    val orcaService = mockk<OrcaService>()

    val serverGroup = ActiveServerGroup(
      name = "keel-test-v001",
      region = "us-east-1",
      zones = listOf("a", "b", "c").map { "us-east-1$it" }.toSet(),
      image = ActiveServerGroupImage(imageId = "foo", appVersion = "bar", baseImageName = "baz", name = "name", imageLocation = "location", description = "description"),
      launchConfig = LaunchConfig(
        ramdiskId = "diskId",
        ebsOptimized = false,
        imageId = "imageId",
        instanceType = "instanceType",
        keyName = "keyPair",
        iamInstanceProfile = "iamRole",
        instanceMonitoring = InstanceMonitoring(false)
      ),
      asg = AutoScalingGroup(
        autoScalingGroupName = "keel-test-v001",
        defaultCooldown = 0,
        healthCheckType = "EC2",
        healthCheckGracePeriod = 0,
        suspendedProcesses = emptySet(),
        enabledMetrics = emptySet(),
        tags = emptySet(),
        terminationPolicies = emptySet(),
        vpczoneIdentifier = ""
      ),
      scalingPolicies = emptyList(),
      vpcId = "foo",
      targetGroups = emptySet(),
      loadBalancers = emptySet(),
      capacity = Capacity(1, 1, 1),
      cloudProvider = "aws",
      securityGroups = emptySet(),
      accountName = "test",
      moniker = parseMoniker("keek-test-v001"),
      instanceCounts = InstanceCounts(1, 1, 0, 0, 0, 0),
      createdTime = 1544656135184
    )

    val taskEntityTags = EntityTags(
      id = "aws:servergroup:${serverGroup.name}:1234567890123:us-east-1",
      idPattern = "{{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}",
      tags = listOf(
        EntityTag(
          name = "spinnaker:metadata",
          namespace = "spinnaker",
          valueType = "object",
          value = mapOf(
            "executionId" to "01E609548XWA7ZBP5M5FGMZ964",
            "executionType" to "orchestration"
          )
        )
      ),
      tagsMetadata = emptyList(),
      entityRef = EntityRef(
        cloudProvider = "aws",
        application = "keel",
        accountId = "1234567890123",
        account = "test",
        region = "us-east-1",
        entityType = "servergroup",
        entityId = serverGroup.name
      )
    )

    val pipelineEntityTags = taskEntityTags.copy(
      tags = listOf(
        EntityTag(
          name = "spinnaker:metadata",
          namespace = "spinnaker",
          valueType = "object",
          value = mapOf(
            "executionId" to "01E609548XWA7ZBP5M5FGMZ964",
            "executionType" to "pipeline"
          )
        )
      )
    )

    val deployContextWithOptionalFields = mapOf(
      "strategy" to "redblack",
      "scaleDown" to false,
      "maxRemainingAsgs" to 3,

      //
      // optional fields
      //
      "rollback" to mapOf("onFailure" to true),
      "delayBeforeDisableSec" to 10,
      "delayBeforeScaleDownSec" to 10
    )

    val deployContextWithoutOptionalFields = mapOf(
      "strategy" to "redblack",
      "scaleDown" to false,
      "maxRemainingAsgs" to 3
    )


    fun orcaTaskExecution(context : Map<String, Any>) = ExecutionDetailResponse(
      id = "01E609548XWA7ZBP5M5FGMZ964",
      name = "A keel deployment task",
      application = "keel",
      buildTime = Instant.now() - Duration.ofHours(1),
      startTime = Instant.now() - Duration.ofHours(1),
      endTime = Instant.now() - Duration.ofMinutes(30),
      status = SUCCEEDED,
      execution = OrcaExecutionStages(
        stages = listOf(
          mapOf(
            "type" to "createServerGroup",
            "context" to context
          )
        )
      )
    )

    val orcaPipelineExecution = ExecutionDetailResponse(
      id = "01E609548XWA7ZBP5M5FGMZ964",
      name = "A user's deployment pipeline",
      application = "keel",
      buildTime = Instant.now() - Duration.ofHours(1),
      startTime = Instant.now() - Duration.ofHours(1),
      endTime = Instant.now() - Duration.ofMinutes(30),
      status = SUCCEEDED,
      stages = listOf(
        mapOf(
          "type" to "deploy",
          "context" to mapOf(
            "clusters" to listOf(
              mapOf(
                "strategy" to "redblack",
                "rollback" to mapOf(
                  "onFailure" to true
                ),
                "scaleDown" to false,
                "maxRemainingAsgs" to 1,
                "delayBeforeDisableSec" to 5,
                "delayBeforeScaleDownSec" to 5
              )
            )
          )
        )
      )
    )

    val subject = ClusterExportHelper(cloudDriverService, orcaService)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("discover deployment strategy") {
      before {
        coEvery { cloudDriverService.activeServerGroup(any(), "keel", "test", any(), any(), "aws") } returns serverGroup
      }

      context("when entity tags indicate deployment done by orchestration task") {
        before {
          coEvery {
            cloudDriverService.getEntityTags("aws", "test", "keel", "servergroup", any())
          } returns listOf(taskEntityTags)
        }

        context("when task execution context contains all optional fields") {
          before {
            coEvery {
              orcaService.getOrchestrationExecution("01E609548XWA7ZBP5M5FGMZ964", any())
            } returns orcaTaskExecution(deployContextWithOptionalFields)
          }

          test("retrieves current deployment strategy from task execution") {
            val deploymentStrategy = runBlocking {
              subject.discoverDeploymentStrategy(
                cloudProvider = "aws",
                account = "test",
                application = "keel",
                serverGroupName = "keel-test-v001"
              )
            }

            expectThat(deploymentStrategy)
              .isA<RedBlack>()
              .and {
                get { maxServerGroups }.isEqualTo(3)
                get { delayBeforeDisable }.isEqualTo(Duration.ofSeconds(10))
                get { delayBeforeScaleDown }.isEqualTo(Duration.ofSeconds(10))
              }
          }
        }

        context("when task execution context contains none of the optional fields") {
          before {
            coEvery {
              orcaService.getOrchestrationExecution("01E609548XWA7ZBP5M5FGMZ964", any())
            } returns orcaTaskExecution(deployContextWithoutOptionalFields)
          }

          test("retrieves current deployment strategy from task execution") {
            val deploymentStrategy = runBlocking {
              subject.discoverDeploymentStrategy(
                cloudProvider = "aws",
                account = "test",
                application = "keel",
                serverGroupName = "keel-test-v001"
              )
            }

            expectThat(deploymentStrategy)
              .isA<RedBlack>()
              .and {
                get { maxServerGroups }.isEqualTo(3)
                get { delayBeforeDisable }.isNull()
                get { delayBeforeScaleDown }.isNull()
              }
          }
        }

        context("when task execution is not found") {
          before {
            coEvery {
              orcaService.getOrchestrationExecution("01E609548XWA7ZBP5M5FGMZ964", any())
            } throws RETROFIT_NOT_FOUND
          }

          test("does not throw an exception, but returns null") {
            expectCatching {
              runBlocking {
                subject.discoverDeploymentStrategy(
                  cloudProvider = "aws",
                  account = "test",
                  application = "keel",
                  serverGroupName = "keel-test-v001"
                )
              }
            }.isSuccess()
              .isNull()
          }
        }
      }

      context("when entity tags indicate deployment done by pipeline") {
        before {
          coEvery {
            cloudDriverService.getEntityTags("aws", "test", "keel", "servergroup", any())
          } returns listOf(pipelineEntityTags)
        }

        context("when pipeline execution is found") {
          before {
            coEvery {
              orcaService.getPipelineExecution("01E609548XWA7ZBP5M5FGMZ964", any())
            } returns orcaPipelineExecution
          }

          test("retrieves current deployment strategy from pipeline execution") {
            val deploymentStrategy = runBlocking {
              subject.discoverDeploymentStrategy(
                cloudProvider = "aws",
                account = "test",
                application = "keel",
                serverGroupName = "keel-test-v001"
              )
            }

            expectThat(deploymentStrategy)
              .isA<RedBlack>()
              .and {
                get { maxServerGroups }.isEqualTo(1)
                get { delayBeforeDisable }.isEqualTo(Duration.ofSeconds(5))
                get { delayBeforeScaleDown }.isEqualTo(Duration.ofSeconds(5))
              }
          }
        }

        context("when pipeline execution is not found") {
          before {
            coEvery {
              orcaService.getPipelineExecution("01E609548XWA7ZBP5M5FGMZ964", any())
            } throws RETROFIT_NOT_FOUND
          }

          test("does not throw an exception, but returns null") {
            expectCatching {
              runBlocking {
                subject.discoverDeploymentStrategy(
                  cloudProvider = "aws",
                  account = "test",
                  application = "keel",
                  serverGroupName = "keel-test-v001"
                )
              }
            }.isSuccess()
              .isNull()
          }
        }
      }
    }
  }
}
