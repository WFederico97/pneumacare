<h2 local-id="1a2b3c4d5e6f">Description</h2>
<p local-id="2b3c4d5e6f7a">This resource manages the registration and retrieval of patient identity records within the ICU system, including PII data such as full name, national ID, and date of birth.</p><ac:structured-macro ac:name="note" ac:schema-version="1" ac:local-id="3c4d5e6f7a8b" ac:macro-id="748f2baf-88a3-41c2-b6dc-000000000001"><ac:rich-text-body>
<p local-id="4d5e6f7a8b9c">PII fields (<code>firstName</code>, <code>lastName</code>, <code>nationalId</code>) are encrypted at rest using <strong>AES-256-GCM</strong> with a random 12-byte IV per write. Encryption and decryption are handled transparently by the JPA layer — callers always send and receive plain text. In <code>staging</code>/<code>prod</code> profiles, <code>POST</code> requires OAuth2 scope <code>SCOPE_write</code> and <code>GET</code> requires <code>SCOPE_read</code>. In the <code>dev</code> profile all endpoints are open (<code>permitAll</code>).</p></ac:rich-text-body></ac:structured-macro>

<h2 local-id="5e6f7a8b9c0d">Endpoint: Register Patient</h2>
<ul local-id="6f7a8b9c0d1e">
<li local-id="7a8b9c0d1e2f">
<p local-id="8b9c0d1e2f3a"><strong>URL: </strong><code>/api/v1/patients</code></p></li>
<li local-id="9c0d1e2f3a4b">
<p local-id="0d1e2f3a4b5c"><strong>METHOD: </strong><code>POST</code></p></li>
<li local-id="1e2f3a4b5c6d">
<p local-id="2f3a4b5c6d7e"><strong>REQUIRED PERMISSIONS: </strong><code>SCOPE_write</code> (staging/prod) — open in dev</p></li></ul>

<h2 local-id="3a4b5c6d7e8f">Request Body</h2>
<p local-id="4b5c6d7e8f9a">JSON object with the patient's identity data. All fields are plain text; the persistence layer encrypts PII transparently.</p>
<table data-table-width="1800" data-layout="align-start" ac:local-id="5c6d7e8f9a0b">
<tbody>
<tr ac:local-id="6d7e8f9a0b1c">
<th ac:local-id="7e8f9a0b1c2d">
<p local-id="8f9a0b1c2d3e"><strong>Field</strong></p></th>
<th ac:local-id="9a0b1c2d3e4f">
<p local-id="0b1c2d3e4f5a"><strong>Type</strong></p></th>
<th ac:local-id="1c2d3e4f5a6b">
<p local-id="2d3e4f5a6b7c"><strong>Required</strong></p></th>
<th ac:local-id="3e4f5a6b7c8d">
<p local-id="4f5a6b7c8d9e"><strong>Description</strong></p></th></tr>
<tr ac:local-id="5a6b7c8d9e0f">
<td ac:local-id="6b7c8d9e0f1a">
<p local-id="7c8d9e0f1a2b"><code>firstName</code></p></td>
<td ac:local-id="8d9e0f1a2b3c">
<p local-id="9e0f1a2b3c4d"><code>String</code></p></td>
<td ac:local-id="0f1a2b3c4d5e">
<p local-id="1a2b3c4d5e6f"><code>Yes</code></p></td>
<td ac:local-id="2b3c4d5e6f7a">
<p local-id="3c4d5e6f7a8b">Patient first name. Max 100 characters. <strong>Stored encrypted (AES-256-GCM).</strong></p></td></tr>
<tr ac:local-id="4d5e6f7a8b9c">
<td ac:local-id="5e6f7a8b9c0d">
<p local-id="6f7a8b9c0d1e"><code>lastName</code></p></td>
<td ac:local-id="7a8b9c0d1e2f">
<p local-id="8b9c0d1e2f3a"><code>String</code></p></td>
<td ac:local-id="9c0d1e2f3a4b">
<p local-id="0d1e2f3a4b5c"><code>Yes</code></p></td>
<td ac:local-id="1e2f3a4b5c6d">
<p local-id="2f3a4b5c6d7e">Patient last name. Max 100 characters. <strong>Stored encrypted (AES-256-GCM).</strong></p></td></tr>
<tr ac:local-id="3a4b5c6d7e8f">
<td ac:local-id="4b5c6d7e8f9a">
<p local-id="5c6d7e8f9a0b"><code>nationalId</code></p></td>
<td ac:local-id="6d7e8f9a0b1c">
<p local-id="7e8f9a0b1c2d"><code>String</code></p></td>
<td ac:local-id="8f9a0b1c2d3e">
<p local-id="9a0b1c2d3e4f"><code>Yes</code></p></td>
<td ac:local-id="0b1c2d3e4f5a">
<p local-id="1c2d3e4f5a6b">National identity document number. Max 20 characters. <strong>Stored encrypted (AES-256-GCM).</strong> DB-level UNIQUE constraint is intentionally absent due to non-deterministic encryption.</p></td></tr>
<tr ac:local-id="2d3e4f5a6b7c">
<td ac:local-id="3e4f5a6b7c8d">
<p local-id="4f5a6b7c8d9e"><code>birthDate</code></p></td>
<td ac:local-id="5a6b7c8d9e0f">
<p local-id="6b7c8d9e0f1a"><code>String (ISO-8601 date)</code></p></td>
<td ac:local-id="7c8d9e0f1a2b">
<p local-id="8d9e0f1a2b3c"><code>Yes</code></p></td>
<td ac:local-id="9e0f1a2b3c4d">
<p local-id="0f1a2b3c4d5e">Patient date of birth in <code>YYYY-MM-DD</code> format. Must be a past date.</p></td></tr></tbody></table>

<h2 local-id="1a2b3c4d5e6a">Payload Example (JSON):</h2><ac:structured-macro ac:name="code" ac:schema-version="1" ac:local-id="2b3c4d5e6f7b" ac:macro-id="65a5f4bb-689d-4d38-919d-000000000001"><ac:parameter ac:name="language">json</ac:parameter><ac:parameter ac:name="breakoutMode">wide</ac:parameter><ac:parameter ac:name="breakoutWidth">1800</ac:parameter><ac:plain-text-body><![CDATA[{
  "firstName": "Juan",
  "lastName": "Pérez",
  "nationalId": "35123456",
  "birthDate": "1989-05-14"
}]]></ac:plain-text-body></ac:structured-macro>

<h2 local-id="3c4d5e6f7a8a">Response JSON (201 Created):</h2><ac:structured-macro ac:name="code" ac:schema-version="1" ac:local-id="4d5e6f7a8b9a" ac:macro-id="018d1fb8-627b-4a24-ac58-000000000001"><ac:parameter ac:name="language">json</ac:parameter><ac:parameter ac:name="breakoutMode">wide</ac:parameter><ac:parameter ac:name="breakoutWidth">1800</ac:parameter><ac:plain-text-body><![CDATA[{
  "status": 201,
  "message": "Patient registered successfully",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "firstName": "Juan",
    "lastName": "Pérez",
    "nationalId": "35123456",
    "birthDate": "1989-05-14"
  }
}]]></ac:plain-text-body></ac:structured-macro>

<h2 local-id="5e6f7a8b9c0a">Error Responses:</h2>
<table data-table-width="1800" data-layout="align-start" ac:local-id="6f7a8b9c0d1a">
<tbody>
<tr ac:local-id="7a8b9c0d1e2a">
<th ac:local-id="8b9c0d1e2f3b"><p local-id="9c0d1e2f3a4a"><strong>HTTP Status</strong></p></th>
<th ac:local-id="0d1e2f3a4b5a"><p local-id="1e2f3a4b5c6a"><strong>Condition</strong></p></th></tr>
<tr ac:local-id="2f3a4b5c6d7a">
<td ac:local-id="3a4b5c6d7e8a"><p local-id="4b5c6d7e8f9b"><code>400 Bad Request</code></p></td>
<td ac:local-id="5c6d7e8f9a0a"><p local-id="6d7e8f9a0b1a">One or more required fields are blank, null, exceed max length, or <code>birthDate</code> is not a past date.</p></td></tr></tbody></table>

<p local-id="7e8f9a0b1c2a" />

<h2 local-id="8f9a0b1c2d3a">Endpoint: Get Patient by ID</h2>
<ul local-id="9a0b1c2d3e4a">
<li local-id="0b1c2d3e4f5b">
<p local-id="1c2d3e4f5a6a"><strong>URL: </strong><code>/api/v1/patients/{id}</code></p></li>
<li local-id="2d3e4f5a6b7a">
<p local-id="3e4f5a6b7c8a"><strong>METHOD: </strong><code>GET</code></p></li>
<li local-id="4f5a6b7c8d9a">
<p local-id="5a6b7c8d9e0a"><strong>REQUIRED PERMISSIONS: </strong><code>SCOPE_read</code> (staging/prod) — open in dev</p></li></ul>

<h2 local-id="6b7c8d9e0f1b">Path Parameters</h2>
<p local-id="7c8d9e0f1a2a">No request body. The patient identity UUID is provided as a path parameter.</p>
<table data-table-width="1800" data-layout="align-start" ac:local-id="8d9e0f1a2b3a">
<tbody>
<tr ac:local-id="9e0f1a2b3c4a">
<th ac:local-id="0f1a2b3c4d5a"><p local-id="1a2b3c4d5e6b"><strong>Field</strong></p></th>
<th ac:local-id="2b3c4d5e6f7c"><p local-id="3c4d5e6f7a8c"><strong>Type</strong></p></th>
<th ac:local-id="4d5e6f7a8b9b"><p local-id="5e6f7a8b9c0b"><strong>Required</strong></p></th>
<th ac:local-id="6f7a8b9c0d1b"><p local-id="7a8b9c0d1e2c"><strong>Description</strong></p></th></tr>
<tr ac:local-id="8b9c0d1e2f3c">
<td ac:local-id="9c0d1e2f3a4c"><p local-id="0d1e2f3a4b5b"><code>id</code></p></td>
<td ac:local-id="1e2f3a4b5c6b"><p local-id="2f3a4b5c6d7b"><code>UUID</code></p></td>
<td ac:local-id="3a4b5c6d7e8b"><p local-id="4b5c6d7e8f9c"><code>Yes</code></p></td>
<td ac:local-id="5c6d7e8f9a0c"><p local-id="6d7e8f9a0b1c">The unique identifier of the patient identity record, as returned by <code>POST /api/v1/patients</code>.</p></td></tr></tbody></table>

<h2 local-id="7e8f9a0b1c2b">Payload Example (JSON):</h2>
<p local-id="8f9a0b1c2d3b">No request body.</p>

<h2 local-id="9a0b1c2d3e4b">Response JSON (200 OK):</h2><ac:structured-macro ac:name="code" ac:schema-version="1" ac:local-id="0b1c2d3e4f5c" ac:macro-id="018d1fb8-627b-4a24-ac58-000000000002"><ac:parameter ac:name="language">json</ac:parameter><ac:parameter ac:name="breakoutMode">wide</ac:parameter><ac:parameter ac:name="breakoutWidth">1800</ac:parameter><ac:plain-text-body><![CDATA[{
  "status": 200,
  "message": "Patient retrieved successfully",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "firstName": "Juan",
    "lastName": "Pérez",
    "nationalId": "35123456",
    "birthDate": "1989-05-14"
  }
}]]></ac:plain-text-body></ac:structured-macro>

<h2 local-id="1c2d3e4f5a6c">Error Responses:</h2>
<table data-table-width="1800" data-layout="align-start" ac:local-id="2d3e4f5a6b7d">
<tbody>
<tr ac:local-id="3e4f5a6b7c8c">
<th ac:local-id="4f5a6b7c8d9b"><p local-id="5a6b7c8d9e0b"><strong>HTTP Status</strong></p></th>
<th ac:local-id="6b7c8d9e0f1c"><p local-id="7c8d9e0f1a2c"><strong>Condition</strong></p></th></tr>
<tr ac:local-id="8d9e0f1a2b3b">
<td ac:local-id="9e0f1a2b3c4b"><p local-id="0f1a2b3c4d5b"><code>404 Not Found</code></p></td>
<td ac:local-id="1a2b3c4d5e6c"><p local-id="2b3c4d5e6f7d">No patient identity record exists for the given UUID.</p></td></tr></tbody></table>

<p local-id="3c4d5e6f7a8d" />
