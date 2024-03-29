package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PENDING
import com.netflix.spinnaker.keel.events.ArtifactDeployedNotification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify

class ArtifactDeployedListenerTests : JUnit5Minutests {
  class Fixture {
    val resource = resource()
    val resourceSpy: Resource<DummyResourceSpec> = spyk(resource)
    val config = deliveryConfig(resource = resourceSpy)
    val artifact = config.artifacts.first()
    val repository = mockk<KeelRepository>(relaxUnitFun = true)
    val publisher: EventPublisher = mockk(relaxUnitFun = true)

    val event = ArtifactVersionDeployed(
      resourceId = resourceSpy.id,
      artifactVersion = "1.1.1"
    )

    val subject = ArtifactDeployedListener(repository, publisher)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    before {
      every { repository.getResource(resource.id) } returns resourceSpy
      every { repository.deliveryConfigFor(resource.id) } returns config
      every { repository.environmentFor(resource.id) } returns config.environments.first()
      every { repository.getArtifactPromotionStatus(config, any(), event.artifactVersion, any()) } returns PENDING
    }

    after {
      clearAllMocks()
    }

    context("no artifact associated with the resource") {
      test("nothing is marked as deployed") {
        subject.onArtifactVersionDeployed(event)
        verify(exactly = 0) { repository.isApprovedFor(config, any(), event.artifactVersion, any()) }
        verify(exactly = 0) { repository.markAsSuccessfullyDeployedTo(config, any(), event.artifactVersion, any()) }
      }
    }

    context("an artifact is associated with a resource") {
      before {
        every { resourceSpy.findAssociatedArtifact(config) } returns artifact
      }
      context("artifact is approved for env") {
        before {
          every { repository.isApprovedFor(any(), any(), event.artifactVersion, any()) } returns true
        }

        context("artifact has been marked currently deployed") {
          before {
            every { repository.getArtifactPromotionStatus(any(), any(), event.artifactVersion, any()) } returns CURRENT
          }

          test("artifact is not marked as deployed again") {
            subject.onArtifactVersionDeployed(event)
            verify(exactly = 0) { repository.markAsSuccessfullyDeployedTo(any(), any(), event.artifactVersion, any()) }
          }
        }

        context("artifact has not been marked currently deployed") {
          test("version is marked as deployed") {
            subject.onArtifactVersionDeployed(event)
            verify(exactly = 1) { repository.markAsSuccessfullyDeployedTo(any(), any(), event.artifactVersion, any()) }
          }

          test("an event is sent out") {
            subject.onArtifactVersionDeployed(event)
            verify { publisher.publishEvent(ofType<ArtifactDeployedNotification>()) }
          }
        }
      }

      context("artifact is not approved for env") {
        before {
          every { repository.isApprovedFor(any(), any(), event.artifactVersion, any()) } returns false
        }
        test("nothing is marked as deployed") {
          subject.onArtifactVersionDeployed(event)
          verify(exactly = 0) { repository.markAsSuccessfullyDeployedTo(config, any(), event.artifactVersion, any()) }
        }
        test("an event is not sent out") {
          subject.onArtifactVersionDeployed(event)
          verify { publisher wasNot Called }
        }
      }
    }
  }
}
