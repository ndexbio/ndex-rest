-- Table: core.cyweb_workspace

-- DROP TABLE IF EXISTS core.cyweb_workspace;

CREATE TABLE IF NOT EXISTS core.cyweb_workspace
(
    "UUID" uuid NOT NULL,
    name character varying(200) COLLATE pg_catalog."default" NOT NULL,
    creation_time timestamp without time zone NOT NULL,
    modification_time timestamp without time zone NOT NULL,
    is_deleted boolean NOT NULL,
    owner_uuid uuid NOT NULL,
    options json,
    CONSTRAINT cyweb_workspace_pkey PRIMARY KEY ("UUID"),
    CONSTRAINT cyweb_workspace_uk1 UNIQUE (owner_uuid, name),
    CONSTRAINT cyweb_workspace_fk1 FOREIGN KEY (owner_uuid)
        REFERENCES core.ndex_user ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE IF EXISTS core.cyweb_workspace
    OWNER to ndexserver;
-- Index: cyweb_workspace_idx1

-- DROP INDEX IF EXISTS core.cyweb_workspace_idx1;

CREATE INDEX IF NOT EXISTS cyweb_workspace_idx1
    ON core.cyweb_workspace USING btree
    (owner_uuid ASC NULLS LAST)
    TABLESPACE pg_default;
    
-- Table: core.cyweb_workspace_network

-- DROP TABLE IF EXISTS core.cyweb_workspace_network;

CREATE TABLE IF NOT EXISTS core.cyweb_workspace_network
(
    workspace_id uuid NOT NULL,
    network_id uuid NOT NULL,
    CONSTRAINT cyweb_workspace_network_pkey PRIMARY KEY (network_id, workspace_id),
    CONSTRAINT cyweb_ws_network_fk1 FOREIGN KEY (workspace_id)
        REFERENCES core.cyweb_workspace ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT cyweb_ws_network_fk2 FOREIGN KEY (network_id)
        REFERENCES core.network ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE IF EXISTS core.cyweb_workspace_network
    OWNER to ndexserver;
-- Index: cyweb_workspace_network_idx

-- DROP INDEX IF EXISTS core.cyweb_workspace_network_idx;

CREATE INDEX IF NOT EXISTS cyweb_workspace_network_idx
    ON core.cyweb_workspace_network USING btree
    (workspace_id ASC NULLS LAST)
    TABLESPACE pg_default;
-- Index: cyweb_workspace_network_idx2

-- DROP INDEX IF EXISTS core.cyweb_workspace_network_idx2;

CREATE INDEX IF NOT EXISTS cyweb_workspace_network_idx2
    ON core.cyweb_workspace_network USING btree
    (network_id ASC NULLS LAST)
    TABLESPACE pg_default;