package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.core.parseMoniker
import com.netflix.spinnaker.keel.export.ExportErrorResult
import com.netflix.spinnaker.keel.export.ExportResult
import com.netflix.spinnaker.keel.export.ExportService
import com.netflix.spinnaker.keel.logging.TracingSupport.Companion.blankMDC
import com.netflix.spinnaker.keel.logging.TracingSupport.Companion.withTracingContext
import com.netflix.spinnaker.keel.retrofit.isNotFound
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import kotlinx.coroutines.runBlocking
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.util.comparator.NullSafeComparator
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/export"])
class ExportController(
  private val handlers: List<ResourceHandler<*, *>>,
  private val cloudDriverCache: CloudDriverCache,
  private val exportService: ExportService
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    val versionSuffix = """@v(\d+)$""".toRegex()
    private val versionPrefix = """^v""".toRegex()
    val versionComparator: Comparator<String> = NullSafeComparator<String>(
      Comparator<String> { s1, s2 ->
        DefaultArtifactVersion(s1?.replace(versionPrefix, "")).compareTo(
          DefaultArtifactVersion(s2?.replace(versionPrefix, ""))
        )
      },
      true // null is considered lower
    )

    /**
     * Assist for mapping between Deck and Clouddriver cloudProvider names
     * and Keel's plugin namespace.
     */
    private val CLOUD_PROVIDER_OVERRIDES = mapOf(
      "aws" to "ec2"
    )

    private val TYPE_TO_KIND = mapOf(
      "classicloadbalancer" to "classic-load-balancer",
      "classicloadbalancers" to "classic-load-balancer",
      "applicationloadbalancer" to "application-load-balancer",
      "applicationloadbalancers" to "application-load-balancer",
      "securitygroup" to "security-group",
      "securitygroups" to "security-group",
      "cluster" to "cluster",
      "clusters" to "cluster"
    )

    const val DEFAULT_PIPELINE_EXPORT_MAX_DAYS: Long = 180
  }

  /**
   * This route is location-less; given a resource name that can be monikered,
   * type, and account, all locations configured for the account are scanned for
   * matching resources that can be combined into a multi-region spec.
   *
   * Types are derived from Clouddriver's naming convention. It is assumed that
   * converting these to kebab case (i.e. securityGroups -> security-groups) will
   * match either the singular or plural of a [ResourceHandler]'s
   * [ResourceHandler.supportedKind].
   */
  @GetMapping(
    path = ["/{cloudProvider}/{account}/{type}/{name}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun get(
    @PathVariable("cloudProvider") cloudProvider: String,
    @PathVariable("account") account: String,
    @PathVariable("type") type: String,
    @PathVariable("name") name: String,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): SubmittedResource<*> {
    val kind = parseKind(cloudProvider, type)
    val handler = handlers.supporting(kind)
    val exportable = generateExportable(cloudProvider, type, account, user, name)

    return runBlocking(blankMDC) {
      withTracingContext(exportable) {
        log.info("Exporting resource ${exportable.toResourceId()}")
        SubmittedResource(
          kind = kind,
          spec = handler.export(exportable)
        )
      }
    }
  }

  @GetMapping(
    path = ["/artifact/{cloudProvider}/{account}/{clusterName}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun getArtifactFromCluster(
    @PathVariable("cloudProvider") cloudProvider: String,
    @PathVariable("account") account: String,
    @PathVariable("clusterName") name: String,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): DeliveryArtifact? {
    val kind = parseKind(cloudProvider, "cluster")
    val handler = handlers.supporting(kind)
    val exportable = generateExportable(cloudProvider, "cluster", account, user, name)

    return runBlocking(blankMDC) {
      withTracingContext(exportable) {
        log.info("Exporting artifact from cluster ${exportable.toResourceId()}")
        handler.exportArtifact(exportable)
      }
    }
  }

  @GetMapping(
    path = ["/{application}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #application)""")
  fun get(
    @PathVariable("application") application: String,
    @RequestParam("maxAgeDays") maxAgeDays: Long?
  ): ExportResult {
    return runBlocking {
      exportService.exportFromPipelines(application, maxAgeDays ?: DEFAULT_PIPELINE_EXPORT_MAX_DAYS)
    }
  }

  @ExceptionHandler(Exception::class)
  fun onException(e: Exception): ResponseEntity<ExportErrorResult> {
    log.error(e.message, e)
    return when (e.cause?.isNotFound ?: e.isNotFound) {
      true -> ResponseEntity.status(NOT_FOUND).body(ExportErrorResult(e.message ?: "not found"))
      else -> ResponseEntity.status(INTERNAL_SERVER_ERROR).body(ExportErrorResult(e.message ?: "unknown error"))
    }
  }

  fun parseKind(cloudProvider: String, type: String) =
    type.toLowerCase().let { t1 ->
      val group = CLOUD_PROVIDER_OVERRIDES[cloudProvider] ?: cloudProvider
      var version: String? = null
      val normalizedType = if (versionSuffix.containsMatchIn(t1)) {
        version = versionSuffix.find(t1)!!.groups[1]?.value
        t1.replace(versionSuffix, "")
      } else {
        t1
      }.let { t2 ->
        TYPE_TO_KIND.getOrDefault(t2, t2)
      }

      if (version == null) {
        version = handlers
          .supporting(group, normalizedType)
          .map { h -> h.supportedKind.kind.version }
          .sortedWith(versionComparator)
          .lastOrNull() ?: error("Unable to find version for group $group, $normalizedType")
      }

      "$group/$normalizedType@v$version"
    }.let(ResourceKind.Companion::parseKind)

  @Suppress("UNCHECKED_CAST")
  fun generateExportable(cloudProvider: String, type: String, account: String, user: String, name: String): Exportable {
    val kind = parseKind(cloudProvider, type)
    return Exportable(
      cloudProvider = kind.group,
      account = account,
      user = user,
      moniker = parseMoniker(name),
      regions = (
        cloudDriverCache
          .credentialBy(account)
          .attributes["regions"] as List<Map<String, Any>>
        )
        .map { it["name"] as String }
        .toSet(),
      kind = kind
    )
  }
}
