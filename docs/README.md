# WorkSphere Documentation

This index defines the reading order and authority of WorkSphere documentation.

1. [Product overview and delivery roadmap](../README.md)
2. [System design](architecture/system-design.md)
3. [Database design and ERD](er-diagram/erd.md)
4. [Architecture decisions](adr/README.md)
5. [Contribution and branching guidance](../CONTRIBUTING.md)

## Development guides

- [Frontend setup and commands](development/frontend.md)
- [Backend setup and commands](development/backend.md)
- [Local infrastructure and CI](development/infrastructure-and-ci.md)
- [Build, CI/CD, and free deployment](development/build-and-free-deployment.md)

## Document authority

- ADRs record individual decisions and their approval status.
- The database design is authoritative for physical tables, keys and tenant-aware relationships.
- The system design is authoritative for component boundaries, flows and operational architecture.
- The root README summarizes product scope and milestones; it must not override a more specific design document.

All ADRs currently remain `Proposed`. The documents form a review-ready baseline, not a claim that the system has been implemented, load-tested or approved for production.

