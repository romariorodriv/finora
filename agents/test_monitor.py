import unittest

from agents.monitor import check_once, extract_evidence, load_sources
from pathlib import Path


class MonitorTests(unittest.TestCase):
    def setUp(self):
        self.sources = [{"id": "test", "url": "https://www.claro.com.pe/personas/movil/postpago/", "label": "Test"}]

    def test_extraction_ignores_scripts_and_captures_visible_price(self):
        result = extract_evidence("<html><script>S/ 999</script><p>Precio S/ 49.90 al mes</p></html>")
        self.assertEqual(result["price_mentions_unverified"], ["S/ 49.90"])

    def test_first_run_is_baseline_not_savings(self):
        _, events = check_once(self.sources, {}, fetcher=lambda _: "<p>S/ 49.90</p>")
        self.assertEqual(events[0]["status"], "baseline")
        self.assertNotIn("saving", events[0])

    def test_changed_page_marks_event(self):
        initial, _ = check_once(self.sources, {}, fetcher=lambda _: "<p>S/ 49.90</p>")
        _, events = check_once(self.sources, initial, fetcher=lambda _: "<p>S/ 59.90</p>")
        self.assertEqual(events[0]["status"], "changed")

    def test_fetch_failure_preserves_last_good_evidence(self):
        initial, _ = check_once(self.sources, {}, fetcher=lambda _: "<p>S/ 49.90</p>")
        def fail(_): raise PermissionError("robots.txt")
        latest, events = check_once(self.sources, initial, fetcher=fail)
        self.assertEqual(events[0]["status"], "error")
        self.assertEqual(latest, initial)

    def test_allowlisted_sources(self):
        sources = load_sources(Path(__file__).with_name("sources.json"))
        self.assertEqual(len(sources), 2)


if __name__ == "__main__":
    unittest.main()
