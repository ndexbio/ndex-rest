ALTER TABLE network
  ADD COLUMN solr_idx_lvl character varying(15)  DEFAULT 'NONE'::character varying;
COMMENT ON COLUMN network.solr_idx_lvl IS 'Solr Index level -- null: no index; ''meta'' Index on metadata. ''all'' full index(metadata and nodes)';

update network set solr_idx_lvl = 'ALL' where solr_indexed = true and is_deleted=false;
update network set solr_idx_lvl = 'NONE' where solr_indexed = false and is_deleted=false;

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
