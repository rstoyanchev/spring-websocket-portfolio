
Demonstrates 3 approaches to testing a Spring STOMP over WebSocket application:

1. Server-side controller tests that load the actual Spring configuration (`context` sub-package)
2. Server-side controller tests that test one controller at a time without loading any Spring configuration (`standalone` sub-package)
3. End-to-end, full integration tests using an embedded Tomcat and a simple STOMP Java client (`tomcat` sub-package)

See the Javadoc of the respective tests for more details.