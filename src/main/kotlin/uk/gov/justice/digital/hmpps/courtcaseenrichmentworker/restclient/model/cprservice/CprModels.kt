package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CprDefendant(
  val cprUUID: String? = null,
  val firstName: String? = null,
  val middleNames: String? = null,
  val lastName: String? = null,
  val dateOfBirth: String? = null,
  val title: CprTitle? = null,
  val masterDefendantId: String? = null,
  val sex: CprSex? = null,
  val religion: CprReligion? = null,
  val ethnicity: CprEthnicity? = null,
  val aliases: List<CprAlias>? = null,
  val nationalities: List<CprNationality>? = null,
  val addresses: List<CprAddress>? = null,
  val identifiers: CprIdentifier? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CprTitle(
  val code: String? = null,
  val description: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CprSex(
  val code: String? = null,
  val description: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CprReligion(
  val code: String? = null,
  val description: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CprEthnicity(
  val code: String? = null,
  val description: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CprAlias(
  val firstName: String? = null,
  val lastName: String? = null,
  val middleNames: String? = null,
  val title: CprTitle? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CprNationality(
  val code: String? = null,
  val description: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CprAddress(
  val noFixedAbode: String? = null,
  val startDate: String? = null,
  val endDate: String? = null,
  val postcode: String? = null,
  val subBuildingName: String? = null,
  val buildingName: String? = null,
  val buildingNumber: String? = null,
  val thoroughfareName: String? = null,
  val dependentLocality: String? = null,
  val postTown: String? = null,
  val county: String? = null,
  val country: String? = null,
  val uprn: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CprIdentifier(
  val crns: List<String>? = null,
  val prisonNumbers: List<String>? = null,
  val defendantIds: List<String>? = null,
  val cids: List<String>? = null,
  val pncs: List<String>? = null,
  val cros: List<String>? = null,
  val nationalInsuranceNumbers: List<String>? = null,
  val driverLicenseNumbers: List<String>? = null,
  val arrestSummonsNumbers: List<String>? = null,
)
