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
    
    
-- Table: core.cyweb_workspace_network

-- DROP TABLE IF EXISTS core.cyweb_workspace_network;

CREATE TABLE IF NOT EXISTS core.cyweb_workspace_network
(
    workspaceid uuid NOT NULL,
    networkid uuid NOT NULL,
    CONSTRAINT cyweb_workspace_network_pkey PRIMARY KEY (networkid, workspaceid),
    CONSTRAINT cyweb_ws_network_fk1 FOREIGN KEY (workspaceid)
        REFERENCES core.cyweb_workspace ("UUID") MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT cyweb_ws_network_fk2 FOREIGN KEY (networkid)
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