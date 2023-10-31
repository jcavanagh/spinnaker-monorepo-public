/*
 * Spinnaker API
 *
 * No description provided (generated by Swagger Codegen https://github.com/swagger-api/swagger-codegen)
 *
 * API version: 1.0.0
 * Generated by: Swagger Codegen (https://github.com/swagger-api/swagger-codegen.git)
 */

package swagger

import (
	"time"
)

type SpinnakerPluginRelease struct {
	Date time.Time `json:"date,omitempty"`
	Preferred bool `json:"preferred,omitempty"`
	RemoteExtensions []RemoteExtensionConfig `json:"remoteExtensions,omitempty"`
	Requires string `json:"requires,omitempty"`
	Sha512sum string `json:"sha512sum,omitempty"`
	Url string `json:"url,omitempty"`
	Version string `json:"version,omitempty"`
}
