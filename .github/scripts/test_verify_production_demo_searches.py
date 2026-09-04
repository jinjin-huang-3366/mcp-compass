import unittest

from verify_production_demo_searches import EXPECTATIONS, validate


def response(demo_id, *, matches=None, strong_match=None, forbidden=None, reasons=None):
    expectation = EXPECTATIONS[demo_id]
    if matches is None and expectation.acceptable_server_ids:
        matches = [{"registryName": next(iter(expectation.acceptable_server_ids))}]
    return {
        "strongMatch": expectation.strong_match if strong_match is None else strong_match,
        "parsedIntent": {
            "forbiddenCapabilities": list(
                expectation.required_forbidden_capabilities if forbidden is None else forbidden
            )
        },
        "matches": [] if matches is None else matches,
        "abstentionReasons": (
            ["All retrieved candidates were excluded by hard constraints."]
            if reasons is None and not expectation.strong_match
            else (reasons or [])
        ),
    }


class ProductionDemoSearchValidatorTest(unittest.TestCase):
    def test_accepts_all_four_labelled_outcomes(self):
        for demo_id in EXPECTATIONS:
            with self.subTest(demo_id=demo_id):
                summary = validate(demo_id, response(demo_id))
                self.assertEqual(demo_id, summary["demo"])

    def test_rejects_missing_deterministic_forbidden_intent(self):
        with self.assertRaisesRegex(ValueError, "missing forbidden capabilities"):
            validate("github-no-delete", response("github-no-delete", forbidden=[]))

    def test_rejects_forbidden_result_in_top_three(self):
        payload = response(
            "postgres-read-only",
            matches=[{"registryName": "io.github.itunified-io/postgres"}],
        )
        with self.assertRaisesRegex(ValueError, "forbidden results appeared"):
            validate("postgres-read-only", payload)

    def test_rejects_strong_match_without_labelled_acceptable_result(self):
        payload = response(
            "web-docs-readonly", matches=[{"registryName": "example/unrelated"}]
        )
        with self.assertRaisesRegex(ValueError, "no labelled acceptable server"):
            validate("web-docs-readonly", payload)

    def test_rejects_abstention_that_returns_matches(self):
        payload = response(
            "twilio-sms-no-voice",
            matches=[{"registryName": "example/unrelated"}],
        )
        with self.assertRaisesRegex(ValueError, "abstention response still returned"):
            validate("twilio-sms-no-voice", payload)


if __name__ == "__main__":
    unittest.main()
