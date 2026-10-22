# hmpps-court-case-enrichment-worker

[![Ministry of Justice Repository Compliance Badge](https://github-community.service.justice.gov.uk/repository-standards/api/hmpps-court-case-enrichment-worker/badge?style=flat)](https://github-community.service.justice.gov.uk/repository-standards/hmpps-court-case-enrichment-worker)
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-court-case-enrichment-worker)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://court-case-enrichment-worker-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)

Template github repo used for new Kotlin based projects.

# Instructions

If this is a HMPPS project then the project will be created as part of bootstrapping -
see [hmpps-project-bootstrap](https://github.com/ministryofjustice/hmpps-project-bootstrap). You are able to specify a
template application using the `github_template_repo` attribute to clone without the need to manually do this yourself
within GitHub.

This project is community managed by the mojdt `#kotlin-dev` slack channel.
Please raise any questions or queries there. Contributions welcome!

Our security policy is located [here](https://github.com/ministryofjustice/hmpps-court-case-enrichment-worker/security/policy).

Documentation to create new service is located [here](https://tech-docs.hmpps.service.justice.gov.uk/creating-new-services/).

## Creating a Cloud Platform namespace

When deploying to a new namespace, you may wish to use the
[templates project namespace](https://github.com/ministryofjustice/cloud-platform-environments/tree/main/namespaces/live.cloud-platform.service.justice.gov.uk/hmpps-templates-dev)
as the basis for your new namespace. This namespace contains both the kotlin and typescript template projects,
which is the usual way that projects are setup.

Copy this folder and update all the existing namespace references to correspond to the environment to which you're deploying.

If you only need the kotlin configuration then remove all typescript references and remove the elasticache configuration.

To ensure the correct github teams can approve releases, you will need to make changes to the configuration in `resources/service-account-github` where the appropriate team names will need to be added (based on [lines 98-100](https://github.com/ministryofjustice/cloud-platform-environments/blob/main/namespaces/live.cloud-platform.service.justice.gov.uk/hmpps-templates-dev/resources/serviceaccount-github.tf#L98) and the reference appended to the teams list below [line 112](https://github.com/ministryofjustice/cloud-platform-environments/blob/main/namespaces/live.cloud-platform.service.justice.gov.uk/hmpps-templates-dev/resources/serviceaccount-github.tf#L112)). Note: hmpps-sre is in this list to assist with deployment issues.

Submit a PR to the Cloud Platform team in [#ask-cloud-platform](https://moj.enterprise.slack.com/archives/C57UPMZLY).
Further instructions from the Cloud Platform team can be found in the [Cloud Platform User Guide](https://user-guide.cloud-platform.service.justice.gov.uk/#cloud-platform-user-guide)

## Renaming from HMPPS Court Case Enrichment Worker - github Actions

Once the new repository is deployed. Navigate to the repository in github, and select the `Actions` tab.
Click the link to `Enable Actions on this repository`.

Find the Action workflow named: `rename-project-create-pr` and click `Run workflow`. This workflow will
execute the `rename-project.bash` and create Pull Request for you to review. Review the PR and merge.

Note: ideally this workflow would run automatically however due to a recent change github Actions are not
enabled by default on newly created repos. There is no way to enable Actions other then to click the button in the UI.
If this situation changes we will update this project so that the workflow is triggered during the bootstrap project.
Further reading: <https://github.community/t/workflow-isnt-enabled-in-repos-generated-from-template/136421>

The script takes six arguments:

### New project name

This should start with `hmpps-` e.g. `hmpps-prison-visits` so that it can be easily distinguished in github from
other departments projects. Try to avoid using abbreviations so that others can understand easily what your project is.

### Slack channel for release notifications

By default, release notifications are only enabled for production. The circleci configuration can be amended to send
release notifications for deployments to other environments if required. Note that if the configuration is amended,
the slack channel should then be amended to your own team's channel as `dps-releases` is strictly for production release
notifications. If the slack channel is set to something other than `dps-releases`, production release notifications
will still automatically go to `dps-releases` as well. This is configured by `releases-slack-channel` in
`.circleci/config.yml`.

### Slack channel for pipeline security notifications

Ths channel should be specific to your team and is for daily / weekly security scanning job results. It is your team's
responsibility to keep up-to-date with security issues and update your application so that these jobs pass. You will
only be notified if the jobs fail. The scan results can always be found in circleci for your project. This is
configured by `alerts-slack-channel` in `.circleci/config.yml`.

### Non production kubernetes alerts

By default Prometheus alerts are created in the application namespaces to monitor your application e.g. if your
application is crash looping, there are a significant number of errors from the ingress. Since Prometheus runs in
cloud platform AlertManager needs to be setup first with your channel. Please see
[Create your own custom alerts](https://user-guide.cloud-platform.service.justice.gov.uk/documentation/monitoring-an-app/how-to-create-alarms.html)
in the Cloud Platform user guide. Once that is setup then the `custom severity label` can be used for
`alertSeverity` in the `helm_deploy/values-*.yaml` configuration.

Normally it is worth setting up two separate labels and therefore two separate slack channels - one for your production
alerts and one for your non-production alerts. Using the same channel can mean that production alerts are sometimes
lost within non-production issues.

### Production kubernetes alerts

This is the severity label for production, determined by the `custom severity label`. See the above
[Non production kubernetes alerts section](non-production-kubernetes-alerts) for more information. This is configured in `helm_deploy/values-prod.yaml`.

### Product ID

This is so that we can link a component to a product and thus provide team and product information in the Developer
Portal. Refer to the developer portal at <https://developer-portal.hmpps.service.justice.gov.uk/products> to find your
product id. This is configured in `helm_deploy/<project_name>/values.yaml`.

## Manually branding from template app

Run the `rename-project.bash` without any arguments. This will prompt for the six required parameters and create a PR.
The script requires a recent version of `bash` to be installed, as well as GNU `sed` in the path.

## Common Kotlin patterns

Many patterns have evolved for HMPPS Kotlin applications. Using these patterns provides consistency across our suite of
Kotlin microservices and allows you to concentrate on building your business needs rather than reinventing the
technical approach.

Documentation for these patterns can be found in the [HMPPS tech docs](https://tech-docs.hmpps.service.justice.gov.uk/common-kotlin-patterns/).
If this documentation is incorrect or needs improving please report to [#ask-prisons-digital-sre](https://moj.enterprise.slack.com/archives/C06MWP0UKDE)
or [raise a PR](https://github.com/ministryofjustice/hmpps-tech-docs).

## Validating a deployment

### Defendant enrichment job endpoint

`POST /jobs/enrich-defendants` is reserved for invoking defendant enrichment from
a Kubernetes CronJob. This POST endpoint permits anonymous access and requires
no bearer token or request body. Other protected endpoints, including `/ping`,
still require authentication. Outbound CCS calls still use service OAuth credentials.

Calling from the same namespace does not enforce namespace-only access.
Any caller able to reach this endpoint can trigger the job, including via the
existing ingress allowlist. Restrict access using ingress rules and Kubernetes
NetworkPolicies if the endpoint must be limited to internal callers.

The job accepts no court-code parameter and currently fetches targets only for
court `B03DE`, hard-coded for the POC in `DefendantEnrichmentJobService`.
Common Platform and Libra enrichment are enabled.

The workflow currently fetches all pages of Common Platform defendant IDs
and Libra cIDs from CCS for 14 days starting today: `fromDate` is today
(inclusive) and `toDate` is today plus 14 days (exclusive).
`EnrichmentTargetService` owns these CCS target calls and pagination validation.
Successful invocations return HTTP 200 with status
`ENRICHMENT_COMPLETED`, fetched and processed counts for both sources, court
code `B03DE`, and a `results` array containing Common Platform and Libra enrichment
results. `persistedResults` counts results acknowledged by CCS; failed counts are zero
on successful runs. Each target is enriched and then posted to CCS before the next
target is processed, including `NO_MATCH` and `NEEDS_REVIEW` outcomes.
CCS target-fetch, enrichment, and persistence failures fail the job with a non-2xx response.

The enrichment implementation processes Common Platform and then Libra targets synchronously
using `DefendantEnrichmentService`,
keeping Common Platform and Libra CPR lookups separate. CPR CRNs are looked up in
Offender Search once per distinct CRN per target. No name-based `/match` fallback
is performed; this follows CCM's existing `CprService`.

Enrichment builds clean results.
A CPR 404 produces `NO_MATCH`. A CPR record without
CRNs still enriches defendant details. Multiple CRNs or zero/multiple Offender
Search responses produce `NEEDS_REVIEW` with candidate matches, but no selected
offender or probation details. Only one CPR CRN with exactly one search response
supplies those details. Grouped matches use `NAME_DOB_ALIAS`, with confirmed and
rejected both false, and use the first search response for candidate PNC/CRO,
matching the existing CCM behavior.

Downstream errors, empty successful response bodies, invalid dates, blank CRNs,
and inconsistent exact-match identifiers fail the job instead of becoming
`NO_MATCH`. Processing stops on the first error and the endpoint returns a
non-2xx response. Earlier acknowledged writes are not rolled back if a later target
fails. Results are also returned to the caller on successful runs.
Do not enable a scheduled CronJob until CCS persistence is implemented and the
CCS update contract is agreed. An eventual HTTP caller should treat non-2xx responses as job failures
(for example, use `curl --fail-with-body`).

`GET /ping` returns HTTP 200 with the following JSON for a valid HMPPS Auth bearer token.
No specific role is required. Requests without a valid token return HTTP 401.

```json
{"message":"HMPPS Court Case Enrichment Worker is running"}
```

Set `TOKEN` to an access token from HMPPS Auth for the environment being checked:

```bash
curl --fail-with-body -H "Authorization: Bearer $TOKEN" \
  https://court-case-enrichment-worker-dev.hmpps.service.justice.gov.uk/ping

curl --fail-with-body -H "Authorization: Bearer $TOKEN" \
  https://court-case-enrichment-worker-preprod.hmpps.service.justice.gov.uk/ping
```

The existing ingress IP allowlists still apply. This endpoint confirms that the application
is responding; use `/health` to check its dependencies.

## Running the application locally

### Offender Search client

`OffenderSearchRestClient.match(MatchRequest)` posts JSON to `/match`.
`search(crn)` posts a typed JSON request to `/search`, deserializes the response
array, and returns a `SearchResponses` wrapper. API models do not include the
other service's domain conversions or application-side match probability.
Callers supply the match request; the other service's domain-dependent factory
has not been copied.

Both operations use the `offender-search-client` OAuth client-credentials
registration, a 10-second timeout per attempt, and up to three retries with
exponential backoff for transport errors, timeouts, and HTTP 5xx responses.
HTTP 4xx (including 429), malformed responses, and empty response bodies are
propagated without retrying. A JSON `[]` is a valid empty search result.
Retry logs do not include personal data or request bodies.

CPR and Offender Search calls log a DEBUG start and completion with the operation,
a per-call correlation ID, and elapsed milliseconds (including retries). CPR
completion distinguishes `RECORD_RECEIVED` from `NOT_FOUND`; Offender Search
completion includes the result count, including zero results. Completion is logged
only after the response is successfully decoded. Final failures log a WARN with
the exception type and downstream HTTP status when available; token-acquisition
and transport failures may have no downstream status. These logs exclude
identifiers, payloads, credentials, tokens, and exception messages.

Each job emits one INFO connector summary for CPR and one for Offender Search,
including when the run fails partway through. Summaries include the run ID, logical
call count, completed calls, empty results, final failures, and cumulative call time
(including retries). CPR `emptyResults` counts not-found responses; Offender Search
counts successful responses containing zero results. A connector with zero calls
was not exercised. Retries are not counted as additional logical calls.
Enable `logging.level.ServiceClientLogging=DEBUG` temporarily for per-call detail.

For the POC, Helm `namespace_secrets` maps `OFFENDER_SEARCH_CLIENT_ID` and
`OFFENDER_SEARCH_CLIENT_SECRET` to `nomis-oauth-client-id` and
`nomis-oauth-client-secret` in `court-case-service-secrets`. That secret must
exist in the worker's namespace in each deployed environment.
The registration name `offender-search-client` is an internal alias; it does
not need to match the registration name used by Court Case Service.
The worker requests service-only client-credentials tokens from
`${hmpps-auth.url}/oauth/token`, without a username. The OAuth client must
support requests without a username and have access to Offender Search.
Do not use a username-required client or fabricate a user identity for the batch.
Preprod and prod require these variables at startup.
Local dev credentials are placeholders and must be overridden for live calls.
`OFFENDER_SEARCH_BASE_URL` is configured for each environment in Helm.
For local mocking, override it and set `OFFENDER_SEARCH_DISABLE_AUTHENTICATION=true`.

### Court Case Service client

`CourtCaseServiceClient.getDefendantIds` and `getCids` fetch a single page from
`/enrichment-targets/defendant-ids` and `/enrichment-targets/cids` respectively.
Both accept an inclusive `fromDate`, exclusive `toDate`, optional `courtCode`,
zero-based `page` (default 0), and `size` (default 200, valid range 1-500).
The response includes pagination metadata; callers use `hasNext` to request subsequent pages.

Requests use a service token obtained from HMPPS Auth with the
`court-case-service-client` OAuth registration, not the caller's bearer token.
Before deploying, provision this worker's own credentials and map them to
`COURT_CASE_SERVICE_CLIENT_ID` and `COURT_CASE_SERVICE_CLIENT_SECRET` through
Helm `namespace_secrets`. Do not reuse Court Case Matcher's credentials.
The worker's secret resource/key names have not yet been supplied, so that mapping
is not included. Preprod and prod require these variables at startup.
The local `dev` profile has placeholder credentials that must be overridden
to call the deployed service.

`COURT_CASE_SERVICE_BASE_URL` is configured per environment in Helm.
`court-case-service.timeout` defaults to 10 seconds per attempt.
Transport failures, timeouts, and HTTP 5xx responses receive up to three retries;
other HTTP errors and malformed/empty responses are propagated.

#### Draft enrichment persistence contract

`CourtCaseServiceClient.persistEnrichmentResult(PersistEnrichmentResultRequest)`
posts one clean result per target using the same CCS service OAuth registration.
The proposed path is **`POST /enrichment-results`**, configurable with
`COURT_CASE_SERVICE_POST_ENRICHMENT_URL`. This endpoint and request schema are
drafts: CCS has not implemented or agreed them yet. The batch job now calls
this method immediately after enriching each target. Until CCS implements the
endpoint, a non-empty run will fail at its first rejected persistence request.

The request distinguishes `COMMON_PLATFORM` defendant IDs from `LIBRA` cIDs
using `sourceType` and `targetId`. It carries an enrichment status, normalized
defendant details (CPR UUID, name, date of birth, sex, address), offender identifiers,
probation details (including termination date and pre-sentence activity), and
grouped CRN matches with normalized aliases and confirmation/rejection flags.
It also includes warnings, errors, `enrichedBy`, and an optional `jobRunId`.
No raw CPR or Offender Search response objects are part of the contract.

Any 2xx response acknowledges the write; the response body is discarded.
Non-2xx responses, including redirects, and transport/timeouts are propagated.
Writes have a 10-second timeout and **no automatic retries** because CCS write
idempotency is not defined. A timeout may mean the write succeeded at CCS; do not
blindly replay it.

Before production use, agree the endpoint, accepted statuses, null/omitted
field semantics (preserve versus clear), missing-CPR behavior, protection of
existing confirmed matches, and idempotency with CCS. In particular, the current
`CprService` preserves existing date of birth and address when CPR supplies no
replacement; a nullable DTO alone does not define that update behavior.

The application comes with a `dev` spring profile that includes default settings for running locally. This is not
necessary when deploying to kubernetes as these values are included in the helm configuration templates -
e.g. `values-dev.yaml`.

There is also a `docker-compose.yml` that can be used to run a local instance of the template in docker and also an
instance of HMPPS Auth (required if your service calls out to other services using a token).

```bash
docker compose pull && docker compose up
```

will run the application and HMPPS Auth within a local docker instance.

### Running the application in Intellij

```bash
docker compose pull && docker compose up --scale hmpps-court-case-enrichment-worker=0
```

will just start a docker instance of HMPPS Auth. The application should then be started with a `dev` active profile
in Intellij.

### Building and running the docker image locally

The `Dockerfile` relies on the application being built first. Steps to build the docker image:
1. Build the jar files
```
./gradlew clean assemble
```
2. Copy the jar files to the base directory so that the docker build can find them
```
cp build/libs/*.jar .
```
3. Build the docker image with required arguments
```
docker build --build-arg GIT_REF=21345 --build-arg GIT_BRANCH=bob --build-arg BUILD_NUMBER=$(date '+%Y-%m-%d') .
```
4. Run the docker image, setting the auth url so that it starts up
```
docker run -e HMPPS_AUTH_URL="https://sign-in-dev.hmpps.service.justice.gov.uk/auth" <sha from step 3>
```
