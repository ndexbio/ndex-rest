-- DROP TABLE public.request_record_raw;

CREATE TABLE public.request_record_raw
(
  tid character varying(30) NOT NULL, -- transaction id
  start_time timestamp without time zone,
  end_time timestamp without time zone,
  ip character varying(200), -- ip address
  method character varying(20), -- POST or GET or PUT ...
  user_name character varying(50),
  auth_type character varying(10),
  user_agent text,
  function_name character varying(300),
  path text,
  query_params text,
  path_params text,
  data text,
  status integer,
  error text,
  CONSTRAINT request_record_raw_pkey PRIMARY KEY (tid)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE public.request_record_raw
  OWNER TO ndexstats;
COMMENT ON COLUMN public.request_record_raw.tid IS 'transaction id';
COMMENT ON COLUMN public.request_record_raw.ip IS 'ip address';
COMMENT ON COLUMN public.request_record_raw.method IS 'POST or GET or PUT ...';