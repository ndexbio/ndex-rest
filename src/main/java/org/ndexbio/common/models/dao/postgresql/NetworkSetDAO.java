package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NetworkSet;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Read-only DAO over the frozen, archived {@code network_set} / {@code network_set_member} tables.
 *
 * <p>The network set feature was removed in favor of v3 folders; the legacy tables are retained
 * read-only. This DAO exposes only what the two re-enabled read endpoints
 * ({@code GET /v2/networkset/{id}} and {@code GET /v2/networkset/{id}/accesskey}) need. It reads the
 * archived network-set data directly from those tables and never reconstructs data from the v3
 * folder/shortcut model. There are no write methods here.
 */
public class NetworkSetDAO extends NdexDBDAO {

	public NetworkSetDAO() throws SQLException {
		super();
	}

	/** For unit tests: inject a (mock) connection. */
	public NetworkSetDAO(Connection connection) {
		super(connection);
	}

	public boolean isNetworkSetOwner(UUID setId, UUID ownerId) throws SQLException {
		String sqlStr = "select 1 from network_set where \"UUID\" = ? and owner_id = ? and is_deleted=false";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, setId);
			p.setObject(2, ownerId);
			try (ResultSet rs = p.executeQuery()) {
				return rs.next();
			}
		}
	}

	public NetworkSet getNetworkSet(UUID setId, UUID userId, String accessKey) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException {

		NetworkSet result = new NetworkSet();
		String sqlStr = "select creation_time, modification_time, owner_id, name, description, access_key, access_key_is_on, other_attributes,showcased, ndexdoi from network_set  where \"UUID\"=? and is_deleted=false";

		String dbAccessKey = null;
		boolean dbKeyIsOn;
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, setId);
			try (ResultSet rs = p.executeQuery()) {
				if (rs.next()) {
					result.setCreationTime(rs.getTimestamp(1));
					result.setModificationTime(rs.getTimestamp(2));
					result.setExternalId(setId);
					result.setOwnerId((UUID) rs.getObject(3));
					result.setName(rs.getString(4));
					result.setDescription(rs.getString(5));
					dbAccessKey = rs.getString(6);
					dbKeyIsOn = rs.getBoolean(7);

					String propStr = rs.getString(8);

					if (propStr != null) {
						ObjectMapper mapper = new ObjectMapper();
						TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {/**/};

						HashMap<String, Object> o = mapper.readValue(propStr, typeRef);
						result.setProperties(o);
					}

					result.setShowcased(rs.getBoolean(9));
					result.setDoi(rs.getString(10));
				} else
					throw new ObjectNotFoundException("Network set", setId);
			}
		}

		boolean keyIsValid = false;
		// accessKey is proven non-null here, so call equals() on it — the stored access_key column
		// is nullable, so dbAccessKey.equals(...) could NPE.
		if (dbKeyIsOn && accessKey != null && accessKey.equals(dbAccessKey))
			keyIsValid = true;
		if (!keyIsValid && accessKey != null)
			throw new UnauthorizedOperationException("Invalid network set access key.");

		loadReadableMembers(result, setId, userId, keyIsValid);

		return result;
	}

	/**
	 * Lists the archived network sets owned by {@code ownerId} from the frozen {@code network_set} table
	 * (never the v3 folder model). Members of each set are filtered to the networks {@code signedInUserId}
	 * may read (same visibility rule as {@link #getNetworkSet}); there is no access key on this endpoint.
	 *
	 * @param offset       row offset for paging; ignored unless {@code limit > 0}
	 * @param limit        max sets to return; {@code <= 0} means no limit
	 * @param summaryOnly  when true, set headers are returned without loading their member networks
	 * @param showcasedOnly when true, restrict to sets with {@code showcased = true}
	 * @return the (possibly empty) list of the owner's non-deleted archived network sets
	 */
	public List<NetworkSet> getNetworkSetsByUserId(UUID ownerId, UUID signedInUserId, int offset, int limit,
			boolean summaryOnly, boolean showcasedOnly)
			throws SQLException, JsonParseException, JsonMappingException, IOException {

		List<NetworkSet> result = new ArrayList<>();

		StringBuilder sqlStr = new StringBuilder(
				"select \"UUID\", creation_time, modification_time, owner_id, name, description, "
				+ "other_attributes, showcased, ndexdoi from network_set where owner_id=? and is_deleted=false");
		if (showcasedOnly)
			sqlStr.append(" and showcased=true");
		sqlStr.append(" order by creation_time");
		// offset/limit are ints (not caller strings), so inlining them is injection-safe.
		if (limit > 0)
			sqlStr.append(" limit ").append(limit).append(" offset ").append(Math.max(offset, 0));

		try (PreparedStatement p = db.prepareStatement(sqlStr.toString())) {
			p.setObject(1, ownerId);
			try (ResultSet rs = p.executeQuery()) {
				ObjectMapper mapper = new ObjectMapper();
				TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {/**/};
				while (rs.next()) {
					NetworkSet set = new NetworkSet();
					set.setExternalId((UUID) rs.getObject(1));
					set.setCreationTime(rs.getTimestamp(2));
					set.setModificationTime(rs.getTimestamp(3));
					set.setOwnerId((UUID) rs.getObject(4));
					set.setName(rs.getString(5));
					set.setDescription(rs.getString(6));
					String propStr = rs.getString(7);
					if (propStr != null)
						set.setProperties(mapper.readValue(propStr, typeRef));
					set.setShowcased(rs.getBoolean(8));
					set.setDoi(rs.getString(9));
					result.add(set);
				}
			}
		}

		// Members are loaded in a second pass, after the header statement above is closed (one active
		// statement per connection). No access key on this endpoint, so members follow readability.
		if (!summaryOnly) {
			for (NetworkSet set : result) {
				loadReadableMembers(set, set.getExternalId(), signedInUserId, false);
			}
		}

		return result;
	}

	/**
	 * Appends the readable member network ids of {@code setId} to {@code set}'s network list, reading only
	 * the frozen {@code network_set_member} / {@code network} tables. When {@code keyIsValid} is true a
	 * valid access key was presented and all members are returned; otherwise members are restricted to the
	 * networks {@code signedInUserId} may read.
	 */
	private void loadReadableMembers(NetworkSet set, UUID setId, UUID signedInUserId, boolean keyIsValid)
			throws SQLException {
		String sqlStr = "select nm.network_id from network_set_member nm, network n where nm.set_id =? and n.\"UUID\"=nm.network_id and " +
				(keyIsValid ? " true" : PostgresNetworkDAO.createIsReadableConditionStr(signedInUserId));

		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, setId);
			try (ResultSet rs = p.executeQuery()) {
				List<UUID> networkIds = set.getNetworks();
				while (rs.next()) {
					networkIds.add((UUID) rs.getObject(1));
				}
			}
		}
	}

	public String getNetworkSetAccessKey(UUID networkSetId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select access_key, access_key_is_on from network_set where \"UUID\" = ? and is_deleted=false";

		String oldKey = null;
		boolean keyIsOn = false;

		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkSetId);
			try (ResultSet rs = p.executeQuery()) {
				if (rs.next()) {
					oldKey = rs.getString(1);
					keyIsOn = rs.getBoolean(2);
				} else
					throw new ObjectNotFoundException("Network set", networkSetId);

			}
		}

		if (keyIsOn)
			return oldKey;

		return null;

	}
}
