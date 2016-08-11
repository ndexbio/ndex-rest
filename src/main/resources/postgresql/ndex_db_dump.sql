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
    website_url character varying(200)
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
    modification_ime timestamp without time zone,
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
    is_verified boolean
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
    is_validated boolean DEFAULT false
);


ALTER TABLE network OWNER TO ndexserver;

--
-- Name: TABLE network; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE network IS 'network info.';


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
    responder character varying(200)
);


ALTER TABLE request OWNER TO ndexserver;

--
-- Name: TABLE request; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON TABLE request IS 'request info.';


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
    owneruuid uuid NOT NULL,
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
    permission_type ndex_permission_type
);


ALTER TABLE user_network_membership OWNER TO ndexserver;

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
-- Data for Name: group_network_membership; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY group_network_membership (group_id, network_id, permission_type) FROM stdin;
\.


--
-- Data for Name: group_network_membership_arc; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY group_network_membership_arc (group_id, network_id, archive_time, permission_type) FROM stdin;
\.


--
-- Data for Name: ndex_group; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY ndex_group ("UUID", creation_time, group_name, image_url, description, is_deleted, other_attributes, modification_time, website_url) FROM stdin;
\.


--
-- Data for Name: ndex_group_user; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY ndex_group_user (group_id, user_id, is_admin) FROM stdin;
\.


--
-- Data for Name: ndex_group_user_arc; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY ndex_group_user_arc (group_id, user_id, is_admin, archive_time) FROM stdin;
5dcb9523-3ee3-11e6-a5c7-06603eb7f303	5dcb9523-3ee3-11e6-a5c7-06603eb7f303	f	2016-08-03 14:34:12.386252
5dcb9523-3ee3-11e6-a5c7-06603eb7f303	5dcb9523-3ee3-11e6-a5c7-06603eb7f303	f	2016-08-03 14:34:48.7829
5dcb9523-3ee3-11e6-a5c7-06603eb7f303	5dcb9523-3ee3-11e6-a5c7-06603eb7f303	f	2016-08-03 14:35:18.240613
\.


--
-- Data for Name: ndex_user; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY ndex_user ("UUID", creation_time, modification_ime, user_name, display_name, first_name, last_name, image_url, website_url, email_addr, password, is_individual, description, is_deleted, other_attributes, is_verified) FROM stdin;
\.


--
-- Data for Name: network; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY network ("UUID", creation_time, modification_time, is_deleted, name, description, edgecount, nodecount, islocked, iscomplete, visibility, cacheid, roid, owner, owneruuid, sourceformat, properties, provenance, cxmetadata, version, ndexdoi, is_validated) FROM stdin;
\.


--
-- Data for Name: request; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY request ("UUID", creation_time, modification_time, is_deleted, sourceuuid, destinationuuid, requestmessage, response, responsemessage, requestpermission, responsetime, other_attributes, responder) FROM stdin;
\.


--
-- Data for Name: task; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY task ("UUID", creation_time, modification_time, status, start_time, end_time, task_type, owneruuid, is_deleted, other_attributes, description, priority, progress, file_format, message, resource) FROM stdin;
\.


--
-- Data for Name: user_network_membership; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY user_network_membership (user_id, network_id, permission_type) FROM stdin;
\.


--
-- Data for Name: user_network_membership_arc; Type: TABLE DATA; Schema: core; Owner: ndexserver
--

COPY user_network_membership_arc (user_id, network_id, archive_time, permission_type) FROM stdin;
\.


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
-- Name: task_ownerUUID_fkey; Type: FK CONSTRAINT; Schema: core; Owner: ndexserver
--

ALTER TABLE ONLY task
    ADD CONSTRAINT "task_ownerUUID_fkey" FOREIGN KEY (owneruuid) REFERENCES ndex_user("UUID");


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

