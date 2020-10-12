package org.ndexbio.server.migration.v2;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
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

/**
 * Command line tool to create CX2 versions of networks
 * this code is installed on. Only networks that are complete, not deleted,
 * with no errors, and do not already have data in network.cx2metadata field
 * are processed. 
 * 
 * This tool will by default
 * use a pool of 4 (set via NUMBER_WORKERS) workers to process 
 * all networks below the size cutoff (set via SMALL_NETWORK_EDGECOUNT_CUTOFF)
 * Networks that are larger are processed by a single worker.
 * 
 * To run (via a terminal):
 * 
 * 1) Deploy this ndex-rest.war file on tomcat
 * 
 * 2) Open a terminal and change into the following directory:
 * 
 *    Example: cd /opt/ndex/tomcat/webapps/ndex-rest/WEB-INF
 * 
 * 3) Set ndexConfigurationPath environment variable to ndex.properties
 *    file 
 * 
 *    Example: export ndexConfigurationPath=/opt/ndex/conf/ndex.properties
 * 
 * 4) Invoke command by running this:
 * 
 *    java -Xmx36g -classpath lib/*:../../../lib/* org.ndexbio.server.migration.v2.CX2NetworkCreator
 * 
 * 
 * 
 * 
 * @author churas
 */
public class CX2NetworkCreator {
	
	/**
	 * Skip any networks that exceed this edge count
	 */
	private static final int edgeCountLimit = 20*1000000; 
	
	/**
	 * Number of workers to concurrently process networks below
	 * {@code SMALL_NETWORK_EDGECOUNT_CUTOFF} edges
	 */
	public static final int NUMBER_WORKERS = 4;
	
	/**
	 * Wait this many seconds for workers to complete there tasks
	 */
	public static final long SECONDS_TO_WAIT_FOR_JOBS = 3600*24;
	
	/**
	 * Small network edge count cutoff
	 */
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
			Queue<Future> futureTasks = new ConcurrentLinkedQueue<>();
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
				futureTasks = null;
				
				networksToUpdate = getIdsOfNetworksToUpdate(Integer.toString(SMALL_NETWORK_EDGECOUNT_CUTOFF), null);
				int remainNetworkCount = networksToUpdate.size();
				System.out.println("Found " + remainNetworkCount
						+ " with "
						+ Integer.toString(SMALL_NETWORK_EDGECOUNT_CUTOFF)
				         + " or more edges to convert");
				
				for (UUID networkUUID : networksToUpdate){
					CX2NetworkCreationRunner task = new CX2NetworkCreationRunner(rootPath, networkUUID, networkdao, globalIdx,
							edgeCountLimit);
					try {
						System.out.print(Integer.toString(remainNetworkCount) + ": " + networkUUID.toString());
						String res = task.call();
						if (res != null){
							System.out.println(res);
						}
					} catch(Exception ex){
						System.err.println("While updating network:  " 
								+ networkUUID.toString() + " : caught exception: " + ex.getMessage());
					} finally {
						remainNetworkCount--;
					}
				}
				
			}
		}
	}
	
	/**
	 * Given a Queue of future tasks this method loops through the tasks
	 * outputting results of completed tasks and canceled tasks. Removing them
	 * from the queue until none remain
	 * @param futureTasks 
	 */
	private static void waitForTasksToFinish(Queue<Future> futureTasks){
		while(futureTasks.isEmpty() == false){
			for (Future ftask : futureTasks){
				try {
					if (ftask.isDone()){
						System.out.println(futureTasks.size() + " : Completed => " + (String)ftask.get());
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
	
	/**
	 * Tells thread to sleep for 1 millisecond
	 */
	protected static void threadSleep(){
		try {
			Thread.sleep(1L);
		}
		catch(InterruptedException ie){

		}
	}
}
