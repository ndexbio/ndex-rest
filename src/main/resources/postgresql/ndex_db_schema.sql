--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.3
-- Dumped by pg_dump version 9.5.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: ndex; Type: COMMENT; Schema: -; Owner: ndexserver
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


SET search_path = core, pg_catalog;

--
-- Name: ndex_permission_type; Type: TYPE; Schema: core; Owner: ndexserver
--

CREATE TYPE ndex_permission_type AS ENUM (
    'READ',
    'WRITE',
    'ADMIN',
    'MEMBER',
    'GROUPADMIN'
);


ALTER TYPE ndex_permission_type OWNER TO ndexserver;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: group_network_membership; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE group_network_membership (
    group_id uuid NOT NULL,
    network_id uuid NOT NULL,
    permission_type ndex_permission_type
);


ALTER TABLE group_network_membership OWNER TO ndexserver;

--
-- Name: group_network_membership_arc; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE group_network_membership_arc (
    group_id uuid,
    network_id uuid,
    archive_time timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone,
    permission_type ndex_permission_type
);


ALTER TABLE group_network_membership_arc OWNER TO ndexserver;

--
-- Name: ndex_group; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE ndex_group (
    "UUID" uuid NOT NULL,
    creation_time timestamp without time zone,
    group_name character varying(100),
    image_url character varying(500),
    description text,
    is_deleted boolean,
    other_attributes jsonb,
    modification_time timestamp without time zone,
    website_url character varying(200),
    is_from_13 boolean
);


ALTER TABLE ndex_group OWNER TO ndexserver;

--
-- Name: TABLE ndex_group; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE ndex_group IS 'Group info.';


--
-- Name: ndex_group_user; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE ndex_group_user (
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    is_admin boolean
);


ALTER TABLE ndex_group_user OWNER TO ndexserver;

--
-- Name: ndex_group_user_arc; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE ndex_group_user_arc (
    group_id uuid,
    user_id uuid,
    is_admin boolean,
    archive_time timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone
);


ALTER TABLE ndex_group_user_arc OWNER TO ndexserver;

--
-- Name: ndex_user; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE ndex_user (
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
    is_from_13 boolean
);


ALTER TABLE ndex_user OWNER TO ndexserver;

--
-- Name: TABLE ndex_user; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE ndex_user IS 'User info.';


--
-- Name: network; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE network (
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
    is_from_13 boolean,
    show_in_homepage boolean DEFAULT false,
    subnetworkids bigint[]
);


ALTER TABLE network OWNER TO ndexserver;

--
-- Name: TABLE network; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE network IS 'network info.';


--
-- Name: COLUMN network.iscomplete; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN network.iscomplete IS 'For server use only. When all the validation processes and indexes are created for this network, we will set iscomplete to true.';


--
-- Name: COLUMN network.cacheid; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN network.cacheid IS 'deprecated';


--
-- Name: COLUMN network.roid; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN network.roid IS 'deprecated';


--
-- Name: COLUMN network.sourceformat; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN network.sourceformat IS 'deprecated. It is a property now in v2.';


--
-- Name: COLUMN network.is_validated; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN network.is_validated IS 'When we pass the CX validation process, this flag will be set to true.';


--
-- Name: COLUMN network.error; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN network.error IS 'store the last error message if server failed to do any validation or indexing on this network.';


--
-- Name: COLUMN network.warnings; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN network.warnings IS 'Stores warnings.';


--
-- Name: COLUMN network.is_from_13; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN network.is_from_13 IS 'true means this network is migrated from Ndex 1.3';


--
-- Name: COLUMN network.show_in_homepage; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN network.show_in_homepage IS 'Indicate whether the owner of this network want to show this network in ''user page'' page.';


--
-- Name: request; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE request (
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


ALTER TABLE request OWNER TO ndexserver;

--
-- Name: TABLE request; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE request IS 'request info.';


--
-- Name: COLUMN request.response; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN request.response IS 'Valid response type are: DECLINED, ACCEPTED, PENDING;';


--
-- Name: COLUMN request.owner_id; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN request.owner_id IS 'The owner of this request.';


--
-- Name: COLUMN request.request_type; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN request.request_type IS 'The valid request types are: 
1. ''user'' which means user permission request on network; 
2. ''group'' which means group permission request on network;
3. ''membership'' which means user memebership request on group.';


--
-- Name: task; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE task (
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
    resource character varying(200)
);


ALTER TABLE task OWNER TO ndexserver;

--
-- Name: TABLE task; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE task IS 'Task info.';


--
-- Name: user_network_membership; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE user_network_membership (
    user_id uuid NOT NULL,
    network_id uuid NOT NULL,
    permission_type ndex_permission_type,
    show_in_homepage boolean DEFAULT false
);


ALTER TABLE user_network_membership OWNER TO ndexserver;

--
-- Name: COLUMN user_network_membership.show_in_homepage; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN user_network_membership.show_in_homepage IS 'For Supporting NDEx web app. tell if the gratee of this permssion want to show this network in his homepage when other users come to his home page.';


--
-- Name: user_network_membership_arc; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE user_network_membership_arc (
    user_id uuid,
    network_id uuid,
    archive_time timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone,
    permission_type ndex_permission_type
);


ALTER TABLE user_network_membership_arc OWNER TO ndexserver;

--
-- Name: v1_group; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE v1_group (
    rid character varying(20),
    account_name character varying(300),
    group_name character varying(100),
    website_url character varying(300),
    image_url character varying(200),
    modification_time timestamp without time zone,
    creation_time timestamp without time zone,
    description text,
    id uuid NOT NULL
);


ALTER TABLE v1_group OWNER TO ndexserver;

--
-- Name: v1_group_network; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE v1_group_network (
    group_id uuid NOT NULL,
    network_rid character varying(10) NOT NULL,
    type character varying NOT NULL
);


ALTER TABLE v1_group_network OWNER TO ndexserver;

--
-- Name: v1_network; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE v1_network (
    rid character varying(20),
    id uuid NOT NULL,
    creation_time timestamp without time zone,
    modification_time timestamp without time zone,
    visibility character varying(10),
    node_count bigint,
    edge_count bigint,
    description text,
    version text,
    props json,
    provenance json,
    readonly boolean,
    name text,
    source_format character varying(100),
    owner character varying(50)
);


ALTER TABLE v1_network OWNER TO ndexserver;

--
-- Name: v1_request; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE v1_request (
    rid character varying(20),
    id uuid NOT NULL,
    source_uuid uuid,
    destination_uuid uuid,
    message text,
    creation_time timestamp without time zone,
    modification_time timestamp without time zone,
    responder character varying(20),
    response_message text,
    response character varying(20),
    in_request character varying(100),
    out_request character varying(100),
    request_permission character varying(50),
    response_time timestamp without time zone
);


ALTER TABLE v1_request OWNER TO ndexserver;

--
-- Name: v1_task; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE v1_task (
    id uuid NOT NULL,
    creation_time timestamp without time zone,
    modification_time timestamp with time zone,
    description text,
    status character varying(20),
    task_type character varying(30),
    resource uuid,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    rid character varying(20),
    owneruuid uuid,
    format character varying(30),
    attributes json
);


ALTER TABLE v1_task OWNER TO ndexserver;

--
-- Name: v1_user; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE v1_user (
    rid character varying(50),
    id uuid NOT NULL,
    creation_time timestamp without time zone,
    modification_time timestamp without time zone,
    account_name character varying(200),
    password character varying(500),
    description text,
    email character varying(100),
    first_name character varying(100),
    last_name character varying(100),
    image_url character varying(200),
    website_url character varying(200)
);


ALTER TABLE v1_user OWNER TO ndexserver;

--
-- Name: v1_user_group; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE v1_user_group (
    user_id uuid NOT NULL,
    group_rid character varying(10) NOT NULL,
    type character varying(10) NOT NULL
);


ALTER TABLE v1_user_group OWNER TO ndexserver;

--
-- Name: v1_user_network; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE v1_user_network (
    user_id uuid NOT NULL,
    network_rid character varying(20) NOT NULL,
    type character varying(10) NOT NULL
);


ALTER TABLE v1_user_network OWNER TO ndexserver;

--
-- Name: working_migrated_uuids; Type: TABLE; Schema: core; Owner: ndexserver
--

CREATE TABLE working_migrated_uuids (
    table_name character varying(50),
    id uuid NOT NULL
);


ALTER TABLE working_migrated_uuids OWNER TO ndexserver;

--
-- Name: groupNetworkMembership_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY group_network_membership
    ADD CONSTRAINT "groupNetworkMembership_pkey" PRIMARY KEY (group_id, network_id);


--
-- Name: group_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY ndex_group
    ADD CONSTRAINT group_pk PRIMARY KEY ("UUID");


--
-- Name: migrated_uuids_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY working_migrated_uuids
    ADD CONSTRAINT migrated_uuids_pkey PRIMARY KEY (id);


--
-- Name: ndexGroupUser_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY ndex_group_user
    ADD CONSTRAINT "ndexGroupUser_pkey" PRIMARY KEY (group_id, user_id);


--
-- Name: network_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY network
    ADD CONSTRAINT network_pk PRIMARY KEY ("UUID");


--
-- Name: request_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY request
    ADD CONSTRAINT request_pk PRIMARY KEY ("UUID");


--
-- Name: task_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY task
    ADD CONSTRAINT task_pk PRIMARY KEY ("UUID");


--
-- Name: userNetworkMembership_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY user_network_membership
    ADD CONSTRAINT "userNetworkMembership_pkey" PRIMARY KEY (user_id, network_id);


--
-- Name: user_pk; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY ndex_user
    ADD CONSTRAINT user_pk PRIMARY KEY ("UUID");


--
-- Name: v1_group_network_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY v1_group_network
    ADD CONSTRAINT v1_group_network_pkey PRIMARY KEY (group_id, network_rid, type);


--
-- Name: v1_group_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY v1_group
    ADD CONSTRAINT v1_group_pkey PRIMARY KEY (id);


--
-- Name: v1_network_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY v1_network
    ADD CONSTRAINT v1_network_pkey PRIMARY KEY (id);


--
-- Name: v1_request_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY v1_request
    ADD CONSTRAINT v1_request_pkey PRIMARY KEY (id);


--
-- Name: v1_task_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY v1_task
    ADD CONSTRAINT v1_task_pkey PRIMARY KEY (id);


--
-- Name: v1_user_group_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY v1_user_group
    ADD CONSTRAINT v1_user_group_pkey PRIMARY KEY (user_id, group_rid, type);


--
-- Name: v1_user_network_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY v1_user_network
    ADD CONSTRAINT v1_user_network_pkey PRIMARY KEY (user_id, network_rid, type);


--
-- Name: v1_user_pkey; Type: CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY v1_user
    ADD CONSTRAINT v1_user_pkey PRIMARY KEY (id);


--
-- Name: groupNetworkMembership_goupId_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "groupNetworkMembership_goupId_idx" ON group_network_membership USING btree (group_id);


--
-- Name: groupNetworkMembership_networkId_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "groupNetworkMembership_networkId_idx" ON group_network_membership USING btree (network_id);


--
-- Name: group_groupname_constraint; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE UNIQUE INDEX group_groupname_constraint ON ndex_group USING btree (group_name) WHERE (NOT is_deleted);


--
-- Name: ndexGroupUser_groupUUID_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "ndexGroupUser_groupUUID_idx" ON ndex_group_user USING btree (group_id);


--
-- Name: ndexGroupUser_userUUID_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "ndexGroupUser_userUUID_idx" ON ndex_group_user USING btree (user_id);


--
-- Name: network_owner_index; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX network_owner_index ON network USING btree (owneruuid) WHERE (NOT is_deleted);


--
-- Name: network_owneruuid_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX network_owneruuid_idx ON network USING btree (owneruuid);


--
-- Name: request_destinationuuid_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX request_destinationuuid_idx ON request USING btree (destinationuuid) WHERE (is_deleted = false);


--
-- Name: request_owner_id_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX request_owner_id_idx ON request USING btree (owner_id) WHERE (is_deleted = false);


--
-- Name: task_owner_index; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX task_owner_index ON task USING btree (owneruuid) WHERE (NOT is_deleted);


--
-- Name: userNetworkMembership_networkId_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "userNetworkMembership_networkId_idx" ON user_network_membership USING btree (network_id);


--
-- Name: userNetworkMembership_userId_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX "userNetworkMembership_userId_idx" ON user_network_membership USING btree (user_id);


--
-- Name: user_emailaddr_constraint; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE UNIQUE INDEX user_emailaddr_constraint ON ndex_user USING btree (email_addr) WHERE (NOT is_deleted);


--
-- Name: user_username_constraint; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE UNIQUE INDEX user_username_constraint ON ndex_user USING btree (user_name) WHERE (NOT is_deleted);


--
-- Name: v1_group_rid_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE UNIQUE INDEX v1_group_rid_idx ON v1_group USING btree (rid);


--
-- Name: v1_network_rid_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE UNIQUE INDEX v1_network_rid_idx ON v1_network USING btree (rid);


--
-- Name: v1_user_network_network_rid_idx; Type: INDEX; Schema: core; Owner: ndexserver
--

CREATE INDEX v1_user_network_network_rid_idx ON v1_user_network USING btree (network_rid);


--
-- Name: groupUser_groupUUID_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY ndex_group_user
    ADD CONSTRAINT "groupUser_groupUUID_fkey" FOREIGN KEY (group_id) REFERENCES ndex_group("UUID");


--
-- Name: groupUser_userUUID_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY ndex_group_user
    ADD CONSTRAINT "groupUser_userUUID_fkey" FOREIGN KEY (user_id) REFERENCES ndex_user("UUID");


--
-- Name: group_network_membership_group_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY group_network_membership
    ADD CONSTRAINT group_network_membership_group_id_fkey FOREIGN KEY (group_id) REFERENCES ndex_group("UUID");


--
-- Name: request_owner_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY request
    ADD CONSTRAINT request_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES ndex_user("UUID");


--
-- Name: task_ownerUUID_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY task
    ADD CONSTRAINT "task_ownerUUID_fkey" FOREIGN KEY (owneruuid) REFERENCES ndex_user("UUID");


--
-- Name: user_network_membership_network_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY user_network_membership
    ADD CONSTRAINT user_network_membership_network_id_fkey FOREIGN KEY (network_id) REFERENCES network("UUID") ON UPDATE CASCADE;


--
-- Name: user_network_membership_user_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY user_network_membership
    ADD CONSTRAINT user_network_membership_user_id_fkey FOREIGN KEY (user_id) REFERENCES ndex_user("UUID");


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

