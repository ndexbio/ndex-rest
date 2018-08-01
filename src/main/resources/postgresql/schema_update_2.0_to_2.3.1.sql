

ALTER TABLE ndex_user ADD COLUMN disk_limit bigint;
ALTER TABLE ndex_user ADD COLUMN storage_usage bigint;

ALTER TABLE network ALTER COLUMN error TYPE text;
ALTER TABLE network ADD COLUMN access_key character varying(500);
ALTER TABLE network ADD COLUMN access_key_is_on boolean;
ALTER TABLE network ADD COLUMN cx_file_size bigint
ALTER TABLE network ADD COLUMN storage_usage bigint;

--ALTER TABLE network ADD COLUMN solr_indexed boolean DEFAULT false;
ALTER TABLE network ADD COLUMN certified boolean DEFAULT false;
ALTER TABLE network ADD COLUMN solr_idx_lvl character varying(15) DEFAULT 'NONE'::character varying;
ALTER TABLE network ADD COLUMN has_sample boolean DEFAULT false;
ALTER TABLE network ADD COLUMN has_layout boolean DEFAULT false;

ALTER TABLE task ADD COLUMN  user_properties jsonb;
--
-- Name: COLUMN network.cx_file_size; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN core.network.cx_file_size IS 'size of the CX network in bytes.';


--
-- Name: COLUMN network.solr_idx_lvl; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN core.network.solr_idx_lvl IS 'Solr Index level -- null: no index; ''meta'' Index on metadata. ''all'' full index(metadata and nodes)';

--
-- Name: COLUMN task.user_properties; Type: COMMENT; Schema: core; Owner: ndexserver
--

COMMENT ON COLUMN core.task.user_properties IS 'Properties that can be set by user.';

--
-- Name: update_user_when_delete(); Type: FUNCTION; Schema: core; Owner: ndexserver
--

CREATE FUNCTION update_user_when_delete() RETURNS trigger
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

CREATE FUNCTION update_user_when_insert() RETURNS trigger
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

CREATE FUNCTION update_user_when_update() RETURNS trigger
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

ALTER TABLE network_set OWNER TO ndexserver;

CREATE TABLE network_set_member (
    set_id uuid NOT NULL,
    network_id uuid NOT NULL
);


ALTER TABLE network_set_member OWNER TO ndexserver;

ALTER TABLE ONLY network_set_member
    ADD CONSTRAINT network_set_member_pkey PRIMARY KEY (set_id, network_id);
	
ALTER TABLE ONLY network_set
    ADD CONSTRAINT network_set_pkey PRIMARY KEY ("UUID");

CREATE INDEX network_set_owner_id_idx ON network_set USING btree (owner_id) WHERE (is_deleted = false);

CREATE TRIGGER insert_trigger AFTER INSERT ON network FOR EACH ROW EXECUTE PROCEDURE update_user_when_insert();

CREATE TRIGGER logical_delete_trigger AFTER UPDATE OF is_deleted ON network FOR EACH ROW WHEN ((new.is_deleted = true)) EXECUTE PROCEDURE update_user_when_delete();

CREATE TRIGGER update_network AFTER UPDATE OF cx_file_size ON network FOR EACH ROW WHEN ((new.is_deleted = false)) EXECUTE PROCEDURE update_user_when_update();

ALTER TABLE ONLY network_set_member
    ADD CONSTRAINT network_set_member_network_id_fkey FOREIGN KEY (network_id) REFERENCES network("UUID");


ALTER TABLE ONLY network_set_member
    ADD CONSTRAINT network_set_member_set_id_fkey FOREIGN KEY (set_id) REFERENCES network_set("UUID");


ALTER TABLE ONLY network_set
    ADD CONSTRAINT network_set_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES ndex_user("UUID");

    
update network set solr_idx_lvl = 'ALL' where is_deleted=false;
ALTER TABLE core.network
    ADD COLUMN has_sample boolean DEFAULT False;

ALTER TABLE core.network
    ADD COLUMN has_layout boolean DEFAULT False;

update network set has_layout = false where has_layout is null and is_deleted = false;


update network n set has_layout = true where is_deleted = false and has_layout = false  and error is null
   and ( select x.name   from jsonb_to_recordset( n.cxmetadata -> 'metaData')  as x("lastUpdate" text, name text, properties jsonb, "elementCount" bigint, version text, "consistencyGroup" bigint)
   where x."name" = 'cartesianLayout'
  ) is not null;

 update network n set has_sample = true where is_deleted = false and error is null and edgeCount > 500;
  update network n set has_sample = false where has_sample is null;

