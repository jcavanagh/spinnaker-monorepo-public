package com.netflix.spinnaker.keel.api.artifacts

import java.time.Instant
import java.time.ZonedDateTime

/**
 * The build metadata of an artifact.
 */
data class BuildMetadata(
  val id: Int, // should be converted to number. Needs to be removed
  val number: String? = null, // build number, currently duplicate value to id
  val uid: String? = null, // a unique id for the build, which is generated by the CI service
  val job: Job? = null,
  val completedAt: String? = null,
  val startedAt: String? = null,
  val startedTs: String? = null,
  val completedTs: String? = null,
  val status: String? = null
) {
  val startedAtInstant: Instant?
    get() = startedAt?.let { ZonedDateTime.parse(it).toInstant() }

  val completedAtInstant: Instant?
    get() = completedAt?.let { ZonedDateTime.parse(it).toInstant() }

  fun isComplete(): Boolean =
    status in listOf("SUCCESS", "FAILURE", "ABORTED", "UNSTABLE")
}

data class Job(
  val link: String? = null,
  val name: String? = null
)