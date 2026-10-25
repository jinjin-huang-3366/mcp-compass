"""Offline feasibility checks for synthetic EVI-07 evidence; not product verification."""

import json
import unittest
from pathlib import Path


CASES = json.loads(Path(__file__).with_name("cases.json").read_text(encoding="utf-8"))["cases"]


def classify(case):
    registry = case["registry"]
    package = case["packageObservation"]
    attestation = case["attestationObservation"]
    declared = registry["package"]

    namespace = "REGISTRY_AUTHENTICATED" if registry["officialResponse"] else "UNCONFIRMED"

    if package is None:
        package_link = "UNOBSERVED"
    elif package.get("mcpName") != registry["name"]:
        package_link = "NAME_MISMATCH"
    elif registry["repository"] and package.get("repository") != registry["repository"]:
        package_link = "SOURCE_CLAIM_MISMATCH"
    elif registry["repository"] and package.get("repository"):
        package_link = "NAME_MATCH_SOURCE_DECLARED"
    else:
        package_link = "NAME_MATCH_SOURCE_UNKNOWN"

    declared_digest = declared.get("fileSha256")
    downloaded_digest = package.get("downloadSha256") if package else None
    if not declared_digest or not downloaded_digest:
        digest = "UNOBSERVED"
    else:
        digest = "MATCH" if declared_digest == downloaded_digest else "MISMATCH"

    if attestation is None:
        attestation_result = "UNOBSERVED"
    elif not attestation["verified"]:
        attestation_result = "INVALID"
    elif not downloaded_digest or attestation["subjectSha256"] != downloaded_digest:
        attestation_result = "SUBJECT_MISMATCH"
    elif not registry["repository"] or attestation["sourceRepository"] != registry["repository"]:
        attestation_result = "SOURCE_MISMATCH"
    else:
        attestation_result = "VERIFIED_SOURCE_MATCH"

    return dict(namespace=namespace, packageLink=package_link, digest=digest,
                attestation=attestation_result)


class ProvenanceFixtureTest(unittest.TestCase):
    def test_every_case_has_distinct_expected_outcomes(self):
        self.assertEqual(8, len(CASES))
        self.assertEqual(len(CASES), len({case["id"] for case in CASES}))
        for case in CASES:
            with self.subTest(case=case["id"]):
                self.assertEqual(case["expected"], classify(case))

    def test_good_registry_status_cannot_mask_other_failures(self):
        for case in CASES:
            if case["id"] in {"package-name-mismatch", "source-claim-mismatch",
                              "tampered-download", "attestation-subject-mismatch",
                              "attestation-source-mismatch", "unverified-attestation"}:
                with self.subTest(case=case["id"]):
                    result = classify(case)
                    self.assertEqual("REGISTRY_AUTHENTICATED", result["namespace"])
                    self.assertTrue(any(result[key] in {"MISMATCH", "NAME_MISMATCH",
                                                         "SOURCE_CLAIM_MISMATCH", "SUBJECT_MISMATCH",
                                                         "SOURCE_MISMATCH", "INVALID"}
                                        for key in ("packageLink", "digest", "attestation")))


if __name__ == "__main__":
    unittest.main()
