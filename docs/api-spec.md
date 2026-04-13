# CampusStore API Specification

Base path: `/api`

Authentication model: session-based (`JSESSIONID`) with CSRF protection for state-changing requests.

Response envelope (typical):

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

Note: Some endpoints may return typed payloads directly depending on controller implementation.

## 1. Authentication

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/auth/login` | Public | Login with local credentials |
| POST | `/auth/logout` | Auth | Logout current session |
| GET | `/auth/me` | Auth | Get authenticated user context |
| PUT | `/auth/password` | Auth | Change password |

## 2. Inventory

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/inventory` | Auth | List inventory items (paged) |
| GET | `/inventory/{id}` | Auth | Get inventory detail |
| POST | `/inventory` | Admin | Create item |
| PUT | `/inventory/{id}` | Admin | Update item |
| DELETE | `/inventory/{id}` | Admin | Deactivate item |

## 3. Search

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/search` | Public | Search with keyword/filters/sort/pagination |
| GET | `/search/trending` | Public | Trending terms (last 7 days) |
| GET | `/search/history` | Auth | Current user search history |

Query parameters for `GET /search` (commonly used):
- `q`
- `categoryId`
- `priceMin`, `priceMax`
- `condition`
- `zoneId`
- `sort` (`newest`, `price-asc`, `price-desc`, `popularity`, `distance`)
- `page`, `size`
- personalization toggle (when supported by endpoint/controller mapping)

## 4. Requests Workflow

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/requests` | Student/Teacher | Create request |
| GET | `/requests/mine` | Auth | List my requests |
| GET | `/requests/pending-approval` | Teacher/Admin | List pending approvals |
| GET | `/requests/{id}` | Auth | Request detail (ownership/assignment enforced) |
| PUT | `/requests/{id}/approve` | Teacher/Admin | Approve request |
| PUT | `/requests/{id}/reject` | Teacher/Admin | Reject request |
| PUT | `/requests/{id}/cancel` | Owner/Admin | Cancel request |
| PUT | `/requests/{id}/start-picking` | Admin | Move APPROVED -> PICKING |
| PUT | `/requests/{id}/ready-for-pickup` | Admin | Move PICKING -> READY_FOR_PICKUP |
| PUT | `/requests/{id}/picked-up` | Admin | Mark request picked up |

## 5. Notifications

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/notifications` | Auth | List notifications |
| GET | `/notifications/unread-count` | Auth | Unread notification count |
| PUT | `/notifications/{id}/read` | Auth | Mark one notification as read |
| PUT | `/notifications/read-all` | Auth | Mark all notifications as read |

## 6. Profile and Preferences

### 6.1 Profile
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/profile` | Auth | Get profile |
| PUT | `/profile` | Auth | Update profile |
| GET | `/profile/addresses` | Auth | List addresses |
| POST | `/profile/addresses` | Auth | Add address |
| PUT | `/profile/addresses/{id}` | Auth | Update address |
| DELETE | `/profile/addresses/{id}` | Auth | Delete address |
| GET | `/profile/contacts` | Auth | List contacts |
| POST | `/profile/contacts` | Auth | Add contact |
| PUT | `/profile/contacts/{id}` | Auth | Update contact |
| DELETE | `/profile/contacts/{id}` | Auth | Delete contact |
| GET | `/profile/tags` | Auth | List interest tags |
| POST | `/profile/tags` | Auth | Add interest tag |
| DELETE | `/profile/tags/{tag}` | Auth | Remove interest tag |

### 6.2 User preferences
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/user/preferences` | Auth | Get user preferences |
| PUT | `/user/preferences/dnd` | Auth | Update DND window |
| PUT | `/user/preferences/personalization` | Auth | Toggle personalization |

`PUT /user/preferences/dnd` semantics:
- `startTime` and `endTime` both `null` => clear DND
- otherwise both required and must differ

### 6.3 Notification preferences
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/notification-preferences` | Auth | Get notification preference matrix |
| PUT | `/notification-preferences` | Auth | Bulk update preferences |

## 7. Browsing and Favorites

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/browsing-history/{itemId}` | Auth | Record browse event |
| GET | `/favorites` | Auth | List favorites |
| POST | `/favorites/{itemId}` | Auth | Add favorite |
| DELETE | `/favorites/{itemId}` | Auth | Remove favorite |

## 8. Admin APIs

### 8.1 Users / org data
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET/POST | `/admin/users` | Admin | List/Create users |
| GET | `/admin/users/{id}` | Admin | Get user detail |
| PUT | `/admin/users/{id}` | Admin | Update user |
| PUT | `/admin/users/{id}/status` | Admin | Update account status |
| PUT | `/admin/users/{id}/roles` | Admin | Update roles |
| GET/POST | `/admin/departments` | Admin | List/Create departments |

### 8.2 Categories
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/categories` | Auth | Category list for normal UI |
| GET/POST | `/admin/categories` | Admin | Admin category management |

### 8.3 Audit and policies
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/admin/audit` | Admin | Query audit logs |
| GET | `/admin/policies` | Admin | List governance/retention policies |
| PUT | `/admin/policies/{entityType}` | Admin | Update policy (audit logged) |

### 8.4 Email outbox (offline export)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/admin/email-outbox` | Admin | List queued email items |
| POST | `/admin/email-outbox/export` | Admin | Export outbox ZIP |

### 8.5 Crawler observability
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET/POST | `/admin/crawler/jobs` | Admin | List/Create crawler jobs |
| PUT | `/admin/crawler/jobs/{id}` | Admin | Update job |
| POST | `/admin/crawler/jobs/{id}/run` | Admin | Trigger run |
| GET | `/admin/crawler/jobs/{id}/failures` | Admin | List failure snapshots |

### 8.6 Zone management
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/admin/zones` | Admin | List zones |
| POST | `/admin/zones` | Admin | Create zone |
| POST | `/admin/zones/distances` | Admin | Set zone-distance relation |

## 9. Warehouse APIs

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET/POST | `/warehouse/locations` | Admin | List/Create storage locations |
| PUT | `/warehouse/locations/{id}` | Admin | Update location attributes |
| POST | `/warehouse/putaway` | Admin | Recommend putaway bins |
| POST | `/warehouse/pick-path` | Admin | Generate pick path from tasks |
| POST | `/warehouse/simulate` | Admin | Strategy simulation report |

## 10. Security and Error Contracts

- Typical auth failures:
  - `401 Unauthorized` for unauthenticated access
  - `403 Forbidden` for authenticated but unauthorized role/object access
- Validation failures: `400 Bad Request` with error payload
- Missing resources: `404 Not Found`
- Rate limits: `429 Too Many Requests` with retry hints when applicable

## 11. Versioning and Compatibility

- Current API is unversioned (`/api/...`).
- Backward-compatible evolution should prefer additive changes to payloads and endpoints.
