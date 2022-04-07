--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.25
-- Dumped by pg_dump version 9.5.25

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: DATABASE ndex; Type: COMMENT; Schema: -; Owner: ndexserver
--

COMMENT ON DATABASE ndex IS 'main database to store network, user, group info.';


--
-- Name: core; Type: SCHEMA; Schema: -; Owner: ndexserver
--

CREATE SCHEMA core;


ALTER SCHEMA core OWNER TO ndexserver;

--
-- Name: SCHEMA core; Type: COMMENT; Schema: -; Owner: ndexserver
--

COMMENT ON SCHEMA core IS 'Schema to support ndex core server.';


--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


--
-- Name: dblink; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS dblink WITH SCHEMA core;


--
-- Name: EXTENSION dblink; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION dblink IS 'connect to other PostgreSQL databases from within a database';


--
-- Name: ndex_permission_type; Type: TYPE; Schema: core; Owner: ndexserver
--

CREATE TYPE core.ndex_permission_type AS ENUM (
    'READ',
    'WRITE',
    'ADMIN',
    'MEMBER',
    'GROUPADMIN'
);


ALTER TYPE core.ndex_permission_type OWNER TO ndexserver;

--
-- Name: update_user_when_delete(); Type: FUNCTION; Schema: core; Owner: ndexserver
--

CREATE FUNCTION core.update_user_when_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$   BEGIN
	update ndex_user
	 set storage_usage = storage_usage - NEW.cx_file_size
	where 
	   "UUID"= NEW.owneruuid and storage_usage is not null and 
	      NEW.cx_file_size is not null;
        RETURN NEW;
    END;$$;


ALTER FUNCTION core.update_user_when_delete() OWNER TO ndexserver;

--
-- Name: update_user_when_insert(); Type: FUNCTION; Schema: core; Owner: ndexserver
--

CREATE FUNCTION core.update_user_when_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$   BEGIN
        IF NEW.cx_file_size IS NULL THEN
            RAISE EXCEPTION 'cx_file_size field cannot be null';
        END IF;
       
	update ndex_user
	 set storage_usage =  
	    CASE WHEN storage_usage is null THEN NEW.cx_file_size
		 ELSE  storage_usage + NEW.cx_file_size
	    END
	where 
	   "UUID"= NEW.owneruuid;
        RETURN NEW;
    END;$$;


ALTER FUNCTION core.update_user_when_insert() OWNER TO ndexserver;

--
-- Name: update_user_when_update(); Type: FUNCTION; Schema: core; Owner: ndexserver
--

CREATE FUNCTION core.update_user_when_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$   BEGIN
        IF NEW.cx_file_size IS NULL THEN
            RAISE EXCEPTION 'cx_file_size field cannot be null';
        END IF;
       
	update ndex_user
	 set storage_usage =  
	    CASE WHEN storage_usage is null THEN NEW.cx_file_size
		 WHEN OLD.cx_file_size is null then storage_usage + NEW.cx_file_size
		 else storage_usage -OLD.cx_file_size + NEW.cx_file_size
	    END
	where 
	   "UUID"= NEW.owneruuid;
        RETURN NEW;
    END;$$;


ALTER FUNCTION core.update_user_when_update() OWNER TO ndexserver;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: full_download_count; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.full_download_count (
    network_id uuid NOT NULL,
    d_month date NOT NULL,
    downloads bigint NOT NULL
);


ALTER TABLE core.full_download_count OWNER TO ndexserver;

--
-- Name: group_network_membership; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.group_network_membership (
    group_id uuid NOT NULL,
    network_id uuid NOT NULL,
    permission_type core.ndex_permission_type
);


ALTER TABLE core.group_network_membership OWNER TO ndexserver;

--
-- Name: group_network_membership_arc; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.group_network_membership_arc (
    group_id uuid,
    network_id uuid,
    archive_time timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone,
    permission_type core.ndex_permission_type
);


ALTER TABLE core.group_network_membership_arc OWNER TO ndexserver;

--
-- Name: ndex_group; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.ndex_group (
    "UUID" uuid NOT NULL,
    creation_time timestamp without time zone,
    group_name character varying(100),
    image_url character varying(500),
    description text,
    is_deleted boolean,
    other_attributes jsonb,
    modification_time timestamp without time zone,
    website_url character varying(200)
);


ALTER TABLE core.ndex_group OWNER TO ndexserver;

--
-- Name: TABLE ndex_group; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE core.ndex_group IS 'Group info.';


--
-- Name: ndex_group_user; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.ndex_group_user (
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    is_admin boolean
);


ALTER TABLE core.ndex_group_user OWNER TO ndexserver;

--
-- Name: ndex_group_user_arc; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.ndex_group_user_arc (
    group_id uuid,
    user_id uuid,
    is_admin boolean,
    archive_time timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone
);


ALTER TABLE core.ndex_group_user_arc OWNER TO ndexserver;

--
-- Name: ndex_user; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.ndex_user (
    "UUID" uuid NOT NULL,
    creation_time timestamp without time zone,
    modification_time timestamp without time zone,
    user_name character varying(100),
    display_name character varying(100),
    first_name character varying(100),
    last_name character varying(100),
    image_url character varying(500),
    website_url character varying(500),
    email_addr character varying(200),
    password character varying(500),
    is_individual boolean,
    description text,
    is_deleted boolean,
    other_attributes jsonb,
    is_verified boolean,
    disk_limit bigint,
    storage_usage bigint
);


ALTER TABLE core.ndex_user OWNER TO ndexserver;

--
-- Name: TABLE ndex_user; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE core.ndex_user IS 'User info.';


--
-- Name: network; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.network (
    "UUID" uuid NOT NULL,
    creation_time timestamp without time zone,
    modification_time timestamp without time zone,
    is_deleted boolean,
    name character varying(500),
    description text,
    edgecount integer,
    nodecount integer,
    islocked boolean,
    iscomplete boolean,
    visibility character varying(100),
    cacheid bigint,
    roid bigint,
    owner character varying(100),
    owneruuid uuid,
    sourceformat character varying(100),
    properties jsonb,
    provenance jsonb,
    cxmetadata jsonb,
    version character varying(100),
    ndexdoi character varying(200),
    is_validated boolean DEFAULT false,
    readonly boolean,
    error character varying(2000),
    warnings text[],
    show_in_homepage boolean DEFAULT false,
    subnetworkids bigint[],
    access_key character varying(500),
    access_key_is_on boolean,
    cx_file_size bigint,
    solr_indexed boolean DEFAULT false,
    certified boolean DEFAULT false,
    solr_idx_lvl character varying(15) DEFAULT 'NONE'::character varying,
    has_layout boolean DEFAULT false,
    has_sample boolean DEFAULT false,
    cx2metadata jsonb,
    cx2_file_size bigint,
    cxformat character varying(20)
);


ALTER TABLE core.network OWNER TO ndexserver;

--
-- Name: TABLE network; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE core.network IS 'network info.';


--
-- Name: COLUMN network.warnings; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN core.network.warnings IS 'stores warnings.';


--
-- Name: COLUMN network.cx_file_size; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN core.network.cx_file_size IS 'size of the CX network in bytes.
';


--
-- Name: COLUMN network.solr_indexed; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN core.network.solr_indexed IS 'true if this network is indexed in Solr.';


--
-- Name: COLUMN network.solr_idx_lvl; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN core.network.solr_idx_lvl IS 'Solr Index level -- null: no index; ''meta'' Index on metadata. ''all'' full index(metadata and nodes)';


--
-- Name: network_arc; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.network_arc (
    "UUID" uuid NOT NULL,
    creation_time timestamp without time zone,
    modification_time timestamp without time zone,
    name character varying(500),
    description text,
    edgecount integer,
    nodecount integer,
    visibility character varying(100),
    owner character varying(100),
    sourceformat character varying(100),
    properties jsonb,
    cxmetadata jsonb,
    version character varying(100),
    is_validated boolean DEFAULT false,
    error character varying(2000),
    warnings text[],
    show_in_homepage boolean DEFAULT false,
    subnetworkids bigint[],
    cx_file_size bigint,
    solr_idx_lvl character varying(15) DEFAULT 'NONE'::character varying,
    has_layout boolean DEFAULT false,
    has_sample boolean DEFAULT false,
    cx2metadata jsonb,
    cx2_file_size bigint,
    cxformat character varying(20)
);


ALTER TABLE core.network_arc OWNER TO ndexserver;

--
-- Name: network_set; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.network_set (
    "UUID" uuid NOT NULL,
    name character varying(180),
    description text,
    owner_id uuid,
    creation_time timestamp without time zone,
    modification_time timestamp without time zone,
    is_deleted boolean,
    access_key character varying(500),
    access_key_is_on boolean,
    other_attributes jsonb,
    showcased boolean,
    ndexdoi character varying(100)
);


ALTER TABLE core.network_set OWNER TO ndexserver;

--
-- Name: network_set_member; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.network_set_member (
    set_id uuid NOT NULL,
    network_id uuid NOT NULL
);


ALTER TABLE core.network_set_member OWNER TO ndexserver;

--
-- Name: request; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.request (
    "UUID" uuid NOT NULL,
    creation_time timestamp without time zone,
    modification_time timestamp without time zone,
    is_deleted boolean,
    sourceuuid uuid,
    destinationuuid uuid,
    requestmessage character varying(1000),
    response character varying(100),
    responsemessage character varying(1000),
    requestpermission character varying(1000),
    responsetime timestamp without time zone,
    other_attributes jsonb,
    responder character varying(200),
    owner_id uuid,
    request_type character varying(20)
);


ALTER TABLE core.request OWNER TO ndexserver;

--
-- Name: TABLE request; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE core.request IS 'request info.';


--
-- Name: COLUMN request.owner_id; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN core.request.owner_id IS 'owner of this request.';


--
-- Name: task; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.task (
    "UUID" uuid NOT NULL,
    creation_time timestamp without time zone,
    modification_time timestamp without time zone,
    status character varying(100),
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    task_type character varying(100),
    owneruuid uuid,
    is_deleted boolean,
    other_attributes jsonb,
    description text,
    priority character varying(20),
    progress integer,
    file_format character varying,
    message text,
    resource character varying(200),
    user_properties jsonb
);


ALTER TABLE core.task OWNER TO ndexserver;

--
-- Name: TABLE task; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE core.task IS 'Task info.';


--
-- Name: COLUMN task.user_properties; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN core.task.user_properties IS 'An object that can be updated  by owner.';


--
-- Name: user_network_membership; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.user_network_membership (
    user_id uuid NOT NULL,
    network_id uuid NOT NULL,
    permission_type core.ndex_permission_type,
    show_in_homepage boolean DEFAULT false
);


ALTER TABLE core.user_network_membership OWNER TO ndexserver;

--
-- Name: user_network_membership_arc; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE core.user_network_membership_arc (
    user_id uuid,
    network_id uuid,
    archive_time timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone,
    permission_type core.ndex_permission_type
);


ALTER TABLE core.user_network_membership_arc OWNER TO ndexserver;

--
-- Name: full_download_count_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.full_download_count
    ADD CONSTRAINT full_download_count_pkey PRIMARY KEY (network_id, d_month);


--
-- Name: groupNetworkMembership_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.group_network_membership
    ADD CONSTRAINT "groupNetworkMembership_pkey" PRIMARY KEY (group_id, network_id);


--
-- Name: group_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.ndex_group
    ADD CONSTRAINT group_pk PRIMARY KEY ("UUID");


--
-- Name: ndexGroupUser_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.ndex_group_user
    ADD CONSTRAINT "ndexGroupUser_pkey" PRIMARY KEY (group_id, user_id);


--
-- Name: network_arc_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.network_arc
    ADD CONSTRAINT network_arc_pk PRIMARY KEY ("UUID");


--
-- Name: network_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.network
    ADD CONSTRAINT network_pk PRIMARY KEY ("UUID");


--
-- Name: network_set_member_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.network_set_member
    ADD CONSTRAINT network_set_member_pkey PRIMARY KEY (set_id, network_id);


--
-- Name: network_set_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.network_set
    ADD CONSTRAINT network_set_pkey PRIMARY KEY ("UUID");


--
-- Name: request_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.request
    ADD CONSTRAINT request_pk PRIMARY KEY ("UUID");


--
-- Name: task_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.task
    ADD CONSTRAINT task_pk PRIMARY KEY ("UUID");


--
-- Name: userNetworkMembership_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.user_network_membership
    ADD CONSTRAINT "userNetworkMembership_pkey" PRIMARY KEY (user_id, network_id);


--
-- Name: user_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.ndex_user
    ADD CONSTRAINT user_pk PRIMARY KEY ("UUID");


--
-- Name: full_download_month_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX full_download_month_idx ON core.full_download_count USING btree (d_month);


--
-- Name: groupNetworkMembership_goupId_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "groupNetworkMembership_goupId_idx" ON core.group_network_membership USING btree (group_id);


--
-- Name: groupNetworkMembership_networkId_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "groupNetworkMembership_networkId_idx" ON core.group_network_membership USING btree (network_id);


--
-- Name: group_groupname_constraint; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE UNIQUE INDEX group_groupname_constraint ON core.ndex_group USING btree (group_name) WHERE (NOT is_deleted);


--
-- Name: ndexGroupUser_groupUUID_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "ndexGroupUser_groupUUID_idx" ON core.ndex_group_user USING btree (group_id);


--
-- Name: ndexGroupUser_userUUID_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "ndexGroupUser_userUUID_idx" ON core.ndex_group_user USING btree (user_id);


--
-- Name: network_owner_index; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX network_owner_index ON core.network USING btree (owneruuid) WHERE (NOT is_deleted);


--
-- Name: network_owneruuid_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX network_owneruuid_idx ON core.network USING btree (owneruuid);


--
-- Name: network_set_member_networkId_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "network_set_member_networkId_idx" ON core.network_set_member USING btree (network_id);


--
-- Name: network_set_owner_id_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX network_set_owner_id_idx ON core.network_set USING btree (owner_id) WHERE (is_deleted = false);


--
-- Name: request_owner_id_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX request_owner_id_idx ON core.request USING btree (owner_id) WHERE (is_deleted = false);


--
-- Name: task_owner_index; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX task_owner_index ON core.task USING btree (owneruuid) WHERE (NOT is_deleted);


--
-- Name: userNetworkMembership_networkId_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "userNetworkMembership_networkId_idx" ON core.user_network_membership USING btree (network_id);


--
-- Name: userNetworkMembership_userId_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "userNetworkMembership_userId_idx" ON core.user_network_membership USING btree (user_id);


--
-- Name: user_emailaddr_constraint; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE UNIQUE INDEX user_emailaddr_constraint ON core.ndex_user USING btree (email_addr) WHERE (NOT is_deleted);


--
-- Name: user_username_constraint; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE UNIQUE INDEX user_username_constraint ON core.ndex_user USING btree (user_name) WHERE (NOT is_deleted);


--
-- Name: insert_trigger; Type: TRIGGER; Schema: core; Owner: ndexserver
--

CREATE TRIGGER insert_trigger AFTER INSERT ON core.network FOR EACH ROW EXECUTE PROCEDURE core.update_user_when_insert();


--
-- Name: logical_delete_trigger; Type: TRIGGER; Schema: core; Owner: ndexserver
--

CREATE TRIGGER logical_delete_trigger AFTER UPDATE OF is_deleted ON core.network FOR EACH ROW WHEN ((new.is_deleted = true)) EXECUTE PROCEDURE core.update_user_when_delete();


--
-- Name: update_network; Type: TRIGGER; Schema: core; Owner: ndexserver
--

CREATE TRIGGER update_network AFTER UPDATE OF cx_file_size ON core.network FOR EACH ROW WHEN ((new.is_deleted = false)) EXECUTE PROCEDURE core.update_user_when_update();


--
-- Name: groupUser_groupUUID_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.ndex_group_user
    ADD CONSTRAINT "groupUser_groupUUID_fkey" FOREIGN KEY (group_id) REFERENCES core.ndex_group("UUID");


--
-- Name: groupUser_userUUID_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.ndex_group_user
    ADD CONSTRAINT "groupUser_userUUID_fkey" FOREIGN KEY (user_id) REFERENCES core.ndex_user("UUID");


--
-- Name: network_set_member_network_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.network_set_member
    ADD CONSTRAINT network_set_member_network_id_fkey FOREIGN KEY (network_id) REFERENCES core.network("UUID");


--
-- Name: network_set_member_set_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.network_set_member
    ADD CONSTRAINT network_set_member_set_id_fkey FOREIGN KEY (set_id) REFERENCES core.network_set("UUID");


--
-- Name: network_set_owner_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.network_set
    ADD CONSTRAINT network_set_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES core.ndex_user("UUID");


--
-- Name: task_ownerUUID_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY core.task
    ADD CONSTRAINT "task_ownerUUID_fkey" FOREIGN KEY (owneruuid) REFERENCES core.ndex_user("UUID");


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

