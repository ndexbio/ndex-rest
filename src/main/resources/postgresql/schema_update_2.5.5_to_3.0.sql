-- NDEx schema migration: 2.5.5 → 3.0
-- Adds folder, folder_permission, and shortcut tables for the v3 file-system API.
-- Adds network.parent and network.show_in_trash for folder membership and trash support.

-- ── network table additions ────────────────────────────────────────────────

ALTER TABLE IF EXISTS core.network
    ADD COLUMN IF NOT EXISTS parent uuid,
    ADD COLUMN IF NOT EXISTS show_in_trash boolean NOT NULL DEFAULT false;

-- ── folder ────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core.folder
(
    "UUID"              uuid                        NOT NULL,
    creation_time       timestamp without time zone NOT NULL,
    modification_time   timestamp without time zone NOT NULL,
    is_deleted          boolean                     NOT NULL DEFAULT false,
    name                character varying(500)      NOT NULL,
    description         text,
    visibility          character varying(100)      NOT NULL DEFAULT 'PRIVATE',
    owneruuid           uuid                        NOT NULL,
    parent              uuid,
    access_key          character varying(500),
    access_key_is_on    boolean                     NOT NULL DEFAULT false,
    show_in_trash       boolean                     NOT NULL DEFAULT false,
    updated_by          character varying(100),
    CONSTRAINT folder_pkey PRIMARY KEY ("UUID"),
    CONSTRAINT folder_owner_fk FOREIGN KEY (owneruuid)
        REFERENCES core.ndex_user ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE NO ACTION NOT VALID,
    CONSTRAINT folder_parent_fk FOREIGN KEY (parent)
        REFERENCES core.folder ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE NO ACTION NOT VALID
);

ALTER TABLE IF EXISTS core.folder OWNER TO ndexserver;

CREATE INDEX IF NOT EXISTS folder_owner_idx
    ON core.folder USING btree (owneruuid)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS folder_parent_idx
    ON core.folder USING btree (parent)
    WHERE is_deleted = false;

-- ── folder_permission ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core.folder_permission
(
    folder_id  uuid                   NOT NULL,
    user_id    uuid                   NOT NULL,
    permission character varying(50)  NOT NULL,
    CONSTRAINT folder_permission_pkey PRIMARY KEY (folder_id, user_id),
    CONSTRAINT folder_permission_folder_fk FOREIGN KEY (folder_id)
        REFERENCES core.folder ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE CASCADE NOT VALID,
    CONSTRAINT folder_permission_user_fk FOREIGN KEY (user_id)
        REFERENCES core.ndex_user ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE CASCADE NOT VALID
);

ALTER TABLE IF EXISTS core.folder_permission OWNER TO ndexserver;

CREATE INDEX IF NOT EXISTS folder_permission_folder_idx
    ON core.folder_permission USING btree (folder_id);

CREATE INDEX IF NOT EXISTS folder_permission_user_idx
    ON core.folder_permission USING btree (user_id);

-- ── shortcut ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core.shortcut
(
    "UUID"              uuid                        NOT NULL,
    creation_time       timestamp without time zone NOT NULL,
    modification_time   timestamp without time zone NOT NULL,
    is_deleted          boolean                     NOT NULL DEFAULT false,
    name                character varying(500),
    target_type         character varying(50)       NOT NULL,
    target              uuid                        NOT NULL,
    visibility          character varying(100)      NOT NULL DEFAULT 'PRIVATE',
    owneruuid           uuid                        NOT NULL,
    parent              uuid,
    updated_by          character varying(100),
    CONSTRAINT shortcut_pkey PRIMARY KEY ("UUID"),
    CONSTRAINT shortcut_owner_fk FOREIGN KEY (owneruuid)
        REFERENCES core.ndex_user ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE NO ACTION NOT VALID,
    CONSTRAINT shortcut_parent_fk FOREIGN KEY (parent)
        REFERENCES core.folder ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE NO ACTION NOT VALID
);

ALTER TABLE IF EXISTS core.shortcut OWNER TO ndexserver;

CREATE INDEX IF NOT EXISTS shortcut_owner_idx
    ON core.shortcut USING btree (owneruuid)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS shortcut_parent_idx
    ON core.shortcut USING btree (parent)
    WHERE is_deleted = false;

-- ── schema version tracking ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core.schema_version
(
    version    character varying(20)            NOT NULL,
    applied_at timestamp without time zone NOT NULL DEFAULT now()
);

ALTER TABLE IF EXISTS core.schema_version OWNER TO ndexserver;

INSERT INTO core.schema_version (version) VALUES ('3.0');
