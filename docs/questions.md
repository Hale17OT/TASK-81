(1) Marketplace Ownership Model

Question: Is this a Peer-to-Peer (P2P) marketplace for students or a Centralized Distribution model managed by the school?

My Understanding: The requirement for "Intelligent Putaway" and admin-managed catalogs implies the school owns and manages the physical inventory.

Solution: Implement a B2C (Business-to-Consumer) model. "Marketplace" will refer strictly to the discovery and search UI/UX style, while inventory remains school-owned. Students cannot list items; they can only request available stock.

(2) Approval Workflow Granularity

Question: Does every student request require teacher intervention, or is the approval scope limited to specific items?

My Understanding: Requiring approval for every pencil or notebook would create a massive administrative bottleneck.

Solution: Add a RequiresApproval boolean flag to the InventoryItem entity. High-risk items (chemicals, electronics) trigger a teacher workflow, while general stationery defaults to auto-approval. Teachers are mapped to specific "Departments" to ensure they only review relevant equipment.

(3) Campus Logistics Logic

Question: How should the system calculate "Distance" for result sorting without using external maps?

My Understanding: The "internal zone distance table" implies a discrete coordinate or weighting system between campus zones (buildings).

Solution: Implement a Weighted Adjacency Matrix in the database. The search engine will calculate distance by joining the User.home_zone and Item.location_zone against this matrix to provide localized sorting.

(4) Data Retention & Privacy (Cryptographic Erasure)

Question: How do we "hard-delete" profiles after 30 days without breaking the 7-year financial audit trail?

My Understanding: Hard-deleting user records would orphan audit logs and break foreign key constraints in the relational DB.

Solution: Utilize Cryptographic Erasure. Delete the specific User record but retain the AuditLog entries. The logs will point to a Deleted_User_Placeholder, and all PII (name, email) within the logs will be overwritten with a one-way hash to satisfy both privacy laws and audit requirements.

(5) Offline Outbox Messaging

Question: How can the system "send emails" in a fully offline environment without an SMTP server?

My Understanding: "Emails" are actually internal notifications queued for later manual export.

Solution: Create an Outbox table. The Spring Boot NotificationService will write messages to this table instead of sending them. Admins can then use a "Flush Outbox" function to generate a ZIP of .eml files or a CSV manifest to process on an internet-connected machine.

(6) Personalization "Privacy Mode"

Question: Does disabling personalization stop data tracking or just the application of that data to search results?

My Understanding: The "Privacy Mode" toggle needs to be clear about its impact on algorithmic bias versus private history.

Solution: When Privacy Mode is ON, the SQL query builder will ignore user weights (browsing history) for search rankings. However, the system will still store the "Last 30 Items" locally for the user's private "Recently Viewed" UI component.

(7) Rate Control for NAT/Proxy IPs

Question: How do we apply a 30 requests/minute limit without penalizing a whole dorm full of students behind a single NAT IP?

My Understanding: On a school network, many users may share a single public-facing IP address.

Solution: Use Bucket4j for rate limiting. Anonymous users will be tracked via a composite key of IP Address + User-Agent. Once authenticated, users are tracked strictly by their User_ID, ensuring individual rate limits regardless of their network origin.

(8) Intelligent Putaway (ABC Classification)

Question: Should ABC classification be based on item value (cost) or pick frequency (velocity)?

My Understanding: "Intelligent Putaway" suggests optimizing for the physical labor of picking rather than just financial value.

Solution: Implement ABC classification based on Pick Frequency. "A" items (high velocity) are directed to "Zone 1" near the exit, while "C" items (slow-moving) go to the back of the warehouse, regardless of their unit cost.

(9) Crawler Observability & Snapshots

Question: What is the scope of the crawler observability, and how do we prevent snapshot bloat?

My Understanding: The system scrapes local intranet pages for inventory updates and needs to record failures for debugging.

Solution: The CrawlerTask will store a Blob of the raw HTML/File content only on failure. To protect database storage, a "Snapshot Cap" of 50 samples per job will be strictly enforced.

(10) Teacher Self-Approval Rules

Question: Can a teacher acting as a "requestor" approve their own equipment requests?

My Understanding: Self-approval undermines the integrity of the audit and oversight system.

Solution: Enforce a Self-Approval Block. If a teacher requests a restricted item, the workflow must route the request to a System Administrator or a peer teacher within the same Department.

(11) Pick Path Strategy Simulation

Question: How does the "Simulation Mode" compare different warehouse strategies?

My Understanding: This is a "What-If" tool used to optimize the physical layout based on historical demand.

Solution: Implement a ShadowPickingEngine. It re-runs the Path-Cost algorithm against the last 1,000 orders using a proposed new layout, outputting a JSON comparison showing the total "walking distance" saved.

(12) Volatile Memory Encryption Key Management

Question: How can we store AES-256 keys locally without them being compromised if the physical server is stolen?

My Understanding: Storing keys in application.properties or on-disk files is a security failure for on-premise hardware.

Solution: Use a Master Key Passphrase entry on application startup. The admin must manually enter the phrase into the Spring Boot console; it is held strictly in volatile memory (RAM) to decrypt the AES keys and is never written to disk.

(13) Dual-Address Schema

Question: How does the system handle the difference between a student's legal shipping address and their campus dorm location?

My Understanding: Fulfillment needs the campus location, while legal/billing records require a standard US-style address.

Solution: Implement a Dual-Address Schema. The profile includes a "Permanent Address" (standard US format) and a "Campus Location" dropdown (Specific Building/Room) for internal fulfillment routing.

(14) Backend Data Masking

Question: Should phone number masking occur in the browser UI or at the API level?

My Understanding: Frontend masking is insecure; the raw data should never leave the server unless authorized.

Solution: Implement Backend Masking at the DTO (Data Transfer Object) level. The REST API will return masked strings (e.g., ***-***-1234) by default. Unmasked data is only sent if the requesting user has the ADMIN_DATA_PRIVACY permission.

(15) 3D Path-Cost Matrix

Question: Does the path-cost calculation account for verticality (high shelving) in the warehouse?

My Understanding: Picking an item from a top shelf is physically more "expensive" than picking from a floor bin.

Solution: Map the stockroom using 3D coordinates (X, Y, Level). The "Path-Cost" between two bins is calculated as Linear Distance + (Level * 2), accounting for the extra time and effort required to use ladders or lifts for higher levels.