package org.ndexbio.common.models.dao;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.exceptions.NetworkConcurrentModificationException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.NetworkSummaryFormat;
import org.ndexbio.model.object.network.NetworkSummaryV3;
import org.ndexbio.model.object.network.VisibilityType;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public interface NetworkDAO extends AutoCloseable {
    void commit() throws SQLException;
    
    NetworkSummary CreateCloneNetworkEntry(UUID networkUUID, UUID ownerId, String ownerUserName, long fileSize, UUID srcUUID) throws SQLException;

    NdexObjectUpdateStatus CreateEmptyNetworkEntry(UUID networkUUID, UUID ownerId, String ownerUserName, long fileSize, String networkName, String cxformat) throws SQLException;

    void setNetworkFileSize(UUID networkID, long fileSize) throws SQLException, NdexException;
    
    void setCX2FileSize(UUID networkID, long cx2FileSize) throws SQLException, NdexException;
    
    void setNetworkFileSizes(UUID networkID, long cxfileSize, long cx2FileSize) throws SQLException, NdexException;
    
    void deleteNetwork(UUID networkId, UUID userId) throws SQLException, NdexException;
    
    UUID getNetworkOwner(UUID networkId) throws SQLException, NdexException;
    
    String getNetworkName(UUID networkId) throws SQLException, NdexException;
    
    String getNetworkDOI(UUID networkId) throws SQLException, NdexException;
    
    int getNodeCount(UUID networkId) throws SQLException, ObjectNotFoundException;
    
    String getNetworkOwnerAcc(UUID networkId) throws SQLException, NdexException;
    
    void setFlag(UUID networkId, String fieldName, boolean value) throws SQLException;
    
    void saveNetworkEntry(NetworkSummary networkSummary, MetaDataCollection metadata, boolean setModificationTime) throws SQLException, NdexException, JsonProcessingException;
    
    void saveCX2NetworkEntry(NetworkSummary networkSummary, Map<String,CxMetadata> metadata, boolean setModificationTime) throws SQLException, NdexException, JsonProcessingException;
        
    boolean isReadable(UUID networkID, UUID userId) throws SQLException, ObjectNotFoundException;
    
    boolean isWriteable(UUID networkID, UUID userId) throws SQLException;
    
    boolean isReadOnly(UUID networkID) throws SQLException, ObjectNotFoundException;
    
    boolean hasDOI(UUID networkID) throws SQLException, ObjectNotFoundException;
    
    boolean isShowCased(UUID networkID) throws SQLException, ObjectNotFoundException;
    
    void setIndexLevel(UUID networkId, NetworkIndexLevel lvl) throws SQLException, NdexException;
    
    boolean isAdmin(UUID networkID, UUID userId) throws SQLException;
    
    void lockNetwork(UUID networkId) throws SQLException, NetworkConcurrentModificationException;
    
    boolean networkIsLocked(UUID networkUUID) throws ObjectNotFoundException, SQLException;
    
    boolean networkIsLocked(UUID networkUUID, int retry) throws ObjectNotFoundException, SQLException, InterruptedException;
    
    boolean networkIsValid(UUID networkUUID) throws ObjectNotFoundException, SQLException;
    
    int setProvenance(UUID networkId, ProvenanceEntity provenance) throws SQLException, NdexException, IOException;
    
    void updateNetworkProfile(UUID networkId, Map<String,String> newValues) throws NdexException, SQLException;
    
    void updateNetworkSummary(UUID networkId, NetworkSummary summary) throws NdexException, SQLException, JsonProcessingException;
    
    boolean hasCX2(UUID networkId) throws SQLException;
    
    void setCxMetadata(UUID networkId, List<CxMetadata> cx2metadata) throws SQLException, JsonProcessingException, NdexException;
    
    void updateMetadataColleciton(UUID networkId, MetaDataCollection metadata) throws SQLException, JsonProcessingException, NdexException;
    
    void checkPermissionOperationCondition(UUID networkId, UUID userId) throws SQLException, ObjectNotFoundException, NdexException;
    
    int grantPrivilegeToGroup(UUID networkUUID, UUID groupUUID, Permissions permission) throws NdexException, SQLException;
    
    int grantPrivilegeToUser(UUID networkUUID, UUID userUUID, Permissions permission) throws NdexException, IOException, SQLException;
    
    int revokeGroupPrivilege(UUID networkUUID, UUID groupUUID) throws SQLException;
    
    int revokeUserPrivilege(UUID networkUUID, UUID userUUID) throws SQLException;
    
    void setErrorMessage(UUID networkId, String errorMessage);
    
    void setWarning(UUID networkId, List<String> warnings) throws SQLException, NdexException;
    
    void setSubNetworkIds(UUID networkId, Set<Long> subNetworkIds) throws SQLException, NdexException;
    
    void setShowcaseFlag(UUID networkId, UUID userId, boolean bv) throws SQLException, UnauthorizedOperationException;
    
    String getNetworkAccessKey(UUID networkId) throws SQLException, ObjectNotFoundException;
    
    String enableNetworkAccessKey(UUID networkId) throws SQLException, ObjectNotFoundException;
    
    String requestDOI(UUID networkId, boolean isCertified) throws SQLException, NdexException;
    
    void cancelDOI(UUID networkId) throws SQLException, NdexException;
    
    void disableNetworkAccessKey(UUID networkId) throws SQLException;
    
    boolean accessKeyIsValid(UUID networkId, String accessKey) throws SQLException;
    
    void setNetworkFolder(UUID networkId, UUID parentId) throws SQLException, NdexException;
    
    UUID getNetworkFolder(UUID networkId) throws SQLException;
    
    void deleteNetworkLogical(UUID networkId, UUID userId) throws SQLException, NdexException;
    
    void deleteNetworkPermanently(UUID networkId, UUID userId) throws SQLException, NdexException;
    
    List<FileItemSummary> listSharedNetworks(UUID userId) throws SQLException;
    
    List<FileItemSummary> listNetworksSharedBySpecificUser(UUID userId, UUID ownerId, boolean compact) throws SQLException;
        
    void rollback() throws SQLException;

    Map<String,String> getNetworkUserPermissions(UUID networkId, Permissions permission, int skipBlocks, int blockSize) 
			throws SQLException;

    void setDOI (UUID networkId, String DOIStr) throws SQLException, NdexException;

    NetworkSummary getNetworkSummaryById (UUID networkId) throws SQLException, ObjectNotFoundException, JsonParseException, JsonMappingException, IOException;
    
    VisibilityType getNetworkVisibility(UUID networkId) throws SQLException, NdexException;
    
    NetworkSummaryV3 getNetworkMetadataById (UUID networkId, NetworkSummaryFormat format) throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException;
    
    List<CxMetadata> getCx2MetaDataList(UUID networkId) throws SQLException, IOException, NdexException;
    
    int getNetworkEdgeCount (UUID networkId) throws SQLException, ObjectNotFoundException;
    
    NdexObjectUpdateStatus clearNetworkSummary(UUID networkId, long fileSize) throws SQLException, NdexException;
    
    void unlockNetwork (UUID networkId) throws  SQLException;

    List<Map<Permissions, Collection<String>>> getAllMembershipsOnNetwork(UUID networkId)
            throws ObjectNotFoundException, NdexException, SQLException;
} 