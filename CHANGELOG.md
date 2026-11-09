# Changelog

All notable changes to the Favicon Manager module are documented in this file.

## [Unreleased]

### Added
- First unit-test suite (JUnit 4 + Mockito) for `FaviconFilter`, covering favicon-request routing, the `jmix:favicon` lookup, the no-favicon case, and the robustness guarantee that the filter chain always proceeds even when site resolution throws.
- JaCoCo coverage wiring for SonarQube reporting.

### Changed
- Replaced the `javax.servlet.*` wildcard import in `FaviconFilter` with explicit imports.
