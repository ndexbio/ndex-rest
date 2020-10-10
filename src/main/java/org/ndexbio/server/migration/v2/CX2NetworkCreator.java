package org.ndexbio.server.migration.v2;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.rest.Configuration;
import org.ndexbio.server.migration.v2.util.CX2NetworkCreationRunner;

public class CX2NetworkCreator {
	
	private static final int edgeCountLimit = 20*1000000; 
	public static final int NUMBER_WORKERS = 4;
	public static final long SECONDS_TO_WAIT_FOR_JOBS = 3600*24;
	public static final int SMALL_NETWORK_EDGECOUNT_CUTOFF = 500000;
	public CX2NetworkCreator() {
		
	}
	
	/**
	 * Queries the database for UUID of networks that are NOT deleted
	 * and are complete and lack cx2metadata and do NOT have errors
	 * @return
	 * @throws IOException
	 * @throws SQLException 
	 */
	public static List<UUID> getIdsOfNetworksToUpdate(final String minEdgeCountCutoff,
			final String maxEdgeCountCutoff) throws IOException, SQLException {
		try (Connection conn = NdexDatabase.getInstance().getConnection()) {

			String sqlStr = "select \"UUID\" from network where is_deleted=false "
					+ "and cx2metadata is null and iscomplete and error is null";
			if (minEdgeCountCutoff != null){
				sqlStr += " and edgecount>=" + minEdgeCountCutoff;
			}
			if (maxEdgeCountCutoff != null){
				sqlStr += " and edgecount<=" + maxEdgeCountCutoff;
			}
			List<UUID> networksToUpdate = new ArrayList<>();
			try (NetworkDAO networkdao = new NetworkDAO()) {
				try (PreparedStatement pst = conn.prepareStatement(sqlStr)) {
					try (ResultSet rs = pst.executeQuery()) {
						while (rs.next()) {
							networksToUpdate.add((UUID) rs.getObject(1));
						}
					}
				}
			}
			return networksToUpdate;
		}
	}
	
	public static void main(String[] args) throws Exception {		

		Configuration configuration = Configuration.createInstance();

		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);

		String rootPath = Configuration.getInstance().getNdexRoot() + "/data/";
		
		ExecutorService es = Executors.newFixedThreadPool(NUMBER_WORKERS);
		
		boolean onlyUpdateSingleNetwork = false;
		List<UUID> networksToUpdate = null;
		try (NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager()) {
			if (args.length == 1) {
				onlyUpdateSingleNetwork = true;
				networksToUpdate = new ArrayList<>();
				networksToUpdate.add(UUID.fromString(args[0]));
			} else {
				networksToUpdate = getIdsOfNetworksToUpdate("0",
						Integer.toString(SMALL_NETWORK_EDGECOUNT_CUTOFF));
			}
			List<Future> futureTasks = new LinkedList<>();
			System.out.println("Found " + networksToUpdate.size() + " networks to update");
			try (NetworkDAO networkdao = new NetworkDAO()) {
				System.out.println("Submitting tasks for processing");
				for (UUID networkUUID : networksToUpdate){
					CX2NetworkCreationRunner task = new CX2NetworkCreationRunner(rootPath, networkUUID, networkdao, globalIdx,
							edgeCountLimit);
					futureTasks.add(es.submit(task));
				}
				waitForTasksToFinish(futureTasks);
				es.shutdown();
				if (es.awaitTermination(SECONDS_TO_WAIT_FOR_JOBS, TimeUnit.SECONDS) == false){
					System.err.println("Time reached before jobs have completed!!!!");
					return;
				}
			
				if (onlyUpdateSingleNetwork == true){
					return;
				}
				
				futureTasks.clear();
				networksToUpdate = getIdsOfNetworksToUpdate(Integer.toString(SMALL_NETWORK_EDGECOUNT_CUTOFF), null);
				es = Executors.newFixedThreadPool(1);
				System.out.println("Found " + networksToUpdate.size() 
						+ " with "
						+ Integer.toString(SMALL_NETWORK_EDGECOUNT_CUTOFF)
				         + " or more edges to convert");
				for (UUID networkUUID : networksToUpdate){
					CX2NetworkCreationRunner task = new CX2NetworkCreationRunner(rootPath, networkUUID, networkdao, globalIdx,
							edgeCountLimit);
					futureTasks.add(es.submit(task));
				}
				waitForTasksToFinish(futureTasks);
				es.shutdown();
				if (es.awaitTermination(SECONDS_TO_WAIT_FOR_JOBS, TimeUnit.SECONDS) == false){
					System.err.println("Time reached before jobs have completed!!!!");;
				}
			}
		}
	}
	
	private static void waitForTasksToFinish(List<Future> futureTasks){
		while(futureTasks.isEmpty() == false){
			for (Future ftask : futureTasks){
				try {
					if (ftask.isDone()){
						System.out.println("Completed Task => " + (String)ftask.get());
						futureTasks.remove(ftask);
					} else if (ftask.isCancelled()){
						System.err.println("Task canceled");
						futureTasks.remove(ftask);
					}

				} catch(InterruptedException ie){
					
				} catch(ExecutionException ee){
					System.err.println("Task raised an exception: " + ee.getMessage());
					futureTasks.remove(ftask);
				}
				threadSleep();
			}
		}
	}
	
	protected static void threadSleep(){
		try {
			Thread.sleep(100L);
		}
		catch(InterruptedException ie){

		}
	}
}
