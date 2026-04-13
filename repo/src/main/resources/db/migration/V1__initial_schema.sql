-- ============================================================
-- V1__initial_schema.sql
-- Flyway migration: initial CampusStore schema for MySQL 8.0
-- ============================================================

-- -----------------------------------------------------------
-- 1. department
-- -----------------------------------------------------------
CREATE TABLE department (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 2. zone
-- -----------------------------------------------------------
CREATE TABLE zone (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    building    VARCHAR(100) NULL,
    floor_level INT          NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_zone_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 3. deleted_user_placeholder
-- -----------------------------------------------------------
CREATE TABLE deleted_user_placeholder (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    original_user_id BIGINT      NOT NULL,
    hashed_identity  VARCHAR(64) NOT NULL,
    deleted_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_deleted_user_original_id (original_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 4. user
-- -----------------------------------------------------------
CREATE TABLE `user` (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    username                VARCHAR(50)  NOT NULL,
    password_hash_encrypted VARBINARY(512) NOT NULL,
    display_name            VARCHAR(100) NOT NULL,
    email_encrypted         VARBINARY(512)  NULL,
    phone_encrypted         VARBINARY(512)  NULL,
    phone_last4             VARCHAR(4)      NULL,
    home_zone_id            BIGINT          NULL,
    department_id           BIGINT          NULL,
    account_status          ENUM('ACTIVE','DISABLED','BLACKLISTED') NOT NULL DEFAULT 'ACTIVE',
    disabled_at             TIMESTAMP       NULL,
    personalization_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    INDEX idx_user_account_status (account_status),
    INDEX idx_user_department_id (department_id),
    CONSTRAINT fk_user_home_zone   FOREIGN KEY (home_zone_id)   REFERENCES zone (id),
    CONSTRAINT fk_user_department  FOREIGN KEY (department_id)   REFERENCES department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 5. user_role
-- -----------------------------------------------------------
CREATE TABLE user_role (
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    user_id    BIGINT    NOT NULL,
    role       ENUM('STUDENT','TEACHER','ADMIN') NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by BIGINT    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role),
    CONSTRAINT fk_user_role_user       FOREIGN KEY (user_id)    REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_granted_by FOREIGN KEY (granted_by) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 6. user_address
-- -----------------------------------------------------------
CREATE TABLE user_address (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    user_id          BIGINT         NOT NULL,
    label            VARCHAR(50)    NULL,
    street_encrypted VARBINARY(1024) NULL,
    city_encrypted   VARBINARY(512)  NULL,
    state_encrypted  VARBINARY(512) NULL,
    zip_code_encrypted VARBINARY(512) NULL,
    is_primary       BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 7. user_tag
-- -----------------------------------------------------------
CREATE TABLE user_tag (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    tag        VARCHAR(50) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_tag (user_id, tag),
    CONSTRAINT fk_user_tag_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 8. user_preference
-- -----------------------------------------------------------
CREATE TABLE user_preference (
    id                      BIGINT    NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT    NOT NULL,
    dnd_start_time          TIME      NULL,
    dnd_end_time            TIME      NULL,
    personalization_enabled BOOLEAN   NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_preference_user (user_id),
    CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 9. zone_distance
-- -----------------------------------------------------------
CREATE TABLE zone_distance (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    from_zone_id BIGINT        NOT NULL,
    to_zone_id   BIGINT        NOT NULL,
    weight       DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_zone_distance (from_zone_id, to_zone_id),
    INDEX idx_zone_distance_from (from_zone_id),
    INDEX idx_zone_distance_to (to_zone_id),
    CONSTRAINT fk_zone_distance_from FOREIGN KEY (from_zone_id) REFERENCES zone (id),
    CONSTRAINT fk_zone_distance_to   FOREIGN KEY (to_zone_id)   REFERENCES zone (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 10. storage_location
-- -----------------------------------------------------------
CREATE TABLE storage_location (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    zone_id           BIGINT        NULL,
    name              VARCHAR(100)  NOT NULL,
    x_coord           DECIMAL(10,2) NULL,
    y_coord           DECIMAL(10,2) NULL,
    level             INT           NOT NULL DEFAULT 0,
    temperature_zone  ENUM('AMBIENT','COOL','COLD','FROZEN')            NOT NULL DEFAULT 'AMBIENT',
    security_level    ENUM('STANDARD','RESTRICTED','HIGH_SECURITY')     NOT NULL DEFAULT 'STANDARD',
    capacity          INT           NOT NULL DEFAULT 100,
    current_occupancy INT           NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_storage_location_zone (zone_id),
    INDEX idx_storage_location_temp_zone (temperature_zone),
    INDEX idx_storage_location_security (security_level),
    CONSTRAINT fk_storage_location_zone FOREIGN KEY (zone_id) REFERENCES zone (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 11. category  (self-referencing)
-- -----------------------------------------------------------
CREATE TABLE category (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    parent_id   BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 12. inventory_item
-- -----------------------------------------------------------
CREATE TABLE inventory_item (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    name               VARCHAR(200)  NOT NULL,
    description        TEXT          NULL,
    sku                VARCHAR(50)   NOT NULL,
    category_id        BIGINT        NULL,
    department_id      BIGINT        NULL,
    location_id        BIGINT        NULL,
    price_usd          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    item_condition     ENUM('NEW','LIKE_NEW','GOOD','FAIR','POOR') NOT NULL DEFAULT 'NEW',
    quantity_total     INT           NOT NULL DEFAULT 0,
    quantity_available INT           NOT NULL DEFAULT 0,
    requires_approval  BOOLEAN       NOT NULL DEFAULT FALSE,
    abc_classification ENUM('A','B','C') NOT NULL DEFAULT 'C',
    expiration_date    DATE          NULL,
    popularity_score   INT           NOT NULL DEFAULT 0,
    heat_score         DECIMAL(10,4) NOT NULL DEFAULT 0.0,
    is_active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_item_sku (sku),
    INDEX idx_inventory_item_category (category_id),
    INDEX idx_inventory_item_department (department_id),
    INDEX idx_inventory_item_location (location_id),
    INDEX idx_inventory_item_condition (item_condition),
    INDEX idx_inventory_item_abc (abc_classification),
    INDEX idx_inventory_item_active (is_active),
    INDEX idx_inventory_item_popularity (popularity_score),
    INDEX idx_inventory_item_heat (heat_score),
    FULLTEXT INDEX ft_inventory_item_name_desc (name, description),
    CONSTRAINT fk_inventory_item_category   FOREIGN KEY (category_id)   REFERENCES category (id),
    CONSTRAINT fk_inventory_item_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT fk_inventory_item_location   FOREIGN KEY (location_id)   REFERENCES storage_location (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 13. item_request
-- -----------------------------------------------------------
CREATE TABLE item_request (
    id               BIGINT    NOT NULL AUTO_INCREMENT,
    requester_id     BIGINT    NOT NULL,
    item_id          BIGINT    NOT NULL,
    approver_id      BIGINT    NULL,
    quantity         INT       NOT NULL DEFAULT 1,
    status           ENUM('PENDING_APPROVAL','APPROVED','AUTO_APPROVED','REJECTED',
                          'PICKING','READY_FOR_PICKUP','PICKED_UP','CANCELLED','OVERDUE')
                               NOT NULL DEFAULT 'PENDING_APPROVAL',
    justification    TEXT      NULL,
    rejection_reason TEXT      NULL,
    approved_at      TIMESTAMP NULL,
    picked_up_at     TIMESTAMP NULL,
    pickup_deadline  TIMESTAMP NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_item_request_requester (requester_id),
    INDEX idx_item_request_item (item_id),
    INDEX idx_item_request_approver (approver_id),
    INDEX idx_item_request_status (status),
    INDEX idx_item_request_created (created_at),
    CONSTRAINT fk_item_request_requester FOREIGN KEY (requester_id) REFERENCES `user` (id),
    CONSTRAINT fk_item_request_item      FOREIGN KEY (item_id)      REFERENCES inventory_item (id),
    CONSTRAINT fk_item_request_approver  FOREIGN KEY (approver_id)  REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 14. pick_task
-- -----------------------------------------------------------
CREATE TABLE pick_task (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    request_id      BIGINT        NOT NULL,
    location_id     BIGINT        NOT NULL,
    assigned_to     BIGINT        NULL,
    status          ENUM('PENDING','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    pick_path_cost  DECIMAL(10,4) NULL,
    sequence_order  INT           NULL,
    started_at      TIMESTAMP     NULL,
    completed_at    TIMESTAMP     NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_pick_task_request (request_id),
    INDEX idx_pick_task_assigned (assigned_to),
    INDEX idx_pick_task_status (status),
    CONSTRAINT fk_pick_task_request  FOREIGN KEY (request_id)  REFERENCES item_request (id),
    CONSTRAINT fk_pick_task_location FOREIGN KEY (location_id) REFERENCES storage_location (id),
    CONSTRAINT fk_pick_task_assigned FOREIGN KEY (assigned_to) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 15. search_log
-- -----------------------------------------------------------
CREATE TABLE search_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NULL,
    query_text   VARCHAR(500) NOT NULL,
    filters_json JSON         NULL,
    result_count INT          NOT NULL DEFAULT 0,
    searched_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_search_log_user (user_id),
    INDEX idx_search_log_searched_at (searched_at),
    CONSTRAINT fk_search_log_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 16. browsing_history
-- -----------------------------------------------------------
CREATE TABLE browsing_history (
    id        BIGINT    NOT NULL AUTO_INCREMENT,
    user_id   BIGINT    NOT NULL,
    item_id   BIGINT    NOT NULL,
    viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_browsing_history_user_viewed (user_id, viewed_at DESC),
    CONSTRAINT fk_browsing_history_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT fk_browsing_history_item FOREIGN KEY (item_id) REFERENCES inventory_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 17. favorite
-- -----------------------------------------------------------
CREATE TABLE favorite (
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    user_id    BIGINT    NOT NULL,
    item_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite_user_item (user_id, item_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_item FOREIGN KEY (item_id) REFERENCES inventory_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 18. notification
-- -----------------------------------------------------------
CREATE TABLE notification (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    type           ENUM('REQUEST_SUBMITTED','REQUEST_APPROVED','REQUEST_REJECTED',
                        'PICKUP_READY','PICKUP_OVERDUE','MISSED_CHECKIN',
                        'SYSTEM_ALERT','CRAWLER_ALERT','POLICY_CHANGE') NOT NULL,
    title          VARCHAR(200) NOT NULL,
    message        TEXT         NULL,
    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_critical    BOOLEAN      NOT NULL DEFAULT FALSE,
    dnd_suppressed BOOLEAN      NOT NULL DEFAULT FALSE,
    reference_type VARCHAR(50)  NULL,
    reference_id   BIGINT       NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at        TIMESTAMP    NULL,
    PRIMARY KEY (id),
    INDEX idx_notification_user (user_id),
    INDEX idx_notification_user_read (user_id, is_read),
    INDEX idx_notification_type (type),
    INDEX idx_notification_created (created_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 19. notification_preference
-- -----------------------------------------------------------
CREATE TABLE notification_preference (
    id                BIGINT  NOT NULL AUTO_INCREMENT,
    user_id           BIGINT  NOT NULL,
    notification_type ENUM('REQUEST_SUBMITTED','REQUEST_APPROVED','REQUEST_REJECTED',
                           'PICKUP_READY','PICKUP_OVERDUE','MISSED_CHECKIN',
                           'SYSTEM_ALERT','CRAWLER_ALERT','POLICY_CHANGE') NOT NULL,
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_pref_user_type (user_id, notification_type),
    CONSTRAINT fk_notification_pref_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 20. email_outbox
-- -----------------------------------------------------------
CREATE TABLE email_outbox (
    id                          BIGINT         NOT NULL AUTO_INCREMENT,
    recipient_user_id           BIGINT         NULL,
    recipient_address_encrypted VARBINARY(512) NULL,
    subject                     VARCHAR(200)   NOT NULL,
    body_text                   TEXT           NULL,
    body_html                   TEXT           NULL,
    eml_data                    LONGTEXT       NULL,
    status                      ENUM('QUEUED','EXPORTED','FAILED') NOT NULL DEFAULT 'QUEUED',
    created_at                  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exported_at                 TIMESTAMP      NULL,
    PRIMARY KEY (id),
    INDEX idx_email_outbox_status (status),
    INDEX idx_email_outbox_recipient (recipient_user_id),
    INDEX idx_email_outbox_created (created_at),
    CONSTRAINT fk_email_outbox_user FOREIGN KEY (recipient_user_id) REFERENCES `user` (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 21. audit_log  (actor_user_id has NO FK to user)
-- -----------------------------------------------------------
CREATE TABLE audit_log (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id        BIGINT       NULL,
    actor_placeholder_id BIGINT       NULL,
    actor_username_hash  VARCHAR(64)  NULL,
    action               VARCHAR(100) NOT NULL,
    entity_type          VARCHAR(50)  NULL,
    entity_id            BIGINT       NULL,
    details_json         JSON         NULL,
    ip_address           VARCHAR(45)  NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_audit_log_actor (actor_user_id),
    INDEX idx_audit_log_action (action),
    INDEX idx_audit_log_entity (entity_type, entity_id),
    INDEX idx_audit_log_created (created_at),
    INDEX idx_audit_log_placeholder (actor_placeholder_id),
    CONSTRAINT fk_audit_log_placeholder FOREIGN KEY (actor_placeholder_id) REFERENCES deleted_user_placeholder (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 22. crawler_job
-- -----------------------------------------------------------
CREATE TABLE crawler_job (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    source_type     ENUM('FILE','INTRANET_PAGE') NOT NULL,
    source_path     VARCHAR(500) NOT NULL,
    cron_expression VARCHAR(50)  NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMP    NULL,
    success_rate    DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    avg_latency_ms  INT          NOT NULL DEFAULT 0,
    parse_hit_rate  DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    anti_bot_blocks INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 23. crawler_task
-- -----------------------------------------------------------
CREATE TABLE crawler_task (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    job_id         BIGINT      NOT NULL,
    status         ENUM('PENDING','RUNNING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
    started_at     TIMESTAMP   NULL,
    completed_at   TIMESTAMP   NULL,
    latency_ms     INT         NULL,
    parse_hit      BOOLEAN     NULL,
    error_message  TEXT        NULL,
    raw_content    LONGBLOB    NULL,
    parser_version VARCHAR(50) NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_crawler_task_job (job_id),
    INDEX idx_crawler_task_status (status),
    INDEX idx_crawler_task_created (created_at),
    CONSTRAINT fk_crawler_task_job FOREIGN KEY (job_id) REFERENCES crawler_job (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 24. rate_limit_violation
-- -----------------------------------------------------------
CREATE TABLE rate_limit_violation (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    identifier         VARCHAR(200) NOT NULL,
    identifier_type    ENUM('ANONYMOUS','AUTHENTICATED') NOT NULL,
    violation_count    INT          NOT NULL DEFAULT 1,
    lockout_until      TIMESTAMP    NULL,
    first_violation_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_violation_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_rate_limit_identifier (identifier),
    INDEX idx_rate_limit_type (identifier_type),
    INDEX idx_rate_limit_lockout (lockout_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 25. path_cost_config
-- -----------------------------------------------------------
CREATE TABLE path_cost_config (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100)  NOT NULL,
    level_multiplier DECIMAL(5,2)  NOT NULL DEFAULT 2.0,
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    config_json      JSON          NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 26. data_retention_policy
-- -----------------------------------------------------------
CREATE TABLE data_retention_policy (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    entity_type     VARCHAR(50) NOT NULL,
    retention_days  INT         NOT NULL,
    description     VARCHAR(500) NULL,
    last_cleanup_at TIMESTAMP   NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_retention_entity_type (entity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------
-- 27. dispute
-- -----------------------------------------------------------
CREATE TABLE dispute (
    id          BIGINT    NOT NULL AUTO_INCREMENT,
    user_id     BIGINT    NOT NULL,
    request_id  BIGINT    NOT NULL,
    reason      TEXT      NULL,
    status      ENUM('OPEN','RESOLVED','DISMISSED') NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    INDEX idx_dispute_user (user_id),
    INDEX idx_dispute_request (request_id),
    INDEX idx_dispute_status (status),
    CONSTRAINT fk_dispute_user    FOREIGN KEY (user_id)    REFERENCES `user` (id),
    CONSTRAINT fk_dispute_request FOREIGN KEY (request_id) REFERENCES item_request (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
