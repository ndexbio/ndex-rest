package org.ndexbio.server.migration.v2.util;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.common.persistence.CXToCX2ServerSideConverter;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.common.util.Util;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.exceptions.NdexException;

/**
 * Instances of this class create a CX2 Network
 * from an existing CX network in the system
 * 
 * @author churas
 */
public class CX2NetworkCreationRunner implements Callable {

	private  String _rootPath;
	private  UUID _networkUUID;
	private NetworkDAO _networkdao;
	private NetworkGlobalIndexManager _globalIdx;
	private int _edgeCountLimit;
	private StringBuilder _sb; 
	
	/**
	 * Constructor
	 * @param rootPath path to data directory. Should end with / 
	 * @param networkUUID UUID of network to update
	 * @param networkdao database access object for network, should be connected
	 * @param globalIdx used to clean up solr indexes on network
	 * @param edgeCountLimit Skip conversion of networks with more edges then this
	 */
	public CX2NetworkCreationRunner(final String rootPath, final UUID networkUUID,
			NetworkDAO networkdao, NetworkGlobalIndexManager globalIdx,
			final int edgeCountLimit){
		_rootPath = rootPath;
		_networkUUID = networkUUID;
		_networkdao = networkdao;
		_globalIdx = globalIdx;
		_edgeCountLimit = edgeCountLimit;
		_sb = new StringBuilder();
	}
	
	/**
	 * This creates CX2 version of network passed in via the constructor.
	 * This method first locks the network in the database before modification
	 * and does an unlock at end. 
	 * 
	 * The conversion involves creating a new cx2 aspects directory and populating
	 * that folder with cx2 aspects.
	 * 
	 * Finally, if the network.arc file exists, this 
	 * method also gzips that file appending
	 * .gz to name.
	 * 
	 * 
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 * @throws NdexException
	 * @throws SolrServerException 
	 */
	@Override
	public String call() throws IOException, SQLException, NdexException, SolrServerException {
		
		try {
			_networkdao.lockNetwork(_networkUUID);

			if (isOkayToConvertNetworkToCX2()) {
				_sb.append("Recreating cx2 for ");
				_sb.append(_networkUUID.toString());
				_sb.append(" ... ");

				// delete cx2 aspect folder if exists
				deleteCX2AspectFolderIfExists();

				MetaDataCollection mc = _networkdao.getMetaDataCollection(_networkUUID);
				try {
					CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_rootPath, mc,
							_networkUUID.toString(), null, true);
					List<CxMetadata> cx2mc = converter.convert();
					_networkdao.setCxMetadata(_networkUUID, cx2mc);
					if (converter.getWarning().size() > 0) {
						List<String> warnings = new java.util.ArrayList<>(
								_networkdao.getWarnings(_networkUUID));
						warnings.removeIf(n -> n.startsWith(CXToCX2ServerSideConverter.messagePrefix));
						warnings.addAll(converter.getWarning());
						_networkdao.setWarning(_networkUUID, warnings);
					}
				} catch (NdexException | RuntimeException e) {
					_networkdao.setErrorMessage(_networkUUID,
							CXToCX2ServerSideConverter.messagePrefix + e.getMessage());
					_sb.append(_networkUUID.toString());
					_sb.append(" has error. Error message is: ");
					_sb.append(e.getMessage());
					_globalIdx.deleteNetwork(_networkUUID.toString());
					try (SingleNetworkSolrIdxManager networkIdx = new SingleNetworkSolrIdxManager(_networkUUID.toString())) {
						networkIdx.dropIndex();
					}
				}

			}

			// gzip the archived cx2 file if it still exists
			String cx1ArchiveFilePath = _rootPath + _networkUUID.toString() + "/" + CXNetworkLoader.CX1ArchiveFileName;
			File f = new File(cx1ArchiveFilePath);
			if ( f.exists()) {
				_sb.append(" Gzipping CX1 network.arc file. ");
				Util.asyncCompressGZIP(cx1ArchiveFilePath);
			}

			return _sb.toString();
		} finally {
			_networkdao.unlockNetwork(_networkUUID);
		}
	}
	
	/**
	 * For network passed in via constructor, this method removes the CX2 aspect 
	 * folder and any data within as 
	 * well as net2.cx file if it exists
	 * @throws IOException 
	 */
	private void deleteCX2AspectFolderIfExists() throws IOException {
		File f = new File(
					_rootPath + _networkUUID.toString() + File.separator + CX2NetworkLoader.cx2AspectDirName);
		if (f.exists()) {
			FileUtils.deleteDirectory(f);
			_sb.append(" aspect folder deleted ... ");
		}

		f = new File (_rootPath + _networkUUID.toString() + File.separator + "net2.cx");
		if ( f.exists())
			f.delete();
	}
	
	/**
	 * Checks if it is okay to create CX2 version of network by
	 * verifying network is NOT part of a collection and the edgecount
	 * of network does NOT exceed {@code edgeCountLimit} set in this
	 * class.
	 * 
	 * In addition, to checking if network cannot be converted, this method
	 * also removes any CX2 prefixed warnings from the database and adds
	 * human readable message about why conversion/creation cannot occur.
	 * 
	 * @param networkUUID
	 * @param networkdao
	 * @return {@code true} if network can be created/converted, {@code false} otherwise
	 * @throws SQLException
	 * @throws NdexException 
	 */
	protected boolean isOkayToConvertNetworkToCX2() throws SQLException, NdexException {
		
		boolean isSingleNetwork = _networkdao.getSubNetworkId(_networkUUID).isEmpty();
		int edgeCount = _networkdao.getNetworkEdgeCount(_networkUUID);
		
		if ( isSingleNetwork && edgeCount <= _edgeCountLimit) {
			return true;
		}
		
		List<String> warnings = new java.util.ArrayList<>(
					_networkdao.getWarnings(_networkUUID));
		warnings.removeIf(n -> n.startsWith(CXToCX2ServerSideConverter.messagePrefix));

		String message = null;
		if ( edgeCount > _edgeCountLimit) {
			message = "CX2 network won't be generated on networks that have more than " + _edgeCountLimit + " edges.";				
		} else {
			message = "CX2 network won't be generated on Cytoscape network collection." ;
		}
		warnings.add(CXToCX2ServerSideConverter.messagePrefix + message);				
		_networkdao.setWarning(_networkUUID, warnings);
		_sb.append(" : ");
		_sb.append(message);
		return false;
	}
	
}
