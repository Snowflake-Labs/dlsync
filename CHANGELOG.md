# DLSync Changelog

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
## [2.4.2] - 2025-09-03
### Updated
- Github action for release
- Updated readme for private key authentication
## [2.4.1] - 2025-08-22
### Fixed
- Fixed issue for encrypted private key files
## [2.4.0] - 2025-08-11
### Added
- Added support for 'PIPE' object type Deployment
- Added support for 'ALERT' object type Deployment
- Added feature toggle to configuration YAML to allow change to error disposition (fail deployment on first deployment tree error or continue after each error to try other items for deployment, logging and collecting all errors in the console.)
- Added support for private key authentication
### Fixed
- Fixed issue with dependency parsing in the form `@some_dependency`

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
## [2.3.0] - 2025-03-31
### Added
- Added support to specify target schemas for creating script from database
- Added unit test for parsing object types

## [2.2.0] - 2025-03-24
### Added
- Added support streamlit object type
- Added github action for release

## [2.1.1] - 2025-03-21
### Fixed
- Fixed null pointer exception when database and schema are missing from script object

## [2.1.0] - 2025-01-28
### Added
- Add support connection properties in config file

## [2.0.0] - 2025-01-21
### Added
- Added antlr for parsing SQL scripts.
- Added test module for unit testing

## [1.5.0] - 2025-01-08
- Initial open source release
