#!/bin/bash
set -e

XML_FILE='org.fox.ttrss/build/reports/lint-results-debug.xml'

echo '## 📋 Android Lint Results' >> "$GITHUB_STEP_SUMMARY"

if [ -f "$XML_FILE" ]; then
  ERRORS=$(grep -c 'severity="Error"' "$XML_FILE" 2>/dev/null || echo '0')
  WARNINGS=$(grep -c 'severity="Warning"' "$XML_FILE" 2>/dev/null || echo '0')
  INFO=$(grep -c 'severity="Information"' "$XML_FILE" 2>/dev/null || echo '0')

  echo '' >> "$GITHUB_STEP_SUMMARY"
  echo '| Severity | Count |' >> "$GITHUB_STEP_SUMMARY"
  echo '|----------|-------|' >> "$GITHUB_STEP_SUMMARY"
  echo "| ❌ Errors | $ERRORS |" >> "$GITHUB_STEP_SUMMARY"
  echo "| ⚠️ Warnings | $WARNINGS |" >> "$GITHUB_STEP_SUMMARY"
  echo "| ℹ️ Info | $INFO |" >> "$GITHUB_STEP_SUMMARY"

  if [ "$ERRORS" -gt 0 ] || [ "$WARNINGS" -gt 0 ]; then
    echo '' >> "$GITHUB_STEP_SUMMARY"
    echo '<details><summary>View Issues</summary>' >> "$GITHUB_STEP_SUMMARY"
    echo '' >> "$GITHUB_STEP_SUMMARY"
    echo '```' >> "$GITHUB_STEP_SUMMARY"
    grep -E 'message=|location' "$XML_FILE" | head -50 >> "$GITHUB_STEP_SUMMARY"
    echo '```' >> "$GITHUB_STEP_SUMMARY"
    echo '</details>' >> "$GITHUB_STEP_SUMMARY"
  fi
else
  echo '' >> "$GITHUB_STEP_SUMMARY"
  echo '> ⚠️ No lint report found' >> "$GITHUB_STEP_SUMMARY"
fi
