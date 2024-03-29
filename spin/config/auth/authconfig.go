// Copyright (c) 2018, Google, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package auth

import (
	"github.com/spinnaker/spin/config/auth/basic"
	gsa "github.com/spinnaker/spin/config/auth/googleserviceaccount"
	config "github.com/spinnaker/spin/config/auth/iap"
	"github.com/spinnaker/spin/config/auth/ldap"
	"github.com/spinnaker/spin/config/auth/oauth2"
	"github.com/spinnaker/spin/config/auth/x509"
)

// Config is the CLI's authentication configuration.
type Config struct {
	Enabled          bool           `json:"enabled" yaml:"enabled"`
	IgnoreRedirects  bool           `json:"ignoreRedirects" yaml:"ignoreRedirects"`
	IgnoreCertErrors bool           `json:"ignoreCertErrors" yaml:"ignoreCertErrors"`
	X509             *x509.Config   `json:"x509,omitempty" yaml:"x509,omitempty"`
	OAuth2           *oauth2.Config `json:"oauth2,omitempty" yaml:"oauth2,omitempty"`
	Basic            *basic.Config  `json:"basic,omitempty" yaml:"basic,omitempty"`
	Iap              *config.Config `json:"iap,omitempty" yaml:"iap,omitempty"`
	Ldap             *ldap.Config   `json:"ldap,omitempty" yaml:"ldap,omitempty"`

	GoogleServiceAccount *gsa.Config `json:"google_service_account,omitempty" yaml:"google_service_account,omitempty"`
}
