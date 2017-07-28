

ALTER TABLE ndex_user ADD COLUMN disk_limit bigint;
ALTER TABLE ndex_user ADD COLUMN storage_usage bigint;

ALTER TABLE network ALTER COLUMN error TYPE text;
ALTER TABLE network ADD COLUMN access_key character varying(500);
ALTER TABLE network ADD COLUMN access_key_is_on boolean;
ALTER TABLE network ADD COLUMN cx_file_size bigintstorage_usage bigint;

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


CREATE TABLE network_set (
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
    showcased boolean
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

